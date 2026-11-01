// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_cost_center（带 tenant_id，租户拦截器自动隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_cost_center")
public class MdCostCenter extends TenantAuditBase {

    private Long companyId;
    private String ccCode;
    private String ccName;
    private String ccType;
    private Long deptId;
    private Long managerId;
    private Long parentId;
    private Integer sort;
    private String status;
    private String remark;
}
