package com.fssc.sharing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.LinkuSyncLog;
import com.fssc.sharing.entity.SupplierRecon;
import com.fssc.sharing.entity.SupplierReconDetail;
import com.fssc.sharing.entity.SupplierReconDiff;
import com.fssc.sharing.mapper.LinkuSyncLogMapper;
import com.fssc.sharing.service.SupplierReconService;
import com.fssc.connector.callback.InboundCallbackVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import com.fssc.common.core.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fssc.framework.tenant.TenantContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 供应商对账 REST API（预留第三方对账平台接口）。
 *
 * 接口分组：
 * 1. 对账单 CRUD：page / generate / summary
 * 2. 逐笔明细：details / autoMatch / manualMatch / unmatch
 * 3. 对账函：sendLetter
 * 4. 供应商确认：supplierConfirm
 * 5. 差异调节：diffs / addDiff / resolveDiff
 * 6. LinkU 对接（预留）：syncLinkU / linkuCallback
 * 7. 兼容旧接口：confirm / diff
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/pay/recon")
@RequiredArgsConstructor
public class SupplierReconController {

    private final SupplierReconService reconService;

    /** G 步：入站回调验签收敛到连接域统一入口（算法与 BCP/LinkU 一致，行为零变化） */
    private final InboundCallbackVerifier callbackVerifier;

    private final LinkuSyncLogMapper linkuSyncLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** LinkU 回调共享密钥；为空时拒绝所有回调（与 BCP fail-fast 对齐） */
    @Value("${fssc.linku-callback.secret:}")
    private String linkuSecret;

    /** 签名时间窗（秒）：|now - X-BCP-Timestamp| 超窗拒绝 */
    private static final long LINKU_MAX_SKEW_SECONDS = 300;

    /** 密钥注入点名称，启动告警与回调错误体共用同一规则，便于运维照提示自查（不打印密钥值） */
    private static final String LINKU_SECRET_HINT =
            "未注入 LinkU 回调共享密钥：配置项 fssc.linku-callback.secret（环境变量 FSSC_LINKU_CALLBACK_SECRET）为空";

    /**
     * 启动自检：密钥未注入时输出显式告警。
     * 只告警不拒启——本环境（dev/演示）没有 BCP 对端是常态，拒启会连带打断其它功能；
     * 但静默启动会让运维以为对接已就绪，因此必须在日志里一眼可见「回调已禁用」。
     * 绝不在此处或日志中打印密钥值，也不给任何环境塞默认值。
     */
    @PostConstruct
    public void checkCallbackSecret() {
        if (!StringUtils.hasText(linkuSecret)) {
            log.warn("{} —— 全部 linku-* 匿名回调端点（5 个）将一律以 401 拒绝（fail-closed）；"
                    + "需对接 BCP 回函时注入密钥并重启。", LINKU_SECRET_HINT);
        }
    }

    // 对账单 CRUD

    @RequiresPerm("pay:recon:list")
    @GetMapping("/page")
    public Result<PageResult<SupplierRecon>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return Result.success(reconService.page(pageNum, pageSize, keyword, status));
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/generate")
    public Result<Long> generate(@RequestBody Map<String, Object> body) {
        Long supplierId = requireLong(body, "supplierId");
        String period = (String) body.get("period");
        return Result.success(reconService.generate(supplierId, period));
    }

    @RequiresPerm("pay:recon:list")
    @GetMapping("/{id}/summary")
    public Result<Map<String, Object>> summary(@PathVariable("id") Long id) {
        return Result.success(reconService.getSummary(id));
    }

    // 逐笔明细与勾对

