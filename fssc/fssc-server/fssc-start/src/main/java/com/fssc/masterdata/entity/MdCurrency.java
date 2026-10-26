// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 币种（md_currency，带 tenant_id）。is_base=1 为记账本位币。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_currency")
public class MdCurrency extends TenantAuditBase {

    private String currencyCode;
    private String currencyName;
    private String symbol;
    private Integer decimalDigit;
    /** 1 本位币 */
    private Integer isBase;
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
