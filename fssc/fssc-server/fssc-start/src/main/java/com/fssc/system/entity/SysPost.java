// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位（sys_post，带 tenant_id）。归属公司+部门，支持左树右表联动过滤。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends TenantAuditBase {

    /** 所属公司ID（sys_company.id） */
    private Long companyId;
    /** 所属部门ID（sys_dept.id） */
    private Long deptId;
    private String postCode;
    private String postName;
    /** 岗位职级标签（如 总监级），用于差旅标准/审批权限判定 */
    private Integer postLevel;
    private Integer sort;
    /** NORMAL正常 / DISABLED停用 */
    private String status;
    /** 备注 */
    private String remark;
}
