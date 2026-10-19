// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.openapi.linku.service;

import com.fssc.common.exception.BizException;
import com.fssc.openapi.linku.LinkuOpenProperties;
import com.fssc.openapi.linku.mapper.LinkuOpenSyncMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LinkU 开放接口写入服务（F-3 / F-7）。语义逐条移植自 {@code FsscArApAdapter} 的回写路径。
 *
 * <h3>原子性</h3>
 * 每个入口方法以 {@code @Transactional} 包裹：一次回写（头表 + 明细 + 差异 + 余额 + 留痕）
 * 要么全部生效、要么整体回滚，杜绝「头表写了、差异没写」的半成功。
 * 失败必抛异常 → 由守卫返回非 2xx → BCP 侧按可重试处理（与 {@code ArApDataProvider} 契约一致）。
 *
 * <h3>幂等</h3>
 * 幂等由守卫在入口按 {@code (eventType, idempotencyKey)} 去重；服务内部再叠加
 * <b>自然键幂等</b>（{@code supplier_recon} 按 tenant+supplier+period、明细按 item_no、
 * 差异按 reason 去重插入），使「重放」与「重跑对账」都不会产生重复副作用。
 *
 * <h3>OTC 代的结构性能力缺口（诚实登记，与原直连实现同口径）</h3>
 * OTC 代 AP 真实付款与 AP 付款状态在 FSSC 无单据级写回目标（付款执行在 {@code pay_plan → op_pay_batch}），
 * 记 WARN 后正常返回而非报错 —— 对任何单据恒定不适用，报错只会产生无法补偿的审计噪音。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkuOpenSyncService {

    private static final String CLAIM_TYPE_AR_AP_OFFSET = "AR_AP_OFFSET";
    private static final String DEFAULT_PAY_METHOD = "银行";
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final DateTimeFormatter PERIOD_YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final LinkuOpenSyncMapper mapper;
    private final LinkuOpenProperties props;

    // ==================== 对账（AP / AR 共用入口） ====================

    /** 对账结果回写：{@code direction=AP} → {@code supplier_recon} 三表；{@code AR} → {@code ar_recon_record}。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncRecon(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        String direction = upper(str(body.get("direction")));
        if ("AR".equals(direction)) {
            return writeArRecon(body, tenantId);
        }
        return writeApRecon(body, tenantId);
    }

    /** AR 应收单／对账头：收敛为 AR 对账回写（{@code ar_recon_record}），与原直连口径一致。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncReceivable(Map<String, Object> body) {
        return writeArRecon(body, tenantId(body));
    }

    private Map<String, Object> writeApRecon(Map<String, Object> body, Long tenantId) {
        Long reconId = asLong(body.get("linkuReconId"));
        if (reconId == null) {
            reconId = asLong(body.get("reconId"));
        }
        if (reconId == null) {
            throw new BizException(400, "缺少 linkuReconId，无法建立 supplier_recon.linku_recon_id 幂等键");
        }
        String partnerCode = str(body.get("partnerId"));
        Long supplierId = mapper.selectSupplierIdByCode(tenantId, partnerCode);
        if (supplierId == null) {
            throw new BizException(400, "供应商编码未在 md_supplier 建档 partner=" + partnerCode);
        }
        String period = toFsccPeriod(resolvePeriod(body));
        LocalDateTime now = LocalDateTime.now();

        List<String> matched = strList(body.get("matchedBillNos"));
        List<String> diff = strList(body.get("diffBillNos"));
        Map<String, Object> summary = asMap(body.get("summary"));

        BigDecimal payableAmount = summaryAmount(summary, "anchorTotal", "payableAmount");
        BigDecimal paidAmount = summaryAmount(summary, "settledAmount", "paidAmount");
        BigDecimal balance = summaryAmount(summary, "balance", "balanceAmount");
        BigDecimal openingBalance = balance.subtract(payableAmount).add(paidAmount);
        String newStatus = diff.isEmpty() ? "DRAFT" : "DIFF";
        String diffRemark = diffRemark(body, summary);

        Long rowId = mapper.selectReconRowId(tenantId, supplierId, period);
        if (rowId == null) {
            mapper.insertSupplierRecon(tenantId, supplierId, buildReconNo(reconId), period,
                    payableAmount, paidAmount, balance, openingBalance, newStatus, diffRemark,
                    reconId, asLong(body.get("partnerPkId")), asLong(body.get("companyId")), now);
            rowId = mapper.selectReconRowId(tenantId, supplierId, period);
        } else {
            mapper.updateSupplierRecon(rowId, payableAmount, paidAmount, balance, openingBalance,
                    newStatus, diffRemark, reconId, asLong(body.get("partnerPkId")), now);
        }
        if (rowId == null) {
            throw new BizException(500, "supplier_recon 已写入但回查不到 id，period=" + period + " supplier=" + supplierId);
        }

        int detailInserted = insertReconDetails(tenantId, rowId, matched);
        int diffInserted = insertReconDiffs(tenantId, rowId, diff, diffType(summary), body.get("diffReason"),
                payableAmount, paidAmount, balance, now);
        log.info("[LinkU开放接口] AP 对账已回写 supplier_recon reconId={} rowId={} period={} 明细={} 差异={}",
                reconId, rowId, period, detailInserted, diffInserted);

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("reconRowId", rowId);
        out.put("period", period);
        out.put("detailsInserted", detailInserted);
        out.put("diffsInserted", diffInserted);
        return out;
    }

    private Map<String, Object> writeArRecon(Map<String, Object> body, Long tenantId) {
        List<String> matched = strList(body.get("matchedBillNos"));
        List<String> diff = strList(body.get("diffBillNos"));
        if (matched.isEmpty() && diff.isEmpty()) {
            log.info("[LinkU开放接口] AR 对账无可回写对账单 reconId={} partner={}",
                    body.get("linkuReconId"), body.get("partnerId"));
            return Map.of("skipped", true);
        }
        Long customerId = mapper.selectCustomerIdByCode(tenantId, str(body.get("partnerId")));
        if (customerId == null) {
            throw new BizException(400, "客户编码未在 crm_customer 建档 partner=" + body.get("partnerId"));
        }
        Long companyId = asLong(body.get("companyId"));
        String period = toPeriodDashed(resolvePeriod(body));
        boolean hasDiff = !diff.isEmpty();
        String status = hasDiff ? "RUNNING" : "CONFIRMED";
        String selectedTag = "SELECTED".equals(str(body.get("generateMode"))) ? "[按单勾选]" : "";
        String remark = trim500(selectedTag + "[BCP]对账" + (hasDiff ? "存在差异" : "已确认")
                + " 已对账=" + matched.size() + " 异常=" + diff.size()
                + (hasDiff && body.get("diffReason") != null ? "；" + body.get("diffReason") : ""));
        LocalDateTime now = LocalDateTime.now();

        Long rowId = mapper.selectArReconRowId(tenantId, companyId, customerId, period);
        if (rowId == null) {
            mapper.insertArReconRecord(tenantId, period, customerId, companyId, status, remark, now);
        } else {
            mapper.updateArReconRecord(rowId, status, remark, now);
        }
        log.info("[LinkU开放接口] AR 对账已回写 ar_recon_record reconId={} period={} customer={} status={} 已对账={} 异常={}",
                body.get("linkuReconId"), period, body.get("partnerId"), status, matched.size(), diff.size());
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("recordType", "ar_recon_record");
        out.put("period", period);
        out.put("status", status);
        return out;
    }

    private int insertReconDetails(Long tenantId, Long rowId, List<String> billNos) {
        if (billNos.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(mapper.selectDetailItemNos(tenantId, rowId));
        LocalDate today = LocalDate.now();
        int inserted = 0;
        for (String billNo : billNos) {
            if (billNo == null || billNo.isBlank() || existing.contains(billNo)) {
                continue;
            }
            mapper.insertReconDetail(tenantId, rowId, billNo, today, null, LocalDateTime.now());
            inserted++;
        }
        return inserted;
    }

    private int insertReconDiffs(Long tenantId, Long rowId, List<String> billNos, String diffType,
                                 Object diffReason, BigDecimal ourAmount, BigDecimal supplierAmount,
                                 BigDecimal diffAmount, LocalDateTime now) {
        if (billNos.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(mapper.selectDiffReasons(tenantId, rowId));
        int inserted = 0;
        for (String billNo : billNos) {
            if (billNo == null || billNo.isBlank()) {
                continue;
            }
            String reason = trim500(billNo + " " + (diffReason == null ? "" : diffReason));
            if (existing.contains(reason)) {
                continue;
            }
            mapper.insertReconDiff(tenantId, rowId, diffType, ourAmount, supplierAmount, diffAmount,
                    reason, LocalDateTime.now());
            inserted++;
        }
        return inserted;
    }

    /** 独立差异写入：按 {@code linkuReconId} 定位既有对账行后追加差异（无 BCP 调用方，契约保留）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncReconDiff(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        String partnerCode = str(body.get("partnerId"));
        Long supplierId = mapper.selectSupplierIdByCode(tenantId, partnerCode);
        if (supplierId == null) {
            throw new BizException(400, "供应商编码未在 md_supplier 建档 partner=" + partnerCode);
        }
        String period = toFsccPeriod(resolvePeriod(body));
        Long rowId = mapper.selectReconRowId(tenantId, supplierId, period);
        if (rowId == null) {
            throw new BizException(400, "未找到对账头，无法追加差异 period=" + period + " partner=" + partnerCode);
        }
        Map<String, Object> summary = asMap(body.get("summary"));
        int inserted = insertReconDiffs(tenantId, rowId, strList(body.get("diffBillNos")), diffType(summary),
                body.get("diffReason"),
                summaryAmount(summary, "anchorTotal", "payableAmount"),
                summaryAmount(summary, "settledAmount", "paidAmount"),
                summaryAmount(summary, "balance", "balanceAmount"), LocalDateTime.now());
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("reconRowId", rowId);
        out.put("diffsInserted", inserted);
        return out;
    }

    // ==================== 核销（AR / AP 共用入口） ====================

    /** 核销回写：AR → 收款核销／对冲；AP → 应付单终态（蓝图代）或结构性跳过（OTC 代）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncWriteoff(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        String direction = upper(str(body.get("direction")));
        boolean isOffset = CLAIM_TYPE_AR_AP_OFFSET.equals(upper(str(body.get("writeoffType"))));
        List<Map<String, Object>> items = asMapList(body.get("items"));
        if (items.isEmpty()) {
            return Map.of("skipped", true, "reason", "items empty");
        }
        LocalDateTime now = LocalDateTime.now();
        if ("AR".equals(direction)) {
            if (isOffset) {
                writeArOffset(tenantId, asLong(body.get("companyId")), items, now);
            } else {
                writeArReceiptWriteoff(tenantId, str(body.get("partnerId")), items, now);
            }
        } else {
            writeApWriteoff(tenantId, asLong(body.get("companyId")), str(body.get("partnerId")), items,
                    isOffset, now);
        }
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("direction", direction);
        out.put("offset", isOffset);
        out.put("items", items.size());
        return out;
    }

    /** AR 收款核销：{@code rc_receipt} + {@code rc_receipt_line} + 应收余额回写。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncReceipt(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        List<Map<String, Object>> items = asMapList(body.get("items"));
        if (items.isEmpty()) {
            return Map.of("skipped", true, "reason", "items empty");
        }
        writeArReceiptWriteoff(tenantId, str(body.get("partnerId")), items, LocalDateTime.now());
        return Map.of("receiptItems", items.size());
    }

    /** AR 核销结果与应收应付对冲留痕：{@code rc_receivable} 余额 + {@code gl_inter_offset}。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncArWriteoff(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        List<Map<String, Object>> items = asMapList(body.get("items"));
        writeArOffset(tenantId, asLong(body.get("companyId")), items, LocalDateTime.now());
        return Map.of("offsetItems", items.size());
    }

    /**
     * AR/AP 对冲留痕：仅调应收余额 + 写 {@code gl_inter_offset}，<b>不</b>生成收款单
     * （对冲无现金流入，写成收款会虚增当期收款）。与原直连 OTC 口径一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncOffsetTrace(Map<String, Object> body) {
        return syncArWriteoff(body);
    }

    private void writeArReceiptWriteoff(Long tenantId, String partnerCode,
                                        List<Map<String, Object>> items, LocalDateTime now) {
        Long customerId = mapper.selectCustomerIdByCode(tenantId, partnerCode);
        if (customerId == null) {
            throw new BizException(400, "客户编码未在 crm_customer 建档 partner=" + partnerCode);
        }
        List<Map<String, Object>> valid = new java.util.ArrayList<>();
        List<Long> validRids = new java.util.ArrayList<>();
        List<String> unresolved = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            BigDecimal amount = asDecimal(item.get("amount"));
            String billNo = str(item.get("sourceBillNo"));
            if (amount == null || billNo == null) {
                continue;
            }
            Long rid = mapper.selectReceivableIdByBillNo(tenantId, billNo);
            if (rid == null) {
                unresolved.add(billNo);
                continue;
            }
            valid.add(item);
            validRids.add(rid);
        }
        if (!unresolved.isEmpty()) {
            // 部分解析失败必须整单失败：跳过明细却照旧扣余额会造出「收款单与核销明细对不上」的错账
            throw new BizException(400, "以下应收单在 rc_receivable 中不存在，无法生成核销明细：" + unresolved);
        }
        if (valid.isEmpty()) {
            return;
        }
        BigDecimal total = valid.stream()
                .map(i -> asDecimal(i.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String receiptNo = "BCPRC" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 14).toUpperCase();

        mapper.insertRcReceipt(tenantId, receiptNo,
                String.join(",", validRids.stream().map(String::valueOf).toList()),
                customerId, now.toLocalDate(), DEFAULT_PAY_METHOD, total, now);
        Long receiptId = mapper.selectRcReceiptId(tenantId, receiptNo);
        if (receiptId == null) {
            throw new BizException(500, "收款单已插入但回查不到 id，单号=" + receiptNo);
        }
        for (int i = 0; i < valid.size(); i++) {
            Map<String, Object> item = valid.get(i);
            String billNo = str(item.get("sourceBillNo"));
            BigDecimal amount = asDecimal(item.get("amount"));
            mapper.insertRcReceiptLine(tenantId, receiptId, validRids.get(i), billNo, amount, now);
            applyReceivableBalance(tenantId, billNo, amount, now);
        }
        log.info("[LinkU开放接口] AR 收款核销已写入 rc_receipt/rc_receipt_line 单号={} 笔数={} 金额={}",
                receiptNo, valid.size(), total);
    }

    private void writeArOffset(Long tenantId, Long companyId, List<Map<String, Object>> items,
                               LocalDateTime now) {
        for (Map<String, Object> item : items) {
            BigDecimal amount = asDecimal(item.get("amount"));
            String billNo = str(item.get("sourceBillNo"));
            if (amount == null || billNo == null) {
                continue;
            }
            applyReceivableBalance(tenantId, billNo, amount, now);
            mapper.insertGlInterOffset(tenantId, buildOffsetNo(), LocalDate.now().format(PERIOD_YYYYMM),
                    companyId, "RECEIVABLE", amount, now);
        }
        log.info("[LinkU开放接口] AR/AP 对冲已回写（应收余额调整 + gl_inter_offset 留痕）笔数={}", items.size());
    }

    /**
     * 应收余额回写 + 余额守卫。0 行受影响时区分「应收单不存在」（数据缺陷，重试无用）
     * 与「余额不足／超额」（疑似重复或并发，须人工冲正），两者都抛错上报。
     */
    private void applyReceivableBalance(Long tenantId, String billNo, BigDecimal amount, LocalDateTime now) {
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[LinkU开放接口] 应收余额回写收到非正金额，已跳过 billNo={} amount={}", billNo, amt);
            return;
        }
        int affected = mapper.applyReceivableBalance(tenantId, billNo, amt, now);
        if (affected == 1) {
            return;
        }
        BigDecimal remain = mapper.selectRemainAmount(tenantId, billNo);
        if (remain == null) {
            throw new BizException(400, "应收单不存在或已删除，余额未回写 billNo=" + billNo + " amount=" + amt);
        }
        throw new BizException(409, "应收余额不足，疑似重复/并发超额核销，已拒绝本次写回 billNo=" + billNo
                + " 请求金额=" + amt + " 当前未清=" + remain);
    }

    private void writeApWriteoff(Long tenantId, Long companyId, String partnerCode,
                                 List<Map<String, Object>> items, boolean isOffset, LocalDateTime now) {
        if (!apServiceRequestAvailable()) {
            // OTC 代结构性能力缺口：无单据级付款状态字段。不按失败上报，避免为每笔真实付款
            // 刷一条永远无法补偿的审计；权威记录仍是 BCP 侧 bcp_writeoff。
            log.warn("[LinkU开放接口] OTC 代 AP 无单据级写回目标（付款执行在 pay_plan → op_pay_batch），"
                    + "已跳过 FSSC 写回；BCP 侧 bcp_writeoff 已完整落库。company={} partner={} 笔数={}",
                    companyId, partnerCode, items.size());
            return;
        }
        for (Map<String, Object> item : items) {
            BigDecimal amount = asDecimal(item.get("amount"));
            String billNo = str(item.get("sourceBillNo"));
            if (amount == null || billNo == null) {
                continue;
            }
            String status = isOffset ? "COMPLETED" : "PAID";
            int affected = mapper.updateApServiceRequestStatus(tenantId, companyId, billNo, status, now);
            if (affected == 0) {
                // 0 行有两种成因：单据不存在（真失败）与已是目标值（幂等重放）。
                // 只能靠存在性查询区分，不能用影响行数（MySQL affectedRows 语义）。
                if (mapper.countApServiceRequest(tenantId, companyId, billNo) == 0) {
                    throw new BizException(400, "应付单不存在，核销状态未回写 billNo=" + billNo
                            + " company=" + companyId);
                }
            }
        }
    }

    // ==================== 发票（验真状态） ====================

    /**
     * 发票写入：现役库（OTC 代）无 {@code ar_pre_invoice}，此时不落业务表，
     * 仅由守卫审计留痕（避免为不存在的表写死数据）。表存在（蓝图代）时回写验真状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncInvoice(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        String billNo = str(body.get("invoiceNo"));
        if (billNo == null) {
            billNo = str(body.get("sourceBillNo"));
        }
        String status = str(body.get("verifyStatus"));
        if (!apPreInvoiceAvailable()) {
            log.info("[LinkU开放接口] ar_pre_invoice 不存在（OTC 代），发票 {} 仅审计留痕，未落业务表", billNo);
            return Map.of("persisted", false, "invoiceNo", billNo == null ? "" : billNo);
        }
        int affected = mapper.updateArPreInvoiceStatus(tenantId, billNo, status);
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("persisted", true);
        out.put("affected", affected);
        return out;
    }

    // ==================== 付款/收款状态 ====================

    /**
     * 付款/收款状态回写。
     * <ul>
     *   <li>AR（OTC）：{@code rc_receipt} 状态；AP（OTC）：无对应字段，WARN 跳过（结构性缺口）；</li>
     *   <li>AR（蓝图）：{@code ar_pre_invoice}；AP（蓝图）：{@code ap_service_request}。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncPaymentStatus(Map<String, Object> body) {
        Long tenantId = tenantId(body);
        String direction = upper(str(body.get("direction")));
        String billNo = str(body.get("sourceBillNo"));
        String status = str(body.get("status"));
        LocalDateTime now = LocalDateTime.now();
        if ("AR".equals(direction)) {
            if (apPreInvoiceAvailable()) {
                mapper.updateArPreInvoiceStatus(tenantId, billNo, status);
            } else {
                mapper.updateRcReceiptStatus(tenantId, billNo, status, now);
            }
        } else if (apServiceRequestAvailable()) {
            mapper.updateApServiceRequestStatus(tenantId, asLong(body.get("companyId")), billNo, status, now);
        } else {
            log.warn("[LinkU开放接口] OTC 代 AP 付款状态无对应写入目标（pay_plan/op_pay_batch 取值域不同），"
                    + "已跳过 billNo={}", billNo);
        }
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("direction", direction);
        out.put("sourceBillNo", billNo);
        out.put("status", status);
        return out;
    }

    // ==================== 代际判定 ====================

    private final Map<String, Boolean> tableCache = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean apServiceRequestAvailable() {
        return tableAvailable("ap_service_request");
    }

    private boolean apPreInvoiceAvailable() {
        return tableAvailable("ar_pre_invoice");
    }

    private boolean tableAvailable(String table) {
        Boolean cached = tableCache.get(table);
        if (cached != null) {
            return cached;
        }
        boolean exists;
        try {
            exists = mapper.tableExists(table) > 0;
        } catch (RuntimeException e) {
            log.warn("[LinkU开放接口] 表存在性探测失败 table={} err={}", table, e.getMessage());
            exists = true;
        }
        tableCache.put(table, exists);
        return exists;
    }

    // ==================== 工具 ====================

    private Long tenantId(Map<String, Object> body) {
        Long t = asLong(body.get("tenantId"));
        return t != null ? t : props.defaultTenantId();
    }

    private String resolvePeriod(Map<String, Object> body) {
        String p = str(body.get("period"));
        if (p != null && !p.isBlank()) {
            return p;
        }
        Map<String, Object> summary = asMap(body.get("summary"));
        Object s = summary == null ? null : summary.get("period");
        return s == null ? null : String.valueOf(s);
    }

    private String toFsccPeriod(String period) {
        if (period == null || period.isBlank()) {
            return LocalDate.now().format(PERIOD_YYYYMM);
        }
        return period.replace("-", "");
    }

    private String toPeriodDashed(String period) {
        if (period == null || period.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        String compact = period.replace("-", "").trim();
        if (compact.length() >= 6) {
            return compact.substring(0, 4) + "-" + compact.substring(4, 6);
        }
        return period;
    }

    private String buildReconNo(Long reconId) {
        return "BCP" + reconId + "-" + LocalDate.now().format(PERIOD_YYYYMM);
    }

    private String buildOffsetNo() {
        return "BCPOF" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private String diffRemark(Map<String, Object> body, Map<String, Object> summary) {
        String reason = str(body.get("diffReason"));
        List<String> diff = strList(body.get("diffBillNos"));
        boolean selected = "SELECTED".equals(str(body.get("generateMode")));
        if (diff.isEmpty() && !selected) {
            return null;
        }
        if (selected) {
            String tag = "[按单勾选 " + buildReconNo(asLong(body.get("linkuReconId"))) + "]";
            return trim500(reason == null || reason.isBlank() ? tag : tag + " " + reason);
        }
        return trim500(reason);
    }

    /** BCP 差异类型 → {@code supplier_recon_diff.diff_type} 取值域。 */
    private String diffType(Map<String, Object> summary) {
        if (summary == null || summary.get("diffType") == null) {
            return "AMOUNT_DIFF";
        }
        return switch (String.valueOf(summary.get("diffType")).toUpperCase()) {
            case "MISSING", "MISSING_INVOICE", "UNMATCHED" -> "MISSING_INVOICE";
            case "MISSING_PAYMENT" -> "MISSING_PAYMENT";
            case "TIME_DIFF", "TIMING_DIFF" -> "TIMING_DIFF";
            case "AMOUNT_DIFF", "PARTIAL" -> "AMOUNT_DIFF";
            default -> "OTHER";
        };
    }

    private BigDecimal summaryAmount(Map<String, Object> summary, String... keys) {
        if (summary == null) {
            return BigDecimal.ZERO;
        }
        for (String key : keys) {
            Object v = summary.get(key);
            if (v == null) {
                continue;
            }
            try {
                return new BigDecimal(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                // 尝试下一个候选键
            }
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object e : list) {
            if (e instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<String> strList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    private static String trim500(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
