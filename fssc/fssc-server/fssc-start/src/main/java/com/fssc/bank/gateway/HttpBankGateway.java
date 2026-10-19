package com.fssc.bank.gateway;

import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ConnectorFacade;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实银企直联网关（HTTP 形态）·<b>域3 收编后的内部实现</b>。
 *
 * <p><b>激活条件不变</b>：{@code fssc.bank.gateway=http}（未配置或 {@code =mock} 时仍走
 * {@link DefaultBankGateway} 模拟，演示离线可跑）。开关语义与历史一致，可一键回滚。</p>
 *
 * <p><b>收编带来的四项治理</b>（原先本类全部缺失）：
 * <ul>
 *   <li><b>鉴权</b>：原本类手拼 {@code X-Api-Key}、apiSecret 明文未用 → 现由统一网关按
 *       {@code ext_interface_config} 注入 {@code Authorization: Bearer} + {@code X-API-Secret}；</li>
 *   <li><b>幂等</b>：原无幂等（重复提交会重复付款）→ 现由网关按「租户 + 幂等键」回放；</li>
 *   <li><b>留痕</b>：原仅 {@code log.info} → 现落 {@code integration_call_log}
 *       （trace_id / retry_count / is_duplicate 可查）；</li>
 *   <li><b>熔断限流</b>：原无 → 现经 {@code ConnectorFacade} 的 ResilienceGuard。</li>
 * </ul>
 * 配置来源也统一了：原本类自查 {@code ext_interface_config(category=BANK)}，现由网关读<b>同一
 * category</b>，<b>配置零迁移</b>。</p>
 *
 * <p><b>为什么报文组装在本类内而不在 connector 侧</b>：本类位于 {@code com.fssc.bank}（业务包），
 * 按 CI 规则 9「业务包禁止 import {@code connector.adapter.*}」，只能依赖
 * {@code com.fssc.connector.api.*} 契约。因此银行报文底稿与流水号解析保留在本类；
 * {@code connector} 侧的 {@code BankPaymentAdapter} 负责通道语义（幂等/留痕/熔断/重试）。
 * 双方通过 {@link ChannelResponse} 契约对接，各自职责不外泄。</p>
 *
 * <p><b>业务层零感知</b>：{@link BankGateway} 接口与 {@code PayBatchService} 均<b>不改动</b>。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fssc.bank.gateway", havingValue = "http")
public class HttpBankGateway implements BankGateway {

    /** ext_interface_config.category：银行通道（与历史口径一致，配置零迁移）。 */
    private static final String CAPABILITY_BANK = "BANK";

    private final ConnectorFacade connectorFacade;
    /** R3 多银行：报文形态按通道配置的 provider 分派（新增一家银行只改配置）。 */
    private final BankChannelResolver channelResolver;

    @Override
    public BankResult submitBatch(String batchNo, List<PayItem> items) {
        BankResult r = new BankResult();
        int count = (items == null) ? 0 : items.size();

        if (!connectorFacade.isConfigured(CAPABILITY_BANK)) {
            r.setSuccess(false);
            r.setFailReason("银企直联未配置或未启用（ext_interface_config category=BANK）");
            r.setResp("NO_BANK_CONFIG");
            log.warn("[银企直联] 批次 {} 提交失败：{}", batchNo, r.getFailReason());
            return r;
        }

        try {
            String key = idempotencyKey(batchNo);
            // R3：报文形态由通道配置的 provider 决定，业务层不感知
            BankProviderCodec codec = channelResolver.activeCodec();
            Object payload = codec.buildPayload(batchNo, items, channelResolver.activeInstance());

            ChannelResponse resp = connectorFacade.invokeWrite(CAPABILITY_BANK, payload, key);

            if (resp == null) {
                r.setSuccess(false);
                r.setFailReason("银企直联无响应");
                r.setResp("NO_RESPONSE");
                log.warn("[银企直联] 批次 {} 无响应 key={}", batchNo, key);
                return r;
            }

            r.setSuccess(resp.isOk());
            r.setResp(resp.getBody());
            if (!resp.isOk()) {
                // 失败必须回填 failReason：业务侧据此标 FAILED 并发起 TR_PAY_FAIL 失败处理审批流
                r.setFailReason(resp.getError() != null && !resp.getError().isBlank()
                        ? resp.getError()
                        : "银行返回失败（HTTP " + resp.getStatusCode() + "）");
                log.warn("[银企直联] 批次 {} 提交失败：{} key={} duplicate={}",
                        batchNo, r.getFailReason(), key, resp.isDuplicate());
                return r;
            }

            r.setSerialNos(serialNosOf(codec, resp.getBody(), count));
            log.info("[银企直联] 批次 {} 经 ConnectorFacade 提交 {} 笔成功 key={} duplicate={} 耗时={}ms provider={}",
                    batchNo, count, key, resp.isDuplicate(), resp.getDurationMs(),
                    channelResolver.activeProvider());
        } catch (Exception e) {
            // 兜底：保证付款失败一定落到「标 FAILED + 失败留痕 + 发起 TR_PAY_FAIL」的闭环，
            // 而不会把异常抛穿进 PayBatchService 的事务导致整批回滚。
            r.setSuccess(false);
            r.setFailReason("银行接口调用失败：" + e.getMessage());
            r.setResp("ERR");
            log.error("[银企直联] 批次 {} 调用异常", batchNo, e);
        }
        return r;
    }

    /**
     * 从银行响应体解析各笔流水号，并在编解码器无法解析时降级为占位流水号。
     *
     * <p>降级口径与 R1/R2 一致：整批一个 {@code BANK+时间戳}，后续笔加 {@code -i} 后缀。</p>
     */
    private List<String> serialNosOf(BankProviderCodec codec, String body, int itemCount) {
        List<String> parsed = codec.parseSerialNos(body, itemCount);
        if (parsed != null) {
            return parsed;
        }
        String batchSerial = "BANK" + System.currentTimeMillis();
        List<String> fallback = new ArrayList<>();
        for (int i = 0; i < Math.max(itemCount, 1); i++) {
            fallback.add(i == 0 ? batchSerial : batchSerial + "-" + i);
        }
        return fallback;
    }

    /**
     * 幂等键：{@code BANK_PAY:{tenantId}:{batchNo}#a0}。
     *
     * <p><b>为什么带尝试号</b>：网关 {@code findPrior()} 按
     * {@code (category, idempotency_key, tenant_id, is_duplicate=0)} 精确匹配且<b>不校验
     * {@code success}</b>，首次失败的结果同样会被回放——不带尝试号则付款失败后永远重试不成功。
     * 带尝试号后：同批次<b>重复提交</b>（同键）命中 → 回放 → <b>不重复付款</b>；
     * 失败后<b>重试</b>（换键 {@code #a1}）未命中 → <b>真实外呼</b>。</p>
     *
     * <p>本次先固定 {@code #a0}；待业务侧提供"失败重试次数"后，把 {@code 0} 换成真实尝试号即可，
     * 无需再动网关（决策记录见 {@code 权限治理/T1.1_幂等失败重试方案决策_20261014.md}）。</p>
     */
    private String idempotencyKey(String batchNo) {
        return "BANK_PAY:" + currentTenantId() + ":" + batchNo + "#a0";
    }

    private Long currentTenantId() {
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            return 0L;
        }
    }
}
