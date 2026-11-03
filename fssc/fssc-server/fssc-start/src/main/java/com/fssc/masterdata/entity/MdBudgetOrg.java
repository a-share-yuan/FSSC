// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算组织（责任组织）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_budget_org")
public class MdBudgetOrg extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 组织编码 */
    private String orgCode;
    /** 组织名称 */
    private String orgName;
    /** 上级组织ID */
    private Long parentId;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
