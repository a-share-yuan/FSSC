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
 * 合同付款条件（cont_pay_condition，需求 5.2：合同支付金额/付款节点说明/预计付款日期）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cont_pay_condition")
public class ContPayCondition extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    private Long contractId;
    /** 付款节点说明 */
    private String payNode;
    /** 支付金额 */
    private BigDecimal payAmount;
    /** 预计付款日期 */
    private LocalDate planDate;
    private String remark;
}
