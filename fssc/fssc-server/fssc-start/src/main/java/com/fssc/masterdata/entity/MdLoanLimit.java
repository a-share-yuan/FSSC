// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 借款额度（需求 2.28：定义员工借款额度上限）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_loan_limit")
public class MdLoanLimit extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 员工用户ID */
    private Long userId;

    /** 员工姓名 */
    private String userName;

    /** 借款类型 LOAN借款/PETTY备用金 */
    private String loanType;

    /** 额度上限 */
    private BigDecimal limitAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
