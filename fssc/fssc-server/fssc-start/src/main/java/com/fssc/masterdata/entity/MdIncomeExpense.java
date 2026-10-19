// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 收支设置（资金计划）（需求 2.21）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_income_expense")
public class MdIncomeExpense extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 收支项目编码 */
    private String ieCode;
    /** 收支项目名称 */
    private String ieName;
    /** 类型 IN收入/OUT支出 */
    private String ieType;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
