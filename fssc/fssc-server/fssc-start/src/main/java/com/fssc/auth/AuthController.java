// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import com.fssc.common.core.Result;
import com.fssc.common.enums.SubjectType;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.TokenBlacklistService;
import com.fssc.framework.security.TokenService;
import com.fssc.framework.security.UserContext;
import com.fssc.platform.service.TenantLifecycleService;
import com.fssc.system.entity.SysUser;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（真实登录）。
 * <ul>
 *   <li>PLATFORM：pf_user + BCrypt，超管得通配权限 *:*:*。</li>
 *   <li>TENANT：tenantCode 识别租户 → sys_user + BCrypt → 装载 perms/features/dataScope 进 Token。</li>
 *   <li>register：企业自助注册（公开），落 APPLYING 待平台审核。</li>
 * </ul>
 * 该路径在白名单（/api/auth/**），无需 Token；登录成功后所有受保护接口走 JwtAuthFilter。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final TenantLifecycleService tenantLifecycleService;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenService tokenService;

    @PostMapping("/login")
    public Result<LoginResult> login(@Validated @RequestBody LoginForm form, HttpServletRequest request) {
        SubjectType type = SubjectType.of(form.getSubjectType());
        if (type == null) {
            return Result.fail(400, "subjectType 必须是 PLATFORM 或 TENANT");
        }
        boolean rememberMe = form.getRememberMe() != null && form.getRememberMe();
        // captchaId/captchaCode（C-3）：仅在该身份连续失败达阈值时被服务端要求，平时可为 null
        if (type == SubjectType.PLATFORM) {
            return Result.success(authService.platformLogin(form.getUsername(), form.getPassword(), rememberMe,
                    form.getCaptchaId(), form.getCaptchaCode(), request));
        }
        if (form.getTenantCode() == null || form.getTenantCode().isBlank()) {
            return Result.fail(400, "租户登录必须携带 tenantCode");
        }
        return Result.success(authService.tenantLogin(
                form.getTenantCode().trim(), form.getUsername(), form.getPassword(), rememberMe,
                form.getCaptchaId(), form.getCaptchaCode(), request));
    }

    /** 企业自助注册（公开，无需登录）：提交后进入 APPLYING，待平台审核开通 */
    @PostMapping("/tenant/register")
    public Result<Long> register(@Validated @RequestBody RegisterForm form) {
        return Result.success(tenantLifecycleService.register(
                form.getTenantCode(), form.getTenantName(), form.getShortName(),
                form.getCreditCode(), form.getIndustry(), form.getScale(),
                form.getContactName(), form.getContactPhone(), form.getContactEmail(), form.getRemark()));
    }

    /** 退出登录（将当前 Token 加入黑名单，实现后端强制下线） */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            tokenBlacklistService.add(token);
        }
        return Result.success();
    }

    /**
     * Token 滑动续期：验签通过且未进黑名单的 Token 可换取新 Token（TTL 重新计时），
     * 旧 Token 立即入黑名单防重放；续期后按普通会话（非记住我）处理。
     */
    @PostMapping("/refresh")
    public Result<LoginResult> refresh(HttpServletRequest request) {
        String old = resolveToken(request);
        if (old == null || tokenBlacklistService.isBlacklisted(old)) {
            return Result.fail(401, "Token 无效或已过期");
        }
        LoginUser u;
        try {
            u = tokenService.parseToken(old);
        } catch (Exception e) {
            return Result.fail(401, "Token 无效或已过期");
        }
        tokenBlacklistService.add(old);
        String fresh = tokenService.createToken(u, false);
        return Result.success(new LoginResult(fresh, u));
    }

    // 个人中心：改密、换绑、更新资料

    /** 获取当前登录用户详细信息（含权限） */
    @PostMapping("/profile")
    public Result<ProfileVo> profile() {
        var ctx = UserContext.getRequired();
        return Result.success(authService.getProfile(ctx));
    }

    /** 更新个人资料（姓名、手机号、邮箱） */
    @PostMapping("/profile/update")
    @OperLog(module = "AUTH", operType = "UPDATE", desc = "更新个人资料")
    public Result<Void> updateProfile(@Validated @RequestBody ProfileUpdateReq req) {
        var ctx = UserContext.getRequired();
        authService.updateProfile(ctx, req);
        return Result.success();
    }

    /** 修改密码（需验证旧密码） */
    @PostMapping("/profile/changePassword")
    @OperLog(module = "AUTH", operType = "UPDATE", desc = "修改密码")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordReq req) {
        var ctx = UserContext.getRequired();
        authService.changePassword(ctx, req.getOldPassword(), req.getNewPassword());
        return Result.success();
    }

    /** 更换手机号 */
    @PostMapping("/profile/changePhone")
    @OperLog(module = "AUTH", operType = "UPDATE", desc = "更换手机号")
    public Result<Void> changePhone(@Validated @RequestBody ChangePhoneReq req) {
        var ctx = UserContext.getRequired();
        authService.changePhone(ctx, req.getNewPhone(), req.getVerifyCode());
        return Result.success();
    }

    /** 更换邮箱 */
    @PostMapping("/profile/changeEmail")
    @OperLog(module = "AUTH", operType = "UPDATE", desc = "更换邮箱")
    public Result<Void> changeEmail(@Validated @RequestBody ChangeEmailReq req) {
        var ctx = UserContext.getRequired();
        authService.changeEmail(ctx, req.getNewEmail(), req.getVerifyCode());
        return Result.success();
    }

    private String resolveToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7).trim();
        }
        return null;
    }

    // DTO

    public static class ProfileVo {
        private SysUser user;
        private List<String> perms;
        private List<String> roles;
        private List<String> features;

        public SysUser getUser() { return user; }
        public void setUser(SysUser user) { this.user = user; }
        public List<String> getPerms() { return perms; }
        public void setPerms(List<String> perms) { this.perms = perms; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public List<String> getFeatures() { return features; }
        public void setFeatures(List<String> features) { this.features = features; }
    }

    public static class ProfileUpdateReq {
        @NotBlank(message = "姓名不能为空")
        private String nickName;
        private String phone;
        private String email;

        public String getNickName() { return nickName; }
        public void setNickName(String nickName) { this.nickName = nickName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ChangePasswordReq {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class ChangePhoneReq {
        @NotBlank(message = "新手机号不能为空")
        private String newPhone;
        @NotBlank(message = "验证码不能为空")
        private String verifyCode;

        public String getNewPhone() { return newPhone; }
        public void setNewPhone(String newPhone) { this.newPhone = newPhone; }
        public String getVerifyCode() { return verifyCode; }
        public void setVerifyCode(String verifyCode) { this.verifyCode = verifyCode; }
    }

    public static class ChangeEmailReq {
        @NotBlank(message = "新邮箱不能为空")
        private String newEmail;
        @NotBlank(message = "验证码不能为空")
        private String verifyCode;

        public String getNewEmail() { return newEmail; }
        public void setNewEmail(String newEmail) { this.newEmail = newEmail; }
        public String getVerifyCode() { return verifyCode; }
        public void setVerifyCode(String verifyCode) { this.verifyCode = verifyCode; }
    }
}
