// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户/往来单位（crm_customer，带 tenant_id，逻辑删除）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CrmCustomer extends TenantAuditBase {

    private String customerCode;
    @NotBlank(message = "客户名称(customerName)不能为空")
    @Size(max = 128, message = "客户名称最多 128 个字符")
    private String customerName;
    /** 往来类型：CUSTOMER 客户 / SUPPLIER 供应商 / VENDOR 服务商 */
    @NotBlank(message = "往来类型(customerType)不能为空")
    private String customerType;
    private String contactName;
    /** 联系电话（选填，填了就必须合规：手机号或含区号固话） */
    @Pattern(regexp = "^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{6,8}(-\\d{1,5})?)?$", message = "联系电话应为手机号或含区号固话（如 0731-88888888）")
    private String contactPhone;
    /** 邮箱（选填，填了就必须合规） */
    @Pattern(regexp = "^([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?$", message = "邮箱格式不正确")
    private String email;
    private String address;
    /** 信用等级 A/B/C */
    private String creditLevel;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
