// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 初始借款余额（导入上线前员工借款余额）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_loan_initial")
public class MdLoanInitial extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 员工用户ID */
    private Long userId;

    /** 员工姓名 */
    private String userName;

    /** 初始借款余额 */
    private BigDecimal balance;

    /** 导入日期 */
    private LocalDate importDate;

    /** 备注 */
    private String remark;

}
