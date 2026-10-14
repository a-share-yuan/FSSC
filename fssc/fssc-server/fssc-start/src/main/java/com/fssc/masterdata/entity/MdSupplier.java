// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商（md_supplier，带 tenant_id）。报销收票方主数据，租户级共享。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier")
public class MdSupplier extends TenantAuditBase {

    private String supplierCode;
    private String supplierName;
    private String shortName;
    /** 供应商类型：GOODS商品/SERVICE服务/TRAVEL差旅等 */
    private String supplierType;
    /** 统一社会信用代码 */
    private String creditCode;
    private String taxNo;
    /** GENERAL一般纳税人/SMALL小规模/OTHER */
    private String taxType;
    /** 付款条件(如 月结30/货到付款)(41号审查 P1-8) */
    private String payTerms;
    /** 结算币种 */
    private String currencyCode;
    /** 发票类型(专票/普票) */
    private String invoiceType;
    private String address;
    private String tel;
    private String contactName;
    private String contactPhone;
    /** 1 黑名单 0 正常 */
    private Integer blacklist;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
