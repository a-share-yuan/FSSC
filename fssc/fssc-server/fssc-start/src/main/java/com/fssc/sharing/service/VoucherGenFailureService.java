// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.PageResult;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.entity.SysOperLog;
import com.fssc.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A12「凭证自动生成执行结果视图」（RA-ROOT-09，2026-09-27 第三轮裁定）。
 *
 * <p>立项目的：审批通过后的自动制证失败此前只能靠 SSH grep 文件日志发现（P3 已改为写
 * {@code sys_oper_log}，见 {@code ExpenseWfEventListener#handleVoucherGenFailure}），
 * 财务侧在界面上看不见、点数也说不清。本视图把「失败事件」变成一等公民：
 * 状态筛选（默认不限）／异常行明细／补生成入口／按来源事件计数。
 *
 * <p><b>只读</b>：不写任何业务表，不新增列，不改历史留痕。补生成动作仍由既有
 * {@code POST /api/tenant/sharing/voucher/generate/{billId}} 完成（幂等在 VoucherService 内）。
 *
 * <p><b>数据源边界（v1）</b>：事件载体是 {@code sys_oper_log} 中 title=
 * {@link #FAILURE_LOG_TITLE} 的行，而该 title 目前只有一个生产者——费用域审批收尾
 * {@code ExpenseWfEventListener}。因此本视图覆盖 <b>费用族</b>（EXPENSE / EXPENSE_PAY /
 * EXP_LOAN / EXP_REPAY）自动制证失败；采购、合同、总账等域的制证失败仅记文件日志，
 * 无事件行可查（已登记为遗留项，不在本视图伪造）。
 *
 * 收件人」自动跑；本 v1 只做「结果视图 + 人工重推」，事件预置与自动通知留下一批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherGenFailureService {

    /** 失败事件标题（唯一真值＝ExpenseWfEventListener#writeVoucherFailOperLog 写入的 title） */
    public static final String FAILURE_LOG_TITLE = "单据自动生成凭证失败";

    /**
     * 单次扫描上限：视图是运营巡检页而非报表，取「最近 N 条事件」足够定位；
     * 超出部分不参与计数，扫描数与是否截断都在 {@link #stats()} 中如实返回。
     */
    static final int SCAN_LIMIT = 2000;

    /** errorMsg 由 ExpenseWfEventListener:476 拼装：[码] sourceType= billNo= billId= companyId= | 原因 */
    private static final Pattern EVENT_MSG = Pattern.compile(
            "sourceType=(\\S+)\\s+billNo=(\\S+)\\s+billId=(\\d+)(?:\\s+companyId=(\\S+?))?\\s*\\|\\s*(.*)",
            Pattern.DOTALL);

    /** 与 VoucherService 各 generate* 入口的 P0-4 状态闸同口径 */
    private static final Set<String> BILL_STATUS_OK_FOR_VOUCHER = Set.of("APPROVED", "PAID", "CLOSED");

    private static final Map<String, String> SOURCE_TYPE_NAMES = Map.of(
            "EXPENSE", "费用报销",
            "EXPENSE_PAY", "对公付款",
            // D-DEC-083：对公结算（PAY/SETTLE）审批通过出的是计提应付凭证，来源与付款凭证分家
            com.fssc.sharing.service.VoucherService.SOURCE_SETTLE, "对公结算计提应付",
            "EXP_LOAN", "借款",
            "EXP_REPAY", "还款");

    /** 单据类型中文名（补生成入口按 billType 派发，需要给财务看清是哪种单） */
    private static final Map<String, String> BILL_TYPE_NAMES = Map.of(
            "CLAIM", "费用报销单",
            "PAY", "对公付款单",
            "APPLY", "差旅事前申请单",
            "LOAN", "借款单",
            "REPAY", "还款单");

    private final SysOperLogMapper sysOperLogMapper;
    private final OpVoucherMapper opVoucherMapper;
    private final ExpBillMapper expBillMapper;

    /**
     * 失败事件分页视图。
     *
     * @param status     PENDING（未补生成）/ RESOLVED（已补生成）；<b>传空＝不限</b>（默认口径）
     * @param sourceType EXPENSE / EXPENSE_PAY / EXP_LOAN / EXP_REPAY；传空＝不限
     * @param billNo     单据号模糊匹配；传空＝不限
     */
    public PageResult<Map<String, Object>> page(long pageNum, long pageSize,
                                                String status, String sourceType, String billNo) {
        List<Map<String, Object>> all = scan();
        List<Map<String, Object>> filtered = filter(all, status, sourceType, billNo);
        long from = Math.max(0L, (pageNum - 1) * pageSize);
        long toExcl = Math.min(filtered.size(), from + pageSize);
        List<Map<String, Object>> slice = from >= filtered.size()
                ? List.of() : filtered.subList((int) from, (int) toExcl);
        return PageResult.of(new ArrayList<>(slice), filtered.size(), pageNum, pageSize);
    }

    /**
     * 按来源事件计数（「按事件计数」一格）。计数基于同一份扫描结果，与分页筛选互不影响。
     * 另返回 scannedRows / truncated，避免把「最近 2000 条内」说成全量真值。
     */
    public Map<String, Object> stats(String sourceType) {
        List<Map<String, Object>> all = scan();
        Map<String, long[]> bySource = new LinkedHashMap<>();
        long pendingTotal = 0;
        for (Map<String, Object> row : all) {
            String st = String.valueOf(row.get("sourceType"));
            if (sourceType != null && !sourceType.isBlank() && !sourceType.trim().equals(st)) {
                continue;
            }
            long[] c = bySource.computeIfAbsent(st, k -> new long[2]);
            if ("PENDING".equals(row.get("result"))) {
                c[0]++;
                pendingTotal++;
            }
            c[1]++;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, long[]> e : bySource.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", e.getKey());
            item.put("sourceTypeName", SOURCE_TYPE_NAMES.getOrDefault(e.getKey(), e.getKey()));
            item.put("pending", e.getValue()[0]);
            item.put("events", e.getValue()[1]);
            items.add(item);
        }
        long allEvents = all.size();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("allEvents", sourceType == null || sourceType.isBlank() ? allEvents : items.stream().mapToLong(i -> (Long) i.get("events")).sum());
        r.put("allPending", sourceType == null || sourceType.isBlank() ? pendingTotal : items.stream().mapToLong(i -> (Long) i.get("pending")).sum());
        r.put("bySource", items);
        r.put("scannedRows", allEvents);
        r.put("scanLimit", SCAN_LIMIT);
        r.put("truncated", allEvents >= SCAN_LIMIT);
        r.put("scope", "费用族自动制证失败（事件载体 sys_oper_log，其它域制证失败无事件行）");
        return r;
    }

    // ------------------------------------------------------------------
    // 组装
    // ------------------------------------------------------------------

    private List<Map<String, Object>> scan() {
        List<SysOperLog> logs = sysOperLogMapper.selectByTitleRecent(FAILURE_LOG_TITLE, SCAN_LIMIT);
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }
        List<Parsed> parsed = new ArrayList<>(logs.size());
        Set<Long> billIds = new HashSet<>();
        for (SysOperLog l : logs) {
            Parsed p = parse(l);
            parsed.add(p);
            if (p.billId != null) {
                billIds.add(p.billId);
            }
        }
        Map<Long, ExpBill> bills = loadBills(billIds);
        Map<String, OpVoucher> vouchers = loadVouchers(billIds);

        List<Map<String, Object>> rows = new ArrayList<>(parsed.size());
        for (Parsed p : parsed) {
            rows.add(toRow(p, p.billId == null ? null : bills.get(p.billId),
                    p.billId == null ? null : vouchers.get(key(p.sourceType, p.billId))));
        }
        return rows;
    }

    /** 一次批量取单据，避免逐行 selectById（同 VoucherService#fillSourceBill 口径） */
    private Map<Long, ExpBill> loadBills(Set<Long> billIds) {
        Map<Long, ExpBill> byId = new HashMap<>();
        if (billIds.isEmpty()) {
            return byId;
        }
        for (ExpBill b : expBillMapper.selectBatchIds(billIds)) {
            byId.put(b.getId(), b);
        }
        return byId;
    }

    /** 幂等键与 VoucherService#existingVoucherId 完全一致：source_type + source_doc_id，VOID 除外 */
    private Map<String, OpVoucher> loadVouchers(Set<Long> billIds) {
        Map<String, OpVoucher> byKey = new HashMap<>();
        if (billIds.isEmpty()) {
            return byKey;
        }
        List<OpVoucher> found = opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .in(OpVoucher::getSourceDocId, billIds)
                .ne(OpVoucher::getStatus, "VOID")
                .orderByAsc(OpVoucher::getId));
        for (OpVoucher v : found) {
            byKey.putIfAbsent(key(v.getSourceType(), v.getSourceDocId()), v);
        }
        return byKey;
    }

    private static String key(String sourceType, Long billId) {
        return sourceType + "#" + billId;
    }

    private Parsed parse(SysOperLog l) {
        Parsed p = new Parsed();
        p.log = l;
        String msg = l.getErrorMsg();
        if (msg != null) {
            Matcher m = EVENT_MSG.matcher(msg);
            if (m.find()) {
                p.sourceType = m.group(1);
                p.billNo = m.group(2);
                try {
                    p.billId = Long.valueOf(m.group(3));
                } catch (NumberFormatException ignore) {
                    // 事件行照实列出，归为「无法定位单据」
                }
                p.companyId = parseLongOrNull(m.group(4));
                p.reason = m.group(5);
                p.malformed = false;
                return p;
            }
        }
        // 解析不出四元组：不猜、不丢，整条 errorMsg 当原因展示（列宽已截断，原样可见即可）
        p.sourceType = "";
        p.billNo = "";
        p.companyId = null;
        p.reason = msg == null ? "事件行缺少可解析的 sourceType/billNo/billId" : msg;
        p.malformed = true;
        return p;
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank() || "null".equals(s)) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> toRow(Parsed p, ExpBill bill, OpVoucher voucher) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("logId", p.log.getId());
        row.put("operTime", p.log.getOperTime());
        row.put("method", p.log.getMethod());
        row.put("sourceType", p.sourceType);
        row.put("sourceTypeName", SOURCE_TYPE_NAMES.getOrDefault(p.sourceType, p.sourceType));
        row.put("billId", p.billId);
        row.put("billNo", bill != null && bill.getBillNo() != null ? bill.getBillNo() : p.billNo);
        row.put("billType", bill == null ? null : bill.getBillType());
        row.put("billTypeName", bill == null ? null : BILL_TYPE_NAMES.getOrDefault(bill.getBillType(), bill.getBillType()));
        row.put("billStatus", bill == null ? null : bill.getStatus());
        row.put("companyId", p.companyId != null ? p.companyId : (bill == null ? null : bill.getCompanyId()));
        row.put("periodYear", bill == null ? null : bill.getPeriodYear());
        row.put("periodMonth", bill == null ? null : bill.getPeriodMonth());
        row.put("reason", p.reason);
        row.put("voucherId", voucher == null ? null : voucher.getId());
        row.put("voucherNo", voucher == null ? null : voucher.getVoucherNo());

        boolean resolved = voucher != null;
        row.put("result", resolved ? "RESOLVED" : "PENDING");
        row.put("resultName", resolved ? "已补生成" : "未补生成");
        if (!resolved && bill != null && bill.getVoucherNo() != null && !bill.getVoucherNo().isBlank()) {
            row.put("inconsistency", "单据已回填凭证号 " + bill.getVoucherNo()
                    + "，但按来源 " + p.sourceType + " 未查到有效凭证（幂等键不一致，需人工核对）");
        }
        if (p.malformed) {
            row.put("retriable", false);
            row.put("retryHint", "事件行无法解析出 billId，不能定位待补单据");
        } else {
            applyRetryGate(row, p, bill);
        }
        return row;
    }

    /**
     * 补生成可行性：只承认与既有入口派发结果<b>一致</b>的来源。
     *
     * <p>{@code POST /voucher/generate/{billId}} 按 billType 派发：PAY → generatePtpPayment
     * （来源 EXPENSE_PAY），其余一律 generate（来源写死 EXPENSE）。所以
     * EXP_LOAN / EXP_REPAY 的失败单走该入口会落错来源、并绕过 EXP_LOAN/EXP_REPAY 幂等键，
     * 结果是「补出一张费用凭证 + 借款凭证仍然缺」；本视图对这类行直接判定不可重推并说明原因，
     * 不把错口径的按钮递给财务（D-DEC-271）。
     */
    private void applyRetryGate(Map<String, Object> row, Parsed p, ExpBill bill) {
        if (bill == null) {
            row.put("retriable", false);
            row.put("retryHint", "单据 " + p.billNo + "（billId=" + p.billId + "）已查不到，无法补生成");
            return;
        }
        if ("RESOLVED".equals(row.get("result"))) {
            row.put("retriable", false);
            row.put("retryHint", "该来源已存在有效凭证，无需重推");
            return;
        }
        if (!BILL_STATUS_OK_FOR_VOUCHER.contains(bill.getStatus())) {
            row.put("retriable", false);
            row.put("retryHint", "补生成入口按 P0-4 拒绝非有效单据，当前状态 " + bill.getStatus());
            return;
        }
        boolean ptp = "PAY".equals(bill.getBillType());
        // D-DEC-083：PAY 单里对公结算（SETTLE）出的是计提应付凭证（来源 EXPENSE_SETTLE），
        // 与其余对公付款（来源 EXPENSE_PAY）同走 generatePtpPayment 派发，两者都可由补生成入口重推。
        boolean match = ("EXPENSE".equals(p.sourceType) && !ptp)
                || (("EXPENSE_PAY".equals(p.sourceType)
                        || VoucherService.SOURCE_SETTLE.equals(p.sourceType)) && ptp);
        if (!match) {
            row.put("retriable", false);
            row.put("retryHint", "既有补生成入口按单据类型派发，来源 " + p.sourceType
                    + "（单据类型 " + bill.getBillType() + "）会被落成另一种来源，重推将产生错凭证；"
                    + "请改走该来源的专用生成方法（待码侧接入，见 D-DEC-271）");
            return;
        }
        row.put("retriable", true);
        row.put("retryHint", "可通过「共享中心→记账凭证→生成凭证」补生成（同源已有凭证则幂等返回）");
    }

    private List<Map<String, Object>> filter(List<Map<String, Object>> rows, String status,
                                             String sourceType, String billNo) {
        List<Map<String, Object>> out = new ArrayList<>();
        String st = status == null ? null : status.trim().toUpperCase();
        String src = sourceType == null ? null : sourceType.trim();
        String no = billNo == null ? null : billNo.trim();
        for (Map<String, Object> row : rows) {
            if (st != null && !st.isEmpty() && !st.equals(row.get("result"))) {
                continue;
            }
            if (src != null && !src.isEmpty() && !src.equals(row.get("sourceType"))) {
                continue;
            }
            if (no != null && !no.isEmpty()) {
                Object rn = row.get("billNo");
                if (rn == null || !String.valueOf(rn).contains(no)) {
                    continue;
                }
            }
            out.add(row);
        }
        return out;
    }

    /** 一条 sys_oper_log 事件的解析结果 */
    private static final class Parsed {
        private SysOperLog log;
        private String sourceType;
        private String billNo;
        private Long billId;
        private Long companyId;
        private String reason;
        private boolean malformed;
    }
}
