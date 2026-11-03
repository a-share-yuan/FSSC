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
 * 现金流量码（管理现金流量项目编码）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_cash_flow")
public class MdCashFlow extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 现金流量码 */
    private String cashCode;

    /** 现金流量名称 */
    private String cashName;

    /** 方向 IN流入/OUT流出 */
    private String direction;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
