package com.fssc.connector.adapter.bank;

import com.fssc.connector.adapter.ChannelAdapter;
import com.fssc.connector.api.ChannelRequest;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.connector.core.ChannelResponseMapper;
import com.fssc.system.gateway.GatewayResponse;
import com.fssc.system.gateway.IntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 银企直联适配器（形态 B · 域3 收编）。
 *
 * <p><b>为什么有本类</b>：银行能力原先另起一套平行实现 {@code com.fssc.bank.gateway.BankGateway}
 * （{@code DefaultBankGateway} 模拟 + {@code HttpBankGateway} HTTP），<b>绕过</b> connector 的
 * 凭据托管 / 幂等 / 留痕 / 熔断统一治理。本类把银行注册为一个正规能力域
 * （{@code ChannelType.BANK_PAYMENT}），使「新增一家银行只加通道配置、不改业务码」成立。</p>
 *
 * <p><b>职责边界（严格）</b>：
 * <ul>
 *   <li><b>本类</b>：银行通道的语义收敛点——幂等键透传、通道级错误归一、
 *       后续多银行按 provider 分派的挂接位。</li>
 *   <li><b>通用网关</b>：按 category（BANK）读 {@code ext_interface_config}、凭据加密与注入
 *       （apiKey→{@code Authorization: Bearer}、apiSecret→{@code X-API-Secret}）、
 *       幂等回放、有界重试、超时、落 {@code integration_call_log}。</li>
 *   <li><b>业务包 {@code com.fssc.bank}</b>：银行报文底稿与流水号解析。按 CI 规则 9，
 *       业务包<b>不得</b> import 本包（{@code connector.adapter.*}），故报文组装保留在
 *       {@code HttpBankGateway} 内，两者仅通过 {@link ChannelResponse} 契约对接。</li>
 * </ul>
 *
 * <p><b>不得抛穿异常</b>（{@link ChannelAdapter} 契约）：付款失败必须以 {@code isOk()=false}
 * 返回，交由业务侧走「标 FAILED + 失败原因留痕 + 发起 TR_PAY_FAIL 失败处理审批流」的闭环，
 * 而不是抛异常打断事务。</p>
 *
 * <p><b>未注册时</b>：{@code ChannelAdapterRegistry.find()} 返回 {@code null}，
 * {@code DefaultConnectorFacade} 自动回落通用网关，行为与收编前一致（零回归兜底）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankPaymentAdapter implements ChannelAdapter {

    /** ext_interface_config.category：银行通道（与历史口径一致，配置零迁移）。 */
    public static final String CAPABILITY_BANK = "BANK";

    private final IntegrationGateway gateway;

    @Override
    public ChannelType type() {
        return ChannelType.BANK_PAYMENT;
    }

    @Override
    public ChannelResponse invoke(ChannelRequest request) {
        // 幂等回放标记（duplicate=true 表示本次未真实外呼）已由
        // ChannelResponseMapper.adapt 从 GatewayResponse 搬运过来，此处不重复设置。
        try {
            GatewayResponse r = gateway.post(
                    request.capability(), request.payload(), request.idempotencyKey());
            return ChannelResponseMapper.adapt(r, type());
        } catch (Exception e) {
            // 契约要求不抛穿异常：付款失败交由业务侧走失败闭环，避免整批事务回滚。
            log.error("[银企直联] 通道调用异常 capability={} key={}",
                    (request == null) ? null : request.capability(),
                    (request == null) ? null : request.idempotencyKey(), e);
            return ChannelResponse.fail("银企直联调用异常：" + e.getMessage());
        }
    }
}
