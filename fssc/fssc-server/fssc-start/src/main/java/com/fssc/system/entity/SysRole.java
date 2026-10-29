// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户角色（sys_role，带 tenant_id） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends TenantAuditBase {

    private String roleCode;
    private String roleName;
    /** 数据权限：1本租户 2本公司 3本部门及以下 4本部门 5仅本人 */
    private Integer dataScope;
    /** 组织范围：COMPANY=本公司内解析(业务审批段) / GROUP=跨公司集中办理(共享段+集团垂直) */
    private String orgScope;
    /** NORMAL正常 / DISABLED停用 */
    private String status;
    private String remark;
}
