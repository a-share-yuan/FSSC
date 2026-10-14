// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 部门（sys_dept，带 tenant_id）。树形：company_id 归属 + parent_id 父子。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends TenantAuditBase {

    /** 所属公司ID */
    private Long companyId;
    private String deptCode;
    private String deptName;
    /** 上级部门，0 为顶级 */
    private Long parentId;
    /** 部门负责人（sys_user.id）：审批节点"直线上级/部门负责人"动态解析用 */
    private Long leaderId;
    /** 祖级路径，逗号分隔 */
    private String ancestors;
    private Integer sort;
    /** NORMAL正常 / DISABLED停用 */
    private String status;
}
