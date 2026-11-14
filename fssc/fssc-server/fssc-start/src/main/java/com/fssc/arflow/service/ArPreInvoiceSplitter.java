package com.fssc.arflow.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预制发票拆分合并纯逻辑（蓝图附件一11.2）：单张限额（默认100万/代销1000万，客户配置覆盖）
 * + 最大明细行数（销货清单受限客户8行）贪心装填；预制发票号=对账单号+三位流水。
 */
public final class ArPreInvoiceSplitter {

    private ArPreInvoiceSplitter() {
    }

    public record Group(List<Integer> lineIndexes, BigDecimal amount, BigDecimal taxAmount,
                        BigDecimal totalAmount) {
    }

    public static List<Group> split(List<Map<String, Object>> lines, BigDecimal limitAmount, Integer maxLines) {
        List<Group> groups = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return groups;
        }
        List<Integer> cur = new ArrayList<>();
        BigDecimal curAmount = BigDecimal.ZERO;
        BigDecimal curTax = BigDecimal.ZERO;
        BigDecimal curTotal = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> line = lines.get(i);
            BigDecimal amount = num(line.get("amount"));
            BigDecimal tax = num(line.get("taxAmount"));
            BigDecimal total = num(line.get("totalAmount"));
            boolean overflow = false;
            if (maxLines != null && cur.size() >= maxLines) {
                overflow = true;
            }
            if (limitAmount != null && curTotal.add(total).compareTo(limitAmount) > 0 && !cur.isEmpty()) {
                overflow = true;
            }
            if (overflow) {
                groups.add(new Group(cur, curAmount, curTax, curTotal));
                cur = new ArrayList<>();
                curAmount = BigDecimal.ZERO;
                curTax = BigDecimal.ZERO;
                curTotal = BigDecimal.ZERO;
            }
            cur.add(i);
            curAmount = curAmount.add(amount);
            curTax = curTax.add(tax);
            curTotal = curTotal.add(total);
        }
        if (!cur.isEmpty()) {
            groups.add(new Group(cur, curAmount, curTax, curTotal));
        }
        return groups;
    }

    /** 预制发票号 = 对账单号 + 三位流水号（蓝图）。 */
    public static String preInvoiceNo(String reconBillNo, int seq) {
        return reconBillNo + String.format("%03d", seq);
    }

    private static BigDecimal num(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(v));
    }
}
