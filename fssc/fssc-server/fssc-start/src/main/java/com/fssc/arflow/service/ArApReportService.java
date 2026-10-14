// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.apflow.entity.ApMonthClose;
import com.fssc.apflow.mapper.ApMonthCloseMapper;
import com.fssc.arflow.entity.ArMonthClose;
import com.fssc.arflow.entity.ArNote;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.entity.ArWriteoffRecord;
import com.fssc.arflow.mapper.ArMonthCloseMapper;
import com.fssc.arflow.mapper.ArNoteMapper;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.arflow.mapper.ArWriteoffRecordMapper;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.mapper.RcBadDebtMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AR/AP 统计报表（蓝图报表统计 9.1 序号21：开票/核销/票据/坏账/月结 5 张）。
 * 蓝图未定义列口径，按各域单据字段聚合：期间×状态分组给出单据数与金额合计。
 */
@Service
@RequiredArgsConstructor
public class ArApReportService {

    private final ArPreInvoiceMapper preInvoiceMapper;
    private final ArWriteoffRecordMapper writeoffMapper;
    private final ArNoteMapper noteMapper;
    private final RcBadDebtMapper badDebtMapper;
    private final ArMonthCloseMapper arMonthCloseMapper;
    private final ApMonthCloseMapper apMonthCloseMapper;

    /** 开票统计：按期间×状态聚合预制发票（张数/金额/税额/价税合计）。 */
    public List<Map<String, Object>> invoicing(String customerName) {
        LambdaQueryWrapper<ArPreInvoice> q = new LambdaQueryWrapper<>();
        if (customerName != null && !customerName.isBlank()) {
            q.like(ArPreInvoice::getCustomerName, customerName);
        }
        List<ArPreInvoice> rows = preInvoiceMapper.selectList(q);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (ArPreInvoice r : rows) {
            String period = periodOf(r.getInvoiceDate(), r.getCreateTime());
            String key = period + "|" + nullToDefault(r.getStatus());
            Map<String, Object> g = grouped.computeIfAbsent(key, k -> baseRow(period, "status", nullToDefault(r.getStatus())));
            g.put("count", asInt(g.get("count")) + 1);
            g.put("amount", asDec(g.get("amount")).add(nvl(r.getAmount())));
            g.put("taxAmount", asDec(g.get("taxAmount")).add(nvl(r.getTaxAmount())));
            g.put("totalAmount", asDec(g.get("totalAmount")).add(nvl(r.getTotalAmount())));
        }
        return new ArrayList<>(grouped.values());
    }

    /** 核销统计：按期间×类型聚合核销记录（笔数/核销金额），含自动核销占比。 */
    public List<Map<String, Object>> writeoff(String customerName) {
        LambdaQueryWrapper<ArWriteoffRecord> q = new LambdaQueryWrapper<>();
        if (customerName != null && !customerName.isBlank()) {
            q.like(ArWriteoffRecord::getCustomerName, customerName);
        }
        List<ArWriteoffRecord> rows = writeoffMapper.selectList(q);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (ArWriteoffRecord r : rows) {
            String period = periodOf(null, r.getCreateTime());
            String key = period + "|" + nullToDefault(r.getWriteoffType());
            Map<String, Object> g = grouped.computeIfAbsent(key, k -> baseRow(period, "writeoffType", nullToDefault(r.getWriteoffType())));
            g.put("count", asInt(g.get("count")) + 1);
            g.put("writeoffAmount", asDec(g.get("writeoffAmount")).add(nvl(r.getWriteoffAmount())));
            if (r.getAutoFlag() != null && r.getAutoFlag() == 1) {
                g.put("autoCount", asInt(g.get("autoCount")) + 1);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /** 票据统计：按类型×状态聚合票据（张数/票面额/贴现利息/贴现净额）。 */
    public List<Map<String, Object>> note() {
        List<ArNote> rows = noteMapper.selectList(new LambdaQueryWrapper<ArNote>().orderByAsc(ArNote::getDueDate));
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (ArNote r : rows) {
            String key = nullToDefault(r.getNoteType()) + "|" + nullToDefault(r.getStatus());
            Map<String, Object> g = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("noteType", nullToDefault(r.getNoteType()));
                m.put("status", nullToDefault(r.getStatus()));
                m.put("count", 0);
                m.put("amount", BigDecimal.ZERO);
                m.put("discountInterest", BigDecimal.ZERO);
                m.put("discountAmount", BigDecimal.ZERO);
                return m;
            });
            g.put("count", asInt(g.get("count")) + 1);
            g.put("amount", asDec(g.get("amount")).add(nvl(r.getAmount())));
            g.put("discountInterest", asDec(g.get("discountInterest")).add(nvl(r.getDiscountInterest())));
            g.put("discountAmount", asDec(g.get("discountAmount")).add(nvl(r.getDiscountAmount())));
        }
        return new ArrayList<>(grouped.values());
    }

    /** 坏账统计：按期间×状态聚合（计提额/核销额/计提笔数）。 */
    public List<Map<String, Object>> badDebt(String period) {
        LambdaQueryWrapper<RcBadDebt> q = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) {
            q.eq(RcBadDebt::getPeriod, period);
        }
        List<RcBadDebt> rows = badDebtMapper.selectList(q);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (RcBadDebt r : rows) {
            String p = r.getPeriod() == null || r.getPeriod().isBlank() ? "未分期" : r.getPeriod();
            String key = p + "|" + nullToDefault(r.getStatus());
            Map<String, Object> g = grouped.computeIfAbsent(key, k -> baseRow(p, "status", nullToDefault(r.getStatus())));
            g.put("count", asInt(g.get("count")) + 1);
            g.put("provisionAmount", asDec(g.get("provisionAmount")).add(nvl(r.getAmount())));
            g.put("writeoffAmount", asDec(g.get("writeoffAmount")).add(nvl(r.getWriteoffAmount())));
        }
        return new ArrayList<>(grouped.values());
    }

