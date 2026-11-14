package com.fssc.openapi.linku.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.openapi.linku.LinkuOpenGuard;
import com.fssc.openapi.linku.LinkuOpenProperties;
import com.fssc.openapi.linku.service.LinkuOpenQueryService;
import com.fssc.openapi.linku.service.LinkuOpenSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * LinkU 开放接口（BCP → FSSC 入站受控通道）。
 *
 * <h3>它替代什么</h3>
 * 原方案：BCP 持有 FSSC 内网库读写账号、跨网直连 MySQL。现改为 FSSC 暴露本组接口，
 * BCP 以 HMAC-SHA256 签名调用，BCP 侧不再持有任何 FSSC 数据库连接。
 *
 * <h3>端点分组</h3>
 * <ul>
 *   <li><b>查询</b>（GET {@code /api/open/linku/query/**}）：签名原文为<b>空串</b>；</li>
 *   <li><b>写入</b>（POST {@code /api/open/linku/sync/**}）：签名原文为请求体原文。</li>
 * </ul>
 * 全部经 {@link LinkuOpenGuard} 统一验签 → 去重 → 审计；未过签不落业务表。
 *
 * <h3>匿名可达但默认 fail-closed</h3>
 * 本路径在 {@code JwtAuthFilter} 白名单内（外部系统无业务 Token），
 * 因此安全完全由验签把关；未配置共享密钥时一律 401 拒绝（见 {@link LinkuOpenProperties}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/open/linku")
@RequiredArgsConstructor
public class LinkuOpenApiController {

    private final LinkuOpenGuard guard;
    private final LinkuOpenProperties props;
    private final LinkuOpenQueryService queryService;
    private final LinkuOpenSyncService syncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 查询

    @GetMapping("/query/payables")
    public Result<Object> payables(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PAYABLES",
                () -> queryService.payables(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        str(p.get("period")), intOf(p.get("limit"))));
    }

    @GetMapping("/query/payments")
    public Result<Object> payments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PAYMENTS",
                () -> queryService.payments(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        intOf(p.get("limit"))));
    }

    @GetMapping("/query/purchase-receipts")
    public Result<Object> purchaseReceipts(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PURCHASE_RECEIPTS",
                () -> queryService.purchaseReceipts(tenant(p), longOf(p.get("companyId")),
                        str(p.get("partnerCode")), intOf(p.get("limit"))));
    }

    @GetMapping("/query/purchase-returns")
    public Result<Object> purchaseReturns(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PURCHASE_RETURNS",
                () -> queryService.purchaseReturns(tenant(p), longOf(p.get("companyId")),
                        str(p.get("partnerCode")), str(p.get("unifiedCode")), intOf(p.get("limit"))));
    }

    @GetMapping("/query/receivables")
    public Result<Object> receivables(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_RECEIVABLES",
                () -> queryService.receivables(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        str(p.get("period")), intOf(p.get("limit"))));
    }

    @GetMapping("/query/receipts")
    public Result<Object> receipts(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_RECEIPTS",
                () -> queryService.receipts(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        intOf(p.get("limit"))));
    }

    /** AR 预制发票环节（三单匹配中间环节）；OTC 代无此表时返回空列表（自动退化为两单匹配）。 */
    @GetMapping("/query/pre-invoices")
    public Result<Object> preInvoices(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PRE_INVOICES",
                () -> queryService.preInvoices(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        intOf(p.get("limit"))));
    }

    /**
     * AR 销售订单环节（三单匹配改造的一腿）。候选表 {@code so_order} 缺失时返回空并告警
     * —— 数据源未就绪，BCP 侧 AR 匹配缺订单环节不自动通过，不回退采购订单。
     */
    @GetMapping("/query/sales-orders")
    public Result<Object> salesOrders(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_SALES_ORDERS",
                () -> queryService.salesOrders(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        str(p.get("period")), intOf(p.get("limit"))));
    }

    /** AR 销售出库发货环节。候选表 {@code so_shipment} 缺失时返回空并告警。 */
    @GetMapping("/query/sale-shipments")
    public Result<Object> saleShipments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_SALE_SHIPMENTS",
                () -> queryService.saleShipments(tenant(p), longOf(p.get("companyId")), str(p.get("partnerCode")),
                        intOf(p.get("limit"))));
    }

    /** 往来单位主数据（供应商／客户）。 */
    @GetMapping("/query/partner")
    public Result<Object> partner(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PARTNER",
                () -> queryService.partner(tenant(p), str(p.get("partnerCode")), str(p.get("type")),
                        intOf(p.get("limit"))));
    }

    /** 期间冻结状态（协同冻结校验：关账期间禁止对账/核销写入）。 */
    @GetMapping("/query/period-status")
    public Result<Object> periodStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> p = params(req);
        return guard.guard(req, resp, "", "QUERY_PERIOD_STATUS",
                () -> queryService.periodStatus(tenant(p), longOf(p.get("companyId")), str(p.get("period"))));
    }

    // 写入

    /** 对账结果回写（AP → supplier_recon 三表；AR → ar_recon_record）。 */
    @PostMapping("/sync/recon")
    public Result<Object> syncRecon(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "RECON_SYNC", () -> syncService.syncRecon(parse(raw)));
    }

    /** 对账差异追加（按既有对账头定位）。 */
    @PostMapping("/sync/recon-diff")
    public Result<Object> syncReconDiff(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "RECON_DIFF_SYNC", () -> syncService.syncReconDiff(parse(raw)));
    }

    /** 发票（含电子发票号、验真状态）。 */
    @PostMapping("/sync/invoice")
    public Result<Object> syncInvoice(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "INVOICE_SYNC", () -> syncService.syncInvoice(parse(raw)));
    }

    /** 核销结果（AR 收款核销／对冲；AP 应付单最终状态）。 */
    @PostMapping("/sync/writeoff")
    public Result<Object> syncWriteoff(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "WRITEOFF_SYNC", () -> syncService.syncWriteoff(parse(raw)));
    }

    /** 应收应付对冲记录（gl_inter_offset，无现金流入，不生成收款单）。 */
    @PostMapping("/sync/offset-trace")
    public Result<Object> syncOffsetTrace(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "OFFSET_TRACE_SYNC", () -> syncService.syncOffsetTrace(parse(raw)));
    }

    /** 应收单／应收对账头（AR）。 */
    @PostMapping("/sync/receivable")
    public Result<Object> syncReceivable(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "RECEIVABLE_SYNC", () -> syncService.syncReceivable(parse(raw)));
    }

    /** 收款单（含核销明细与应收余额回写）。 */
    @PostMapping("/sync/receipt")
    public Result<Object> syncReceipt(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "RECEIPT_SYNC", () -> syncService.syncReceipt(parse(raw)));
    }

    /** AR 核销结果与应收应付对冲记录。 */
    @PostMapping("/sync/ar-writeoff")
    public Result<Object> syncArWriteoff(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "AR_WRITEOFF_SYNC", () -> syncService.syncArWriteoff(parse(raw)));
    }

    /** 付款／收款状态回写。 */
    @PostMapping("/sync/payment-status")
    public Result<Object> syncPaymentStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String raw = readBody(req);
        return guard.guard(req, resp, raw, "PAYMENT_STATUS_SYNC", () -> syncService.syncPaymentStatus(parse(raw)));
    }

    // 工具

    /** 读取原始请求体（保证参与签名的原文不被框架二次解析改动）。 */
    private String readBody(HttpServletRequest req) throws IOException {
        try (InputStream in = req.getInputStream()) {
            byte[] buf = in.readAllBytes();
            return buf == null || buf.length == 0 ? "" : new String(buf, StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new BizException(400, "请求体不是合法 JSON：" + e.getMessage());
        }
    }

    private Map<String, Object> params(HttpServletRequest req) {
        Map<String, Object> m = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                m.put(k, v[0]);
            }
        });
        return m;
    }

    /** 租户：入参优先，缺省取配置缺省租户（请求已验签，故可信任）。 */
    private Long tenant(Map<String, Object> p) {
        Long t = longOf(p.get("tenantId"));
        return t != null ? t : props.defaultTenantId();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private static Integer intOf(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }
}
