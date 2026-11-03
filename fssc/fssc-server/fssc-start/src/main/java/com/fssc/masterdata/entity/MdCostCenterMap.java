// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本中心预算组织映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_cost_center_map")
public class MdCostCenterMap extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 成本中心ID */
    private Long costCenterId;

    /** 成本中心名称 */
    private String costCenterName;

    /** 预算组织ID */
    private Long budgetOrgId;

    /** 预算组织名称 */
    private String budgetOrgName;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
