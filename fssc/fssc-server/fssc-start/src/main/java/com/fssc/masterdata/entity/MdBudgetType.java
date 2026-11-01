// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算类型（预算管理设计方案，设计原型 image_109）。
 * 区分"项目预算 / 常规预算"：hasProject=Y 时预算须关联具体项目。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_budget_type")
public class MdBudgetType extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 预算类型名称（唯一） */
    private String typeName;

    /** 是否包含项目 Y/N */
    private String hasProject;

    /** 排序 */
    private Integer sort;

    /** 状态 NORMAL启用 / DISABLED停用 */
    private String status;

    /** 备注 */
    private String remark;
}
