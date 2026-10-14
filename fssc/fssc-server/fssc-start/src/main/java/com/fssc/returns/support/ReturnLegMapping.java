// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.support;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 退货下游腿分流映射表 —— 采购退货（R5-D-084）与销售退货（R5-D-089）共用同一套口径。
 *
 * <p>与实体 javadoc、界面下拉文案同源的口径：</p>
 * <ul>
 *   <li>{@code REFUND} 退货退款：冲减往来款并进对账——采购侧向 {@code supplier_recon_detail}
 *       落 {@code PUR_RETURN} 负向行（amount 取负）并重算对账单余额；销售侧生成红字应收（贷项）
 *       冲减对应 {@code rc_receivable.remain_amount}，并把该笔退货写入客户对账基础表
 *       {@code ar_statement}（check_status 待核对）；</li>
 *   <li>{@code EXCHANGE} 退货补货：不冲减应付/应收、不进对账，仅单据登记。</li>
 * </ul>
 *
 * <p>两域服务只准消费本表，禁止各自另写一份模式判断（R5-D-089 修法与 R5-D-084 落在同一套
 * 映射表上，避免两域口径漂移、各修一次）。</p>
 */
public final class ReturnLegMapping {

    /** 退货退款：进对账（冲减应付/应收 + 落对账行）。 */
    public static final String MODE_REFUND = "REFUND";
    /** 退货补货：不冲减、不进对账。 */
    public static final String MODE_EXCHANGE = "EXCHANGE";

    /** supplier_recon_detail.item_type：采购退货负向行（REFUND 专用）。 */
    public static final String PUR_RETURN_ITEM_TYPE = "PUR_RETURN";

    private ReturnLegMapping() {
    }

    /**
     * 分流条件（唯一判据）：return_mode 归一后为 REFUND（退货退款）⇒ 进对账；
     * EXCHANGE 退货补货以及空值/未知值一律不进（fail-safe 不冲减）。
     */
    public static boolean entersRecon(String returnMode) {
        if (returnMode == null || returnMode.isBlank()) {
            return false;
        }
        return MODE_REFUND.equals(returnMode.trim().toUpperCase(Locale.ROOT));
    }

    /** 下游取负：退货单金额表内存正数（恒正），对账/应收口径按负数落。 */
    public static BigDecimal negate(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.negate();
    }
}
