// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 税金计提（gl_tax_provision，带 tenant_id）。按会计期间批量计提税金，DRAFT → POSTED。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_tax_provision")
public class GlTaxProvision extends TenantAuditBase {

    /** 记账公司（组织合法性守卫） */
    private Long companyId;
    /** 计提单号 */
    private String provisionNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** VAT增值税/SURCHARGE附加/INCOME所得税 */
    private String taxType;
    /** 关联会计科目 md_subject.id */
    private Long subjectId;
    /** 计税基数 */
    private BigDecimal baseAmount;
    /** 税率（百分比，如 13=13%） */
    private BigDecimal rate;
    /** 税额 = baseAmount * rate / 100 */
    private BigDecimal taxAmount;
    /** DRAFT/POSTED */
    private String status;
    /** 记账凭证号 */
    private String voucherNo;
}