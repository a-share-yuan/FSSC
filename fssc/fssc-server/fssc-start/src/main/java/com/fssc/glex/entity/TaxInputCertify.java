// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进项认证台账（tax_input_certify）。
 * 逐张发票跟踪进项税认证状态：待认证→已认证→已抵扣→已转出。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tax_input_certify")
public class TaxInputCertify extends TenantAuditBase {

    private Long companyId;
    private Long invoiceId;
    private String invoiceNo;
    private String invoiceCode;
    /** 专票/普通发票/电子发票/农产品收购 */
    private String invoiceType;
    private String supplierName;
    private String supplierTaxNo;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** PENDING/CERTIFIED/DEDUCTED/TRANSFERRED/REJECTED */
    private String certifyStatus;
    private LocalDate certifyDate;
    private String certifyPeriod;
    private String deductPeriod;
    private String transferReason;
    private Integer riskFlag;
    private String sourceDocType;
    private Long sourceDocId;
    private String remark;
}
