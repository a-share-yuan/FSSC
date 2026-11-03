// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同支付记录（cont_pay_record，实际付款金额/结算数量/单价/单据号/状态/支付日期）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cont_pay_record")
public class ContPayRecord extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    private Long contractId;
    /** 实际付款金额 */
    private BigDecimal payAmount;
    /** 结算数量 */
    private BigDecimal settleQty;
    /** 单价 */
    private BigDecimal unitPrice;
    /** 单据号（对公付款单） */
    private String billNo;
    /** 单据类型 */
    private String billType;
    /** 状态（PAID/IN_PROGRESS） */
    private String status;
    /** 支付日期 */
    private LocalDate payDate;
    private String remark;
}