    /** 月结统计：AR应收月结/成本月结 + AP月末关账 全清单（期间/类型/状态/差异/关账时间）。 */
    public List<Map<String, Object>> monthClose(String period) {
        List<Map<String, Object>> out = new ArrayList<>();
        LambdaQueryWrapper<ArMonthClose> arq = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) {
            arq.eq(ArMonthClose::getPeriod, period);
        }
        for (ArMonthClose c : arMonthCloseMapper.selectList(arq.orderByDesc(ArMonthClose::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("closeNo", c.getCloseNo());
            m.put("period", c.getPeriod());
            m.put("domain", "AR");
            m.put("closeType", c.getCloseType());
            m.put("status", c.getStatus());
            m.put("amount", c.getPulledAmount());
            m.put("diffAmount", c.getDiffAmount());
            m.put("diffFlag", c.getDiffFlag());
            m.put("closedTime", c.getClosedTime());
            out.add(m);
        }
        LambdaQueryWrapper<ApMonthClose> apq = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) {
            apq.eq(ApMonthClose::getPeriod, period);
        }
        for (ApMonthClose c : apMonthCloseMapper.selectList(apq.orderByDesc(ApMonthClose::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("closeNo", c.getCloseNo());
            m.put("period", c.getPeriod());
            m.put("domain", "AP");
            m.put("closeType", "AP_CLOSE");
            m.put("status", c.getStatus());
            m.put("amount", null);
            m.put("diffAmount", null);
            m.put("diffFlag", 0);
            m.put("closedTime", c.getClosedTime());
            out.add(m);
        }
        return out;
    }

    // ---------------- 工具 ----------------

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private Map<String, Object> baseRow(String period, String statusKey, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", period);
        m.put(statusKey, status);
        m.put("count", 0);
        m.put("amount", BigDecimal.ZERO);
        if (statusKey.equals("status")) {
            m.put("taxAmount", BigDecimal.ZERO);
            m.put("totalAmount", BigDecimal.ZERO);
            m.put("provisionAmount", BigDecimal.ZERO);
            m.put("writeoffAmount", BigDecimal.ZERO);
        }
        if (statusKey.equals("writeoffType")) {
            m.put("writeoffAmount", BigDecimal.ZERO);
            m.put("autoCount", 0);
        }
        return m;
    }

    private String periodOf(LocalDate date, LocalDateTime time) {
        if (date != null) {
            return MONTH_FMT.format(date);
        }
        if (time != null) {
            return MONTH_FMT.format(time);
        }
        return "未知期间";
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nullToDefault(String v) {
        return v == null || v.isBlank() ? "UNKNOWN" : v;
    }

    private static int asInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static BigDecimal asDec(Object v) {
        return v instanceof BigDecimal ? (BigDecimal) v : BigDecimal.ZERO;
    }
}
