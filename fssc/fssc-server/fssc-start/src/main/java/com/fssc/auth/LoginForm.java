// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。subjectType=TENANT 时必须带 tenantCode（企业编码，登录时识别租户）；
 * subjectType=PLATFORM 时走 pf_user 通道。
 * <p>
 * captchaId / captchaCode（C-3 登录图形验证码）：<b>平时可不传</b>——仅当该身份连续凭据失败达到
 * {@code fssc.login.captcha-fail-threshold}（默认 3）且总开关 {@code fssc.login.captcha-enabled=true}
 * 时，服务端才回 {@code 4011 CAPTCHA_REQUIRED} 要求携带；两者由 GET /api/auth/captcha 成对下发，
 * 一次一用（校验过即失效，重复提交判 {@code 4012}）。
 */
public class LoginForm {

    @NotBlank(message = "username 必填")
    private String username;

    @NotBlank(message = "password 必填")
    private String password;

    /** PLATFORM / TENANT */
    @NotBlank(message = "subjectType 必填")
    private String subjectType;

    /** 企业编码（TENANT 必填） */
    private String tenantCode;

    /** 记住我（true 时延长 Token 有效期至 7 天） */
    private Boolean rememberMe;

    /** 验证码号（C-3）：由 GET /api/auth/captcha 下发，与 captchaCode 配套，失败阈值之上必填 */
    private String captchaId;

    /** 验证码输入（C-3）：校验通过即消费失效（防重放） */
    private String captchaCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

    public Boolean getRememberMe() { return rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }

    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}
