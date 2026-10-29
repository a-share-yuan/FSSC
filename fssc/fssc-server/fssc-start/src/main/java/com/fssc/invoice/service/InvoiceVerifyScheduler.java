// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.service.ExtInterfaceConfigService;
import com.fssc.framework.datasource.FsscDataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票定时验真（需求 7.1）：每 5 分钟扫描待验发票（verify_status IN NONE/PENDING/FAILED）自动验真。
 *
 * <p>验真通道由「外部接口配置」页面统一管理（category=INVOICE_VERIFY），并新增<b>发票能力网关</b>缺省环节：
 * <ul>
 *   <li>配置已启用（status=ENABLED）：调用该配置指定的真实外部验真接口（HTTP POST + Bearer Auth），
 *       按响应 JSON 的 result/pass 字段判定 PASS/FAIL——管理员显式配置优先；</li>
 *   <li>配置未启用或不存在：默认改走发票能力网关（InvoiceGateway，8096）的验真门面
 *       {@code POST {base}/api/tax/v1/verify/invoices}，逐张给结论并把请求体与返回原文一起留痕
 *       （发票真伪查验/认证抵扣默认走我方封装的 API）；
 *       网关结论同样出自真实查验通道，满足「带报文」的出口要求；</li>
 *   <li>网关被显式关闭（{@code invoice.gateway.enabled=false}）或地址留空：
 *       <b>本轮不做任何判定</b>，待验发票原状态（NONE=未验真 / PENDING=验真中）
 *       保持不变——不再回退「模拟 PASS 写 SUCCESS」。模拟结果不是税务局的结论，
 *       落成 SUCCESS 会被下游（三单匹配发票覆盖、发票合规审核、归档检查）当已验真事实（假合规）。
 *       网关不可达／网关明确回错（未配置上游凭证、超时、非 200、响应非法）属<b>通道故障而非查验结论</b>：
 *       该张本轮不写结论、保持原状态，只记告警日志——既不伪造通过，也不把网络抖动写成「验真失败」。</li>
 * </ul>
 *
 * <p>【可判定前置谓词】只有「有发票影像（img_file_id 非空）且来源不是手工补录
 * （source_doc_type≠MANUAL）」的发票才进验真队列：手工键入的扫描件既无影像也无 OCR/税务报文，
 * 建单两分钟就被置 SUCCESS 属程序造出的假状态，且这些行也不占 {@code limit} 名额（否则永远捞不到真待验票）。
 * 不满足前置谓词的行留在原状态（NONE=未验真 / PENDING=验真中），等人工补影像后自然进入队列；
 * 取值一律用 {@code constants/enums.js} 与 inv_invoice 既有 verify_status 语义，不新增枚举。
 *
 * <p>去重：已 SUCCESS 的不重复处理；队列为空时直接跳过。
 *
 * <p>【租户上下文】定时任务跑在 Spring 调度线程上，无 {@link TenantContext}。
 * inv_invoice 为租户过滤表，直接查询会触发 fail-closed 抛
 * {@code TENANT_CONTEXT_MISSING}。故：
 * <ol>
 *   <li>待验扫描用 {@code runIgnoreTenant} 跨租户聚合（仅扫描，不写入数据库）；</li>
 *   <li>逐张验真时用 {@code callAs(该发票 tenantId)} 恢复其归属租户上下文，
 *       保证 verifyNow 的 UPDATE/日志落入正确租户，杜绝串户。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceVerifyScheduler {

    private final InvoiceEnhanceService enhanceService;
    private final InvInvoiceMapper invoiceMapper;
    private final JobHeartbeatService jobHeartbeatService;
    private final ExtInterfaceConfigService extInterfaceConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    /** 网关环节超时：上游查验含税局往返，与网关 OCR/验真的读超时规则一致留足余量 */
    private static final Duration GATEWAY_TIMEOUT = Duration.ofSeconds(30);

    // 发票能力网关（InvoiceGateway）缺省验真环节：三系统统一键名
    @Value("${invoice.gateway.base-url:http://127.0.0.1:8096}")
    private String gatewayBaseUrl;

    @Value("${invoice.gateway.api-key:}")
    private String gatewayApiKey;

    /** 总开关：false 时验真退回「无 ENABLED 外部配置即不判定」的旧规则 */
    @Value("${fssc.gateway.enabled:true}")
    private boolean gatewayEnabled;

    /**
     * 前置谓词（SQL 片段，与 {@link #isVerifiableByJob} 同一规则，改一处必改两处）：
     * 有影像 且 来源非手工补录，才允许由定时作业给出验真结论。
     */
    private static final String VERIFIABLE_SQL =
            "img_file_id IS NOT NULL AND (source_doc_type IS NULL OR source_doc_type <> 'MANUAL')";

    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 首次 1 分钟后，之后每 5 分钟
    public void autoVerifyPending() {
        jobHeartbeatService.run("InvoiceVerifyScheduler", () -> {
        try {
            // 待验扫描：跨租户聚合（runIgnoreTenant 关闭租户过滤；仅扫描不写入数据库）
            List<InvInvoice>[] holder = new List[1];
            TenantContext.runIgnoreTenant(() ->
                    holder[0] = invoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                            .in(InvInvoice::getVerifyStatus, "NONE", "PENDING", "FAILED")
                            // 无影像/手工补录的发票直接排除在队列外，
                            // 既不会被置 SUCCESS，也不会白占 limit 名额
                            .apply(VERIFIABLE_SQL)
                            // 开票日期未填或晚于当日的票不进本轮队列
                            // （未来票先于开票日拿到验真结论＝「验真时间早于开票日期」的怪证据）
                            .and(w -> w.isNull(InvInvoice::getInvoiceDate)
                                    .or().le(InvInvoice::getInvoiceDate, LocalDate.now()))
                            .last("limit 50")));
            List<InvInvoice> pending = holder[0];
            if (pending == null || pending.isEmpty()) {
                return;
            }
            log.info("[定时验真] 扫描到 {} 张待验发票", pending.size());

            // 读取验真接口配置（定时任务无 TenantContext，用 callAs(1L) 建立租户上下文读取）
            ExtInterfaceConfig[] cfgHolder = new ExtInterfaceConfig[1];
            TenantContext.callAs(1L, () ->
                    cfgHolder[0] = extInterfaceConfigService.getEnabledByCategory("INVOICE_VERIFY"));
            ExtInterfaceConfig verifyConfig = cfgHolder[0];
            boolean useRealApi = verifyConfig != null
                    && "ENABLED".equals(verifyConfig.getStatus())
                    && verifyConfig.getApiUrl() != null
                    && !verifyConfig.getApiUrl().isBlank();

            if (useRealApi) {
                log.info("[定时验真] 使用外部验真接口: {} ({})",
                        verifyConfig.getInterfaceName(), verifyConfig.getApiUrl());
            } else if (gatewayAvailable()) {
                // 需求：发票真伪查验默认走我们封装的 API（InvoiceGateway 8096）。
                // 管理员在「外部接口配置」显式启用了 INVOICE_VERIFY 时仍以上面的配置优先。
                log.info("[定时验真] 默认通道=发票能力网关 {}（逐张查验并落报文留痕）", gatewayBase());
            } else {
                // 网关被显式关闭且无 ENABLED 外部配置 ⇒ 不产出任何验真结论。
                // 旧行为是「回退模拟 PASS」并把 50 张票刷成 SUCCESS（verify_msg=定时验真(系统自动)通过），
                // 系统内没有一条外部报文支撑该状态，却成为下游发票覆盖/合规审核/归档的准入依据。
                log.warn("[定时验真] 验真通道不可用（category=INVOICE_VERIFY 无 ENABLED 配置，"
                        + "且发票能力网关未启用/地址为空），本轮跳过 {} 张待验发票："
                        + "其 verify_status 保持不变（NONE=未验真 / PENDING=验真中），"
                        + "不再回退模拟 PASS 写 SUCCESS；请启用网关或在「外部接口配置」启用验真通道",
                        pending.size());
                return;
            }

            for (InvInvoice inv : pending) {
                // 兜底校验：扫描规则与逐行规则不一致时，宁可跳过也不给无凭据的发票造结论
                if (!isVerifiableByJob(inv)) {
                    log.info("[定时验真] 发票 {} 跳过：{}（不判定验真结果，保持原状态）",
                            inv.getId(), unverifiableReason(inv));
                    continue;
                }
                if (useRealApi) {
                    TenantContext.callAs(inv.getTenantId(), () -> {
                        VerifyResult vr = callExternalVerify(verifyConfig, inv);
                        // 结论出自真实外部通道 ⇒ 走「带报文」的出口，
                        // 把请求体与返回原文一起进 inv_verify_log（旧实现只写 resp_content=result=PASS，
                        // 等于用一行自造报文冒充查验证据），并标 channel=SYSTEM（系统作业，
                        // 旧实现误标 REALTIME，与「实为定时作业」不符）。
                        enhanceService.applyVerifyConclusion(inv.getId(), vr.pass(),
                                "定时验真(系统自动)·" + vr.message(), "SYSTEM",
                                vr.request(), vr.rawResponse(), null);
                    });
                } else {
                    TenantContext.callAs(inv.getTenantId(), () -> {
                        VerifyResult vr = callGatewayVerify(inv);
                        if (vr.inconclusive()) {
                            // 通道故障（网关不可达/回错/无票面结果）≠ 查验结论：本张不判定，留原状态待下轮。
                            // 既没有报文就不写 SUCCESS，也不把网络抖动写成「验真失败」。
                            log.warn("[定时验真] 发票 {} 本轮不判定：{}", inv.getId(), vr.message());
                            return;
                        }
                        // 结论出自网关的真实查验通道 ⇒ 走「带报文」的出口进 inv_verify_log；
                        // channel 取该列既有名称表 TAX（税局规则查验），作业记录由 sys_job_heartbeat/verify_msg 承担。
                        enhanceService.applyVerifyConclusion(inv.getId(), vr.pass(),
                                "定时验真(发票能力网关)·" + vr.message(), "TAX",
                                vr.request(), vr.rawResponse(), null);
                    });
                }
            }
        } catch (Exception e) {
            log.error("[定时验真] 执行异常", e);
        }
        });
    }

    /** 与 {@link #VERIFIABLE_SQL} 同一规则的 Java 侧前置谓词 */
    private static boolean isVerifiableByJob(InvInvoice inv) {
        return unverifiableReason(inv) == null;
    }

    /** 不可判定原因；返回 null 表示可判定 */
    private static String unverifiableReason(InvInvoice inv) {
        if (inv == null) {
            return "发票不存在";
        }
        if (inv.getImgFileId() == null) {
            return "无发票影像(img_file_id=NULL)";
        }
        if ("MANUAL".equalsIgnoreCase(inv.getSourceDocType())) {
            return "手工补录来源(source_doc_type=MANUAL)";
        }
        if (inv.getInvoiceDate() != null && inv.getInvoiceDate().isAfter(LocalDate.now())) {
            return "开票日期晚于当前日期(invoice_date=" + inv.getInvoiceDate() + ")";
        }
        return null;
    }

    /**
     * 调用外部验真接口：HTTP POST JSON，请求体包含发票要素，响应按 JSON 字段判定 PASS/FAIL。
     *
     * <p>请求头：Content-Type: application/json；apiKey 非空时附加 Authorization: Bearer {apiKey}。
     * 请求体字段：invoiceCode / invoiceNo / invoiceDate / amount / sellerTaxNo / checkCode。
     * 响应判定：JSON 中 result=PASS 或 pass=true 则通过，否则 FAIL，附带 message 字段。</p>
     */
    private VerifyResult callExternalVerify(ExtInterfaceConfig config, InvInvoice inv) {
        // 请求体在 try 外声明，异常分支也要能把「发出去了什么」留给日志
        String jsonBody = "";
        try {
            // 构造请求体
            Map<String, Object> body = new HashMap<>();
            body.put("invoiceCode", inv.getInvoiceCode() != null ? inv.getInvoiceCode() : "");
            body.put("invoiceNo", inv.getInvoiceNo() != null ? inv.getInvoiceNo() : "");
            body.put("invoiceDate", inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : "");
            body.put("amount", inv.getAmount() != null ? inv.getAmount().toPlainString() : "");
            body.put("sellerTaxNo", inv.getSellerTaxNo() != null ? inv.getSellerTaxNo() : "");
            body.put("checkCode", inv.getCheckCode() != null ? inv.getCheckCode() : "");
            jsonBody = objectMapper.writeValueAsString(body);

            // 构造 HTTP 请求
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            // 鉴权
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.getApiKey());
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
            HttpResponse<String> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String msg = "验真接口返回 HTTP " + resp.statusCode();
                log.warn("[定时验真] 发票 {} 验真失败: {}", inv.getId(), msg);
                return new VerifyResult(false, msg, jsonBody, truncate(resp.body()));
            }

            // 解析响应 JSON 判定结果
            JsonNode respJson = objectMapper.readTree(resp.body());
            // 兼容多种响应格式：result="PASS" / pass=true / verified=true / success=true
            boolean pass = false;
            String message = null;

            if (respJson.has("result")) {
                pass = "PASS".equalsIgnoreCase(respJson.get("result").asText());
            } else if (respJson.has("pass")) {
                pass = respJson.get("pass").asBoolean();
            } else if (respJson.has("verified")) {
                pass = respJson.get("verified").asBoolean();
            } else if (respJson.has("success")) {
                pass = respJson.get("success").asBoolean();
            } else if (respJson.has("code")) {
                // 兼容 code=0 表示成功的风格
                pass = respJson.get("code").asInt() == 0;
            }

            if (respJson.has("message")) {
                message = respJson.get("message").asText();
            } else if (respJson.has("msg")) {
                message = respJson.get("msg").asText();
            }

            if (message == null || message.isBlank()) {
                message = pass ? "验真通过(外部接口)" : "验真失败(外部接口)";
            }

            log.info("[定时验真] 发票 {} 验真结果: {} - {}", inv.getId(),
                    pass ? "PASS" : "FAIL", message);
            return new VerifyResult(pass, message, jsonBody, truncate(resp.body()));

        } catch (Exception e) {
            String msg = "调用验真接口异常: " + e.getMessage();
            log.warn("[定时验真] 发票 {} 验真异常: {}", inv.getId(), e.getMessage());
            return new VerifyResult(false, msg, jsonBody, null);
        }
    }

    // 发票能力网关验真环节（默认通道）

    /**
     * 网关环节是否可用：总开关开启且基址非空。
     *
     * <p>本环节取值只认 yml/环境变量（{@code invoice.gateway.*} 与 {@code fssc.gateway.enabled}）：
     * 定时作业跨租户跑，sys_config 是租户级且落在 master 库，把它当验真开关会造成
     * 「A 租户的配置影响 B 租户发票」的串户判定。租户级即时切换请走管理面：
     * 在「外部接口配置」启用 category=INVOICE_VERIFY（优先级高于网关环节），
     * 或停用网关进程本身。</p>
     */
    private boolean gatewayAvailable() {
        return gatewayEnabled && !gatewayBase().isBlank();
    }

    /** 生效网关基址（yml {@code invoice.gateway.base-url}），去尾部斜杠 */
    private String gatewayBase() {
        String v = gatewayBaseUrl;
        if (v == null) {
            return "";
        }
        v = v.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 默认环节：调用发票能力网关的真伪查验门面 {@code POST {base}/api/tax/v1/verify/invoices}。
     *
     * <p>只使用网关的中性契约字段（invoiceCode / invoiceNumber / invoiceDate / totalWithTax / checkCode），
     * 上游接口的路径、字段名与签名一律不泄漏到 FSSC。判定取 {@code data.results[0].passed}；
     * 请求体与返回原文一并返回，供 inv_verify_log 复核。</p>
     *
     * <p>【不臆造结论】网络异常、非 2xx、{@code success=false}、无 results 元素——都归为
     * {@code inconclusive}（本轮不判定），既不给 PASS 也不给 FAIL；只有网关真的回了一张票的查验结果，
     * 才落成 SUCCESS/FAILED。</p>
     */
    private VerifyResult callGatewayVerify(InvInvoice inv) {
        String jsonBody = "";
        try {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("invoiceCode", inv.getInvoiceCode() == null ? "" : inv.getInvoiceCode());
            item.put("invoiceNumber", inv.getInvoiceNo() == null ? "" : inv.getInvoiceNo());
            item.put("invoiceDate", inv.getInvoiceDate() == null ? "" : inv.getInvoiceDate().toString());
            // inv_invoice.amount 即「价税合计」（见实体注释），网关契约字段名为 totalWithTax
            item.put("totalWithTax", inv.getAmount() == null ? BigDecimal.ZERO : inv.getAmount());
            item.put("checkCode", inv.getCheckCode() == null ? "" : inv.getCheckCode());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("invoices", List.of(item));
            jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBase() + "/api/tax/v1/verify/invoices"))
                    .timeout(GATEWAY_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            String key = gatewayApiKey;
            if (!isBlank(key)) {
                builder.header("X-Gateway-Api-Key", key.trim());
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(GATEWAY_TIMEOUT).build();
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return VerifyResult.noVerdict(
                        "发票能力网关 HTTP " + resp.statusCode() + " " + brief(resp.body()),
                        jsonBody, truncate(resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.path("success").asBoolean(false)) {
                return VerifyResult.noVerdict("发票能力网关返回失败 " + root.path("code").asText("")
                        + " " + root.path("message").asText(""), jsonBody, truncate(resp.body()));
            }
            JsonNode results = root.path("data").path("results");
            if (!results.isArray() || results.isEmpty()) {
                return VerifyResult.noVerdict("发票能力网关未返回查验结果", jsonBody, truncate(resp.body()));
            }
            JsonNode r = results.get(0);
            boolean pass = r.path("passed").asBoolean(false);
            String message = r.path("failMessage").asText("");
            if (message.isBlank()) {
                message = pass ? "查验一致(网关)" : "查验不一致(网关)";
            }
            log.info("[定时验真] 发票 {} 网关查验: {} - {}", inv.getId(), pass ? "PASS" : "FAIL", message);
            return new VerifyResult(pass, false, message, jsonBody, truncate(resp.body()));
        } catch (Exception e) {
            log.warn("[定时验真] 发票 {} 调用网关异常: {}", inv.getId(), e.getMessage());
            return VerifyResult.noVerdict("调用发票能力网关异常: " + e.getMessage(), jsonBody, null);
        }
    }

    /** 报文落 inv_verify_log 前的长度截断（req_param/resp_content 为 text，仍防单行失控） */
    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 4000 ? s : s.substring(0, 4000) + "…(截断)";
    }

    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String v = body.replaceAll("\\s+", " ").trim();
        return v.length() <= 200 ? v : v.substring(0, 200) + "…";
    }

    /**
     * 验真结果内部载体。
     *
     * @param pass 通道判定
     * @param inconclusive true=通道故障/无结果，本轮<b>不判定</b>（不写 verify_status，留原状态待下轮）
     * @param message 说明
     * @param request 实际发出的请求体（落 req_param）
     * @param rawResponse 通道返回原文（落 resp_content；曾被写成自造的 result=PASS）
     */
    private record VerifyResult(boolean pass, boolean inconclusive, String message,
                                String request, String rawResponse) {

        VerifyResult(boolean pass, String message, String request, String rawResponse) {
            this(pass, false, message, request, rawResponse);
        }

        static VerifyResult noVerdict(String message, String request, String rawResponse) {
            return new VerifyResult(false, true, message, request, rawResponse);
        }
    }
}
