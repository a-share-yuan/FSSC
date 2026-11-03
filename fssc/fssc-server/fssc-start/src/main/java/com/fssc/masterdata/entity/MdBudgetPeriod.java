// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算期间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_budget_period")
public class MdBudgetPeriod extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 期间类型 ANNUAL/QUARTER/MONTHLY */
    private String periodType;
    /** 预算年度 */
    private Integer periodYear;
    /** 期间号 */
    private Integer periodNo;
    /** 期间名称 */
    private String periodName;
    /** 开始日期 */
    private String beginDate;
    /** 结束日期 */
    private String endDate;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
