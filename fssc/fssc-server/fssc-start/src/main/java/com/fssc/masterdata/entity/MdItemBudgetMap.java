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
 * 费用类型与预算科目映射（需求 2.34）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_item_budget_map")
public class MdItemBudgetMap extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 费用类型ID */
    private Long itemId;

    /** 费用类型名称 */
    private String itemName;

    /** 预算科目ID */
    private Long budgetItemId;

    /** 预算科目维度 */
    private String budgetDimKey;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
