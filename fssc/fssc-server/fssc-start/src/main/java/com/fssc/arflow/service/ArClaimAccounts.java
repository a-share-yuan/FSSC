// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AR科目与组织映射纯逻辑（蓝图附件四/附件五 落地）。
 */
public final class ArClaimAccounts {

    private ArClaimAccounts() {
    }

    /** 客户索赔总部科目（附件五11.6.1）：国内16%→60010101；国内税率空→660121；国外→60010102。 */
    public static String headquartersClaimAccount(boolean overseas, BigDecimal taxRate) {
        if (overseas) {
            return "60010102";
        }
        if (taxRate == null || taxRate.signum() == 0) {
            return "660121";
        }
        return "60010101";
    }

    /** 事业部索赔科目（附件五：默认600101）。 */
    public static final String BU_CLAIM_ACCOUNT = "600101";

    /** 其它业务收入固定科目（附件五11.6.3）。 */
    public static final String OTHER_REV_CREDIT = "A0.00.650101.00.00.往来.0";
    public static final String OTHER_REV_TAX = "A0.00.222127.00.00.00.0";
    public static final String OTHER_REV_RENT_DEBIT = "220300";

    /** 汇票贴息科目（附件五11.6.2）。 */
    public static final String NOTE_DISCOUNT_AR = "112200";
    public static final String NOTE_DISCOUNT_INCOME = "660302";
    public static final String NOTE_DISCOUNT_TAX = "22210103";

    /**
     * 事业部事务处理金额（附件四11.5.1）：厂别00不在事业部做事务处理及凭证（返回null）；
     * 外币跨法人跨账套折合本位币。
     */
    public static BigDecimal buAmount(String factoryCode, BigDecimal amount, String ccy, BigDecimal fxRate) {
        if (amount == null) {
            return null;
        }
        if ("00".equals(factoryCode)) {
            return null;
        }
        boolean foreign = ccy != null && !"CNY".equalsIgnoreCase(ccy);
        if (foreign) {
            BigDecimal rate = fxRate == null ? BigDecimal.ONE : fxRate;
            return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
        return amount;
    }

    /** 贴现利息 = 票面金额 × 年化利率% × 天数 / 360。 */
    public static BigDecimal discountInterest(BigDecimal faceAmount, BigDecimal annualRatePercent, Integer days) {
        if (faceAmount == null || annualRatePercent == null || days == null || days <= 0) {
            return BigDecimal.ZERO;
        }
        return faceAmount.multiply(annualRatePercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(100 * 360L), 2, RoundingMode.HALF_UP);
    }
}
