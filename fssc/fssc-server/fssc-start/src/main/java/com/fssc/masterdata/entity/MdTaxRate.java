// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 税率（md_tax_rate，带 tenant_id）。发票/费用取税。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_tax_rate")
public class MdTaxRate extends TenantAuditBase {

    private String rateCode;
    private String rateName;
    private BigDecimal rateValue;
    /** VAT增值税/OTHER */
    private String taxType;
    /** 1 可抵扣 */
    private Integer deductible;
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
}
