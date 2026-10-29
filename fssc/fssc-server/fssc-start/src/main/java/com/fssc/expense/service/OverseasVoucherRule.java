// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.fssc.expense.entity.ExpBillLine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ⑤ 境外凭证合规纯规则集（国家税务总局 2016年19号公告规则）：
 * 收据/行程单中文说明必填 / 境外票据无进项抵扣 / 差旅补贴超标并入个税判定。
 *
 * <p>全部静态纯函数，不依赖 Spring 与数据库，供 ExpenseBillService（提交校验链）、
 * ExpenseDetailService（差旅行补贴核算）调用，可直接单测。仿 AbroadStandardRule 范式。
 */
public final class OverseasVoucherRule {

    /** 凭证形态：境内发票（默认，旧行为） */
    public static final String FORM_FAPIAO = "FAPIAO";
    /** 凭证形态：境外收据（税前扣除须附中文说明） */
    public static final String FORM_RECEIPT = "RECEIPT";
    /** 凭证形态：行程单（税前扣除须附中文说明） */
    public static final String FORM_ITINERARY = "ITINERARY";

    private OverseasVoucherRule() {
    }

    /** 是否境外收据类凭证形态（RECEIPT/ITINERARY）：19号公告要求载明行程/地点/费用/人名的中文说明 */
    public static boolean isReceiptForm(String voucherForm) {
        return FORM_RECEIPT.equalsIgnoreCase(voucherForm) || FORM_ITINERARY.equalsIgnoreCase(voucherForm);
    }

    /**
     * 中文说明必填判定：abroad 且 voucher_form ∈ (RECEIPT, ITINERARY) → cn_desc 必须非空。
     * 返回错误消息；满足（或 abroad=false / FAPIAO / 空形态按默认境内发票）返回 null——国内行为零变化。
     */
    public static String requireCnDesc(boolean abroad, ExpBillLine line) {
        if (!abroad || line == null || !isReceiptForm(line.getVoucherForm())) {
            return null;
        }
        String desc = line.getCnDesc();
        if (desc == null || desc.isBlank()) {
            return "境外收据须填中文说明（19号公告税前扣除要件）";
        }
        return null;
    }

    /**
     * 境外无进项抵扣判定：abroad → true（境外收据/行程单非增值税扣税凭证，
     * deduct_amount 应置 0、税率视为 0 规则）。已填 taxAmount/taxRate 且价税勾稽成立的不做重算，
     * 仅在可抵扣税额层面归零（处理在调用方，便于记录 log）。
     */
    public static boolean forceNoDeduct(boolean abroad, ExpBillLine line) {
        return abroad && line != null;
    }

    /**
     * 补贴超标并入个税：actual &gt; standardDaily × days → 超标额 = actual − 标准额
     * 2 位小数四舍五入；不超标或参数缺失返回 0（0=不超，无需并入工资薪金计税）。
     */
    public static BigDecimal allowanceTaxable(BigDecimal actual, BigDecimal standardDaily, BigDecimal days) {
        if (actual == null || standardDaily == null || days == null
                || standardDaily.compareTo(BigDecimal.ZERO) <= 0 || days.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal standard = standardDaily.multiply(days).setScale(2, RoundingMode.HALF_UP);
        BigDecimal over = actual.subtract(standard);
        return over.compareTo(BigDecimal.ZERO) > 0 ? over.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