    @RequiresPerm("pay:recon:list")
    @GetMapping("/{id}/details")
    public Result<List<SupplierReconDetail>> details(@PathVariable("id") Long id) {
        return Result.success(reconService.listDetails(id));
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/{id}/auto-match")
    public Result<Map<String, Object>> autoMatch(@PathVariable("id") Long id) {
        int n = reconService.autoMatch(id);
        return Result.success(Map.of("matchedCount", n));
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/{id}/manual-match")
    public Result<Void> manualMatch(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long invoiceDetailId = requireLong(body, "invoiceDetailId");
        Long paymentDetailId = requireLong(body, "paymentDetailId");
        reconService.manualMatch(id, invoiceDetailId, paymentDetailId);
        return Result.success();
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/detail/{detailId}/unmatch")
    public Result<Void> unmatch(@PathVariable("detailId") Long detailId) {
        reconService.unmatch(detailId);
        return Result.success();
    }

    // 对账函

    @RequiresPerm("pay:recon:list")
    @PostMapping("/{id}/send-letter")
    public Result<Map<String, Object>> sendLetter(@PathVariable("id") Long id) {
        String letterNo = reconService.sendLetter(id);
        return Result.success(Map.of("letterNo", letterNo));
    }

    // 供应商确认

    @RequiresPerm("pay:recon:list")
    @PostMapping("/{id}/supplier-confirm")
    public Result<Void> supplierConfirm(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String confirmStatus = (String) body.get("confirmStatus");
        String remark = (String) body.get("remark");
        reconService.supplierConfirm(id, confirmStatus, remark);
        return Result.success();
    }

    // 差异调节

    @RequiresPerm("pay:recon:list")
    @GetMapping("/{id}/diffs")
    public Result<List<SupplierReconDiff>> diffs(@PathVariable("id") Long id) {
        return Result.success(reconService.listDiffs(id));
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/{id}/diffs")
    public Result<Long> addDiff(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String diffType = (String) body.get("diffType");
        BigDecimal ourAmount = body.get("ourAmount") != null
                ? new BigDecimal(body.get("ourAmount").toString()) : BigDecimal.ZERO;
        BigDecimal supplierAmount = body.get("supplierAmount") != null
                ? new BigDecimal(body.get("supplierAmount").toString()) : BigDecimal.ZERO;
        String reason = (String) body.get("reason");
        return Result.success(reconService.addDiff(id, diffType, ourAmount, supplierAmount, reason));
    }

    @RequiresPerm("pay:recon:list")
    @PostMapping("/diffs/{diffId}/resolve")
    public Result<Void> resolveDiff(@PathVariable("diffId") Long diffId, @RequestBody Map<String, Object> body) {
        String adjustmentType = (String) body.get("adjustmentType");
        String voucherNo = (String) body.get("voucherNo");
        String remark = (String) body.get("remark");
        reconService.resolveDiff(diffId, adjustmentType, voucherNo, remark);
        return Result.success();
    }

    // LinkU 对接接口（回调接收模式）

    /**
     * 查看 LinkU 同步状态（只读）。
     * 架构已纠正：FSSC 不主动推送数据到 LinkU，而是被动接收 LinkU 的 Webhook 回调。
     */
    @RequiresPerm("pay:recon:list")
    @GetMapping("/{id}/linku-status")
    public Result<Map<String, Object>> linkuStatus(@PathVariable("id") Long id) {
        SupplierRecon r = reconService.getLinkUStatus(id);
        return Result.success(Map.of(
                "linkuSyncStatus", r.getLinkuSyncStatus() != null ? r.getLinkuSyncStatus() : "NONE",
                "linkuSyncTime", r.getLinkuSyncTime() != null ? r.getLinkuSyncTime().toString() : "",
                "linkuReconId", r.getLinkuReconId() != null ? r.getLinkuReconId() : 0
        ));
    }

    /**
     * LinkU Webhook 回调：统一入口（方案1）。
     * BCP 投递单一 URL，信封 X-BCP-Event-Type 指明事件；先验 HMAC 签名+时间窗，
     * 合并信封顶层与 payload 后按事件类型内部转发到对应对账方法，并落 linku_sync_log。
     */
    @PostMapping("/linku-callback")
    public Result<Object> linkuCallback(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = readBody(req);
        return guardAndExecute(req, resp, rawBody, "RECON_CONFIRM", this::dispatchEnvelope);
    }

    /**
     * LinkU Webhook 回调 2：三单匹配结果回传。
     */
    @PostMapping("/linku-match-result")
    public Result<Object> linkuMatchResult(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = readBody(req);
        return guardAndExecute(req, resp, rawBody, "RECON_MATCH", m -> reconService.receiveLinkUMatchResult(
                linkuId(m), str(m, "matchStatus", str(m, "reconStatus")),
                asMap(m, "matchSummary"), asList(m, "matchItems")));
    }

    /**
     * LinkU Webhook 回调 3：差异处理结果回传。
     */
    @PostMapping("/linku-diff-resolved")
    public Result<Object> linkuDiffResolved(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = readBody(req);
        return guardAndExecute(req, resp, rawBody, "DIFF_RESOLVED", m -> reconService.receiveLinkUDiffResolved(
                linkuId(m), lid(m.get("diffId")), str(m, "diffType"), dec(m, "diffAmount", BigDecimal.ZERO),
                str(m, "handleStatus"), str(m, "handleBy"), str(m, "handleRemark"), str(m, "adjustmentType")));
    }

    /**
     * LinkU Webhook 回调 4：对账确认单回传。
     */
    @PostMapping("/linku-recon-confirmed")
    public Result<Object> linkuReconConfirmed(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = readBody(req);
        return guardAndExecute(req, resp, rawBody, "RECON_CONFIRMED", m -> reconService.receiveLinkUReconConfirmed(
                linkuId(m), str(m, "reconNo"), str(m, "supplierCode"), str(m, "supplierName"), str(m, "period"),
                dec(m, "openingBalance", BigDecimal.ZERO), dec(m, "periodPayable", BigDecimal.ZERO),
                dec(m, "periodPaid", BigDecimal.ZERO), dec(m, "closingBalance", BigDecimal.ZERO),
                str(m, "confirmedAt"), str(m, "confirmedBy")));
    }

    /**
     * LinkU Webhook 回调 5：发票验真结果回传。
     */
    @PostMapping("/linku-invoice-verified")
    public Result<Object> linkuInvoiceVerified(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = readBody(req);
        return guardAndExecute(req, resp, rawBody, "INVOICE_VERIFIED", m -> reconService.receiveLinkUInvoiceVerified(
                linkuId(m), str(m, "invoiceNo"), str(m, "invoiceCode"), str(m, "invoiceDate"),
                dec(m, "invoiceAmount", BigDecimal.ZERO), dec(m, "taxAmount", BigDecimal.ZERO),
                str(m, "verifyStatus"), str(m, "verifyRemark"), str(m, "duplicateCheck"), str(m, "ocrFileUrl")));
    }

    // LinkU 回调统一守卫：验签 + 去重 + 落 log

    /**
     * 统一起点：0) 无密钥 fail-fast；1) HMAC 验签+时间窗；2) 去重去重；3) 执行业务；4) 落 linku_sync_log。
     * 验签/执行失败返回对应 HTTP 非 2xx 状态码（body.code 保持一致），供 BCP 判为重试信号。
     *
     * 第二项（写入侧办结）：**未过签的请求不写 linku_sync_log**，改记应用日志。
     * 回调端点对外可达，原先验签失败也 INSERT 一条含完整请求体的记录 ⇒
     * 任何人反复投递即可无界撑大该表（未鉴权写入面 + 请求体全量入库）。
     * 已过签的"业务执行失败"仍照常写入数据库——那是合法来源的审计线索，不能丢。
     * 未过签侧保留 IP + eventType + 拒绝码在应用日志里，审计可查但不写入数据库，且绝不打印密钥值。
     */
    private Result<Object> guardAndExecute(HttpServletRequest req, HttpServletResponse resp,
                                           String rawBody, String defaultEventType,
                                           Consumer<Map<String, Object>> consumer) throws IOException {
        long start = System.currentTimeMillis();
        String eventType = req.getHeader("X-BCP-Event-Type");
        if (!StringUtils.hasText(eventType)) {
            eventType = defaultEventType;
        }
        Map<String, Object> merged = mergeEnvelope(rawBody);
        Long linkuReconId = linkuId(merged);
        String idemKey = callbackIdempotencyKey(req, merged, linkuReconId);

        // 0) 未配置 secret：拒绝所有回调（fail-fast）。错误体带自诊断规则，对端不必翻源码。
        if (!StringUtils.hasText(linkuSecret)) {
            resp.setStatus(401);
            String diag = "LINKU_CALLBACK_DISABLED|" + LINKU_SECRET_HINT;
            rejectWithoutPersist(req, eventType, linkuReconId, "SECRET_NOT_CONFIGURED");
            return Result.fail(401, diag);
        }

        // 1) HMAC 验签 + 时间窗
        InboundCallbackVerifier.Result sig = callbackVerifier.verify(linkuSecret,
                req.getHeader("X-BCP-Timestamp"), req.getHeader("X-BCP-Signature"),
                rawBody, System.currentTimeMillis() / 1000, LINKU_MAX_SKEW_SECONDS);
        if (!sig.ok()) {
            int status = sig.isTimestampError() ? 400 : 401;
            resp.setStatus(status);
            rejectWithoutPersist(req, eventType, linkuReconId,
                    sig.isTimestampError() ? "INVALID_TIMESTAMP" : "INVALID_SIGNATURE");
            return Result.fail(status, sig.isTimestampError() ? "INVALID_LINKU_TIMESTAMP" : "INVALID_LINKU_SIGNATURE");
        }

        // 2) 去重：同事件同唯一键已成功处理过则跳过，避免重复副作用
        if (alreadyProcessed(eventType, idemKey, linkuReconId)) {
            return Result.success();
        }

        // 3) 执行业务（匿名租户上下文）
        try {
            TenantContext.runIgnoreTenant(() -> consumer.accept(merged));
            writeLog(eventType, linkuReconId, idemKey, merged, "SUCCESS", null, System.currentTimeMillis() - start);
            return Result.success();
        } catch (Exception ex) {
            log.error("LinkU 回调处理失败 eventType={} linkuReconId={} err={}",
                    eventType, linkuReconId, ex.getMessage(), ex);
            writeLog(eventType, linkuReconId, idemKey, merged, "FAILED",
                    String.valueOf(ex.getMessage()), System.currentTimeMillis() - start);
            resp.setStatus(500);
            return Result.fail(500, "LINKU_CALLBACK_FAILED");
        }
    }

    /** 方案1：以统一入口为准，按信封 eventType 内部转发到对应对账方法 */
    private void dispatchEnvelope(Map<String, Object> m) {
        String eventType = str(m, "eventType");
        if (eventType == null) {
            eventType = str(m, "bcpEventType");
        }
        if (eventType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "envelope missing eventType");
        }
        switch (eventType) {
            case "RECON_CONFIRM" -> {
                String cs = str(m, "confirmStatus");
                if (cs == null) {
                    // BCP 载荷语义映射：reconStatus=CONFIRMED→确认；其余(DIFF/…)→有异议
                    cs = "CONFIRMED".equals(str(m, "reconStatus")) ? "CONFIRMED" : "REJECTED";
                }
                reconService.receiveLinkUConfirm(linkuId(m), cs, str(m, "remark", str(m, "comment")));
            }
            case "RECON_MATCH" -> reconService.receiveLinkUMatchResult(
                    linkuId(m), str(m, "matchStatus", str(m, "reconStatus")),
                    asMap(m, "matchSummary"), asList(m, "matchItems"));
            case "DIFF_RESOLVED" -> reconService.receiveLinkUDiffResolved(
                    linkuId(m), lid(m.get("diffId")), str(m, "diffType"), dec(m, "diffAmount", BigDecimal.ZERO),
                    str(m, "handleStatus"), str(m, "handleBy"), str(m, "handleRemark"), str(m, "adjustmentType"));
            case "RECON_CONFIRMED" -> reconService.receiveLinkUReconConfirmed(
                    linkuId(m), str(m, "reconNo"), str(m, "supplierCode"), str(m, "supplierName"), str(m, "period"),
                    dec(m, "openingBalance", BigDecimal.ZERO), dec(m, "periodPayable", BigDecimal.ZERO),
                    dec(m, "periodPaid", BigDecimal.ZERO), dec(m, "closingBalance", BigDecimal.ZERO),
                    str(m, "confirmedAt"), str(m, "confirmedBy"));
            case "INVOICE_VERIFIED" -> reconService.receiveLinkUInvoiceVerified(
                    linkuId(m), str(m, "invoiceNo"), str(m, "invoiceCode"), str(m, "invoiceDate"),
                    dec(m, "invoiceAmount", BigDecimal.ZERO), dec(m, "taxAmount", BigDecimal.ZERO),
                    str(m, "verifyStatus"), str(m, "verifyRemark"), str(m, "duplicateCheck"), str(m, "ocrFileUrl"));
            default -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "unsupported eventType: " + eventType);
        }
    }

    /** 读取原始请求体（保证参与签名的原文不被框架二次解析改动） */
    private String readBody(HttpServletRequest req) throws IOException {
        try (InputStream in = req.getInputStream()) {
            byte[] buf = in.readAllBytes();
            return buf == null || buf.length == 0 ? "" : new String(buf, StandardCharsets.UTF_8);
        }
    }

    /** 解析 JSON；同时把信封顶层与 payload 合并成一张 Map（顶层优先，payload 缺省补位） */
    private Map<String, Object> mergeEnvelope(String rawBody) {
        Map<String, Object> top = parseJson(rawBody);
        Map<String, Object> m = top != null ? top : new HashMap<>();
        Object payload = m.get("payload");
        if (payload instanceof Map<?, ?> p) {
            for (Map.Entry<?, ?> e : p.entrySet()) {
                if (e.getKey() != null) {
                    m.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.warn("LinkU 回调体解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 第二项：未过签请求的拒绝只记应用日志，不写 linku_sync_log。
     * 记 IP + eventType + linkuReconId + 拒绝码；不落请求体、不落密钥值。
     */
    private void rejectWithoutPersist(HttpServletRequest req, String eventType, Long linkuReconId, String reason) {
        log.warn("LinkU 回调被拒（未落库，避免对外可达端点上的无界写入）: ip={} eventType={} linkuReconId={} reason={}",
                req.getRemoteAddr(), eventType, linkuReconId, reason);
    }

    /** 落 linku_sync_log 审计记录（仅在验签通过后的处理结果上调用） */
    private void writeLog(String eventType, Long linkuReconId, String idempotencyKey, Map<String, Object> m,
                          String status, String errorMsg, long durationMs) {
        try {
            LinkuSyncLog log = new LinkuSyncLog();
            log.setEventType(eventType);
            log.setLinkuReconId(linkuReconId);
            log.setIdempotencyKey(idempotencyKey);
            log.setSupplierId(parseSupplierId(m.get("partnerId")));
            log.setRequestBody(toJson(m));
            log.setResponseStatus(status);
            log.setErrorMsg(errorMsg);
            log.setDurationMs((int) durationMs);
            log.setCreateTime(LocalDateTime.now());
            linkuSyncLogMapper.insert(log);
        } catch (DuplicateKeyException dup) {
            // 撞 uk_linku_sync_log_idem 说明"同一唯一键已有一条 SUCCESS 记录"，
            // 即本事件已被处理过一次（重复投递/并发投递）。这属于去重命中，不是故障，
            // 不能按普通异常吞掉 —— 否则"记录丢失"会被伪装成"从未收到过该回调"。
            log.info("[LinkU回调] 幂等命中：同一幂等键已有成功留痕，本次不再重复落库 eventType={} idemKey={}",
                    eventType, idempotencyKey);
        } catch (Exception ex) {
            log.error("LinkU 回写 linku_sync_log 失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 回调唯一键。
     *
     * <p>取键顺序：① 信封 body 的 {@code idempotencyKey}；② BCP 投递头 {@code X-BCP-Event-Id}
     * （每条发件箱事件唯一：重投同一事件取值相同 ⇒ 可去重；不同事件取值不同 ⇒ 不互撞）；
     * ③ 回落到 {@code linkuReconId}。三者皆空才退化为"无去重保护"。</p>
     *
     * <p>该键写入 {@code linku_sync_log.idempotency_key}，与 event_type 合成生成列
     * {@code active_idem_key}。该列未回填时恒为 NULL ⇒ 生成列恒等于
     * {@code "RECON_MATCH:"}（冒号后为空），第二条成功记录必撞唯一键。</p>
     */
    private String callbackIdempotencyKey(HttpServletRequest req, Map<String, Object> m, Long linkuReconId) {
        String k = str(m, "idempotencyKey");
        if (!StringUtils.hasText(k)) {
            k = req.getHeader("X-BCP-Event-Id");
        }
        if (!StringUtils.hasText(k) && linkuReconId != null) {
            k = "R-" + linkuReconId;
        }
        if (!StringUtils.hasText(k)) {
            return null;
        }
        // ':' 是 active_idem_key 的分隔符，键体内不得出现；列宽 128，留出 event_type 前缀余量
        String normalized = k.replace(':', '-').trim();
        return normalized.length() <= 110 ? normalized : normalized.substring(0, 110);
    }

    private boolean alreadyProcessed(String eventType, String idempotencyKey, Long linkuReconId) {
        if (eventType == null) {
            return false;
        }
        LambdaQueryWrapper<LinkuSyncLog> w = new LambdaQueryWrapper<LinkuSyncLog>()
                .eq(LinkuSyncLog::getEventType, eventType)
                .eq(LinkuSyncLog::getResponseStatus, "SUCCESS");
        if (StringUtils.hasText(idempotencyKey)) {
            w.eq(LinkuSyncLog::getIdempotencyKey, idempotencyKey);
        } else if (linkuReconId != null) {
            w.eq(LinkuSyncLog::getLinkuReconId, linkuReconId);
        } else {
            // 既无唯一键也无对账单ID：无法判定是否重复，按"未处理"放行（后续照常落记录）
            return false;
        }
        Long cnt = linkuSyncLogMapper.selectCount(w);
        return cnt != null && cnt > 0;
    }

    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return String.valueOf(m);
        }
    }

    /** 信封 linkuReconId（顶层）；可空 */
    private Long linkuId(Map<String, Object> m) {
        return lid(m.get("linkuReconId"));
    }

    private Long lid(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 语义映射：BCP 侧 supplierId/partnerId 可能是字符串编码，entity 为 Long，无法解析则置空 */
    private Long parseSupplierId(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String str(Map<String, Object> m, String key, String def) {
        String s = str(m, key);
        return s == null ? def : s;
    }

    private BigDecimal dec(Map<String, Object> m, String key, BigDecimal def) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Map<String, Object>>) v : null;
    }

    /** Map body 取必填 Long：缺参/类型不符一律 400，不让它变成 500（裸 ((Number)map.get(k)).longValue() 会NPE）。 */
    private static Long requireLong(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少必填参数: " + key);
        }
        if (!(v instanceof Number)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数类型错误: " + key + " 应为数字");
        }
        return ((Number) v).longValue();
    }
}
