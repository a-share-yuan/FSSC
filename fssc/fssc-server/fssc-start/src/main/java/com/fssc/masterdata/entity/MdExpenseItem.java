// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 费用项（md_expense_item，带 tenant_id，树形）。报销单费用明细的分类骨架。
 * 合并自 expense_type（费用类型），新增字段见下。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_expense_item")
public class MdExpenseItem extends TenantAuditBase {

    private String itemCode;
    private String itemName;
    /** 父费用项，0 为顶级 */
    private Long parentId;
    private String ancestors;
    private Integer itemLevel;
    /** 1 叶子 0 非叶子 */
    private Integer isLeaf;
    /** 适用单据类型：APPLY/CLAIM/LOAN 等 */
    private String billType;
    /** 关联会计科目 */
    private Long subjectId;
    private BigDecimal defaultTaxRate;
    /** 1 必须发票 */
    private Integer needInvoice;
    /** 1 必须附件 */
    private Integer needAttach;
    /** 1 预算管控 */
    private Integer budgetControl;
    /** 费用标准控制：NONE/HARD/SOFT */
    private String stdControl;
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;

    // 合并自 expense_type（费用类型）

    /** 是否需事前审批：1-是 0-否 */
    private Integer requiresPreApproval;

    /** 是否需关联合同：1-是 0-否 */
    private Integer requiresContract;

    /** 是否需关联项目：1-是 0-否 */
    private Integer requiresProject;

    /** 预算控制类型：RIGID/FLEXIBLE/NONE */
    private String budgetControlType;

    /** 审批复杂度：1-5 */
    private Integer approvalComplexity;

    /** 是否含税：1-含税 0-不含税 */
    private Integer hasTax;

    /** 默认总账科目 */
    private String defaultGlAccount;

    /** 排序号 */
    private Integer sortOrder;
}
