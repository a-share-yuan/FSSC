// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 企业自助注册请求（公开接口，无需登录）。
 * 注册成功后租户进入 APPLYING（待审核），平台审核通过后自动创建租户管理员。
 */
public class RegisterForm {

    /** 企业编码（登录用，唯一） */
    @NotBlank(message = "企业编码必填")
    private String tenantCode;

    /** 企业全称 */
    @NotBlank(message = "企业名称必填")
    private String tenantName;

    /** 企业简称 */
    private String shortName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 行业 */
    private String industry;

    /** 企业规模 */
    private String scale;

    /** 联系人姓名 */
    @NotBlank(message = "联系人必填")
    private String contactName;

    /** 联系电话 */
    @NotBlank(message = "联系电话必填")
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 备注（申请说明） */
    private String remark;

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
