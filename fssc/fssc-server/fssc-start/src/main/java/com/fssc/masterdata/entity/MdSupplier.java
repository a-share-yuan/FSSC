// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商（md_supplier，带 tenant_id）。报销收票方主数据，租户级共享。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier")
public class MdSupplier extends TenantAuditBase {

    @NotBlank(message = "供应商编码(supplierCode)不能为空")
    @Size(max = 64, message = "供应商编码最多 64 个字符")
    private String supplierCode;
    @NotBlank(message = "供应商名称(supplierName)不能为空")
    @Size(max = 128, message = "供应商名称最多 128 个字符")
    private String supplierName;
    private String shortName;
    /** 供应商类型：GOODS商品/SERVICE服务/TRAVEL差旅等 */
    private String supplierType;
    /** 统一社会信用代码（选填，填了就必须合规：18 位 GB 32100 字符集） */
    @Pattern(regexp = "^([0-9A-HJ-NPQRTUWXY]{18})?$", message = "统一社会信用代码应为 18 位（数字+大写字母，不含 I/O/Z/S/V）")
    private String creditCode;
    /** 税号（选填：15 位旧版 / 18 位信用代码 / 20 位） */
    @Pattern(regexp = "^([0-9A-Z]{15}|[0-9A-HJ-NPQRTUWXY]{18}|[0-9A-Z]{20})?$", message = "税号应为 15/18/20 位数字或大写字母")
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
    /** 电话（选填，填了就必须合规：手机号或含区号固话） */
    @Pattern(regexp = "^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{6,8}(-\\d{1,5})?)?$", message = "电话应为手机号或含区号固话（如 0731-88888888）")
    private String tel;
    private String contactName;
    /** 联系手机（选填，填了就必须合规） */
    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "联系手机应为 11 位手机号")
    private String contactPhone;
    /** 1 黑名单 0 正常 */
    private Integer blacklist;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
