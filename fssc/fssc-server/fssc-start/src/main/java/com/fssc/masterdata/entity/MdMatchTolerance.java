// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 三单匹配参数化容差配置（md_match_tolerance，带 tenant_id）。
 * 按维度(维度 code)配置容差类型与容差值，供 ThreeWayMatchService 做软硬判定。
 * 维度：PAY_VS_INVOICE（付款≤发票，硬约束容差0）、INVOICE_VS_RECEIPT（发票≤收货）、RECEIPT_VS_PO（收货≤订单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_match_tolerance")
public class MdMatchTolerance extends TenantAuditBase {

    /** 维度：PAY_VS_INVOICE / INVOICE_VS_RECEIPT / RECEIPT_VS_PO */
    private String dimensionCode;
    /** 适用公司(NULL=全组织)(41号审查 P2-13 差异化容差) */
    private Long companyId;
    /** 适用供应商(NULL=全部) */
    private Long supplierId;
    /** 适用物料组(NULL=全部,预留) */
    private String materialGroup;
    /** 容差类型：PERCENT百分比 / FIXED固定金额 */
    private String toleranceType;
    /** 容差值（百分比用 2.0000=2%；固定金额用元） */
    private BigDecimal toleranceValue;
    private String remark;
    /** 生效日(NULL=立即) */
    private java.time.LocalDate effectDate;
    /** 失效日(NULL=长期) */
    private java.time.LocalDate expireDate;
}