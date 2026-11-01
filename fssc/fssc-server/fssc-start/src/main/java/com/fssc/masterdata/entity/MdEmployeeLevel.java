// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 员工等级（差旅标准控制）（需求 2.18）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_employee_level")
public class MdEmployeeLevel extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 等级编码 */
    private String levelCode;
    /** 等级名称 */
    private String levelName;
    /** 排序 */
    private Integer sort;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
