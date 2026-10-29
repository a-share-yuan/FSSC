// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import com.fssc.auth.AuthController.ProfileUpdateReq;
import com.fssc.auth.AuthController.ProfileVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.StrUtil;
import com.fssc.framework.security.LoginGuardService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.TokenService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfLoginLog;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.entity.PfUser;
import com.fssc.platform.mapper.PfLoginLogMapper;
import com.fssc.platform.mapper.PfTenantMapper;
import com.fssc.platform.mapper.PfTenantPackageMapper;
import com.fssc.platform.mapper.PfUserMapper;
import com.fssc.system.entity.SysLoginLog;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysLoginLogMapper;
import com.fssc.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 真实登录服务（平台 pf_user / 租户 sys_user 双通道）。
 * <p>
 * 关键点：
 * <ul>
 *   <li>租户识别：tenantCode → pf_tenant → tenantId，然后在该租户上下文中校验 sys_user。</li>
 *   <li>租户上下文在登录期通过 TenantContext.callAs(tenantId) 建立 —— tenantId 来自
 *       pf_tenant 查询结果（先验企业、再验人，最终人必须能通过该租户的 BCrypt 校验）。</li>
 *   <li>登录成功写入 Token：subjectType/tenantId/perms/features/dataScope 全部进 claims。</li>
 *   <li>双轨登录日志：平台写 pf_login_log，租户写 sys_login_log（带 tenant_id）。</li>
 *   <li>登录防刷（LoginGuardService）：进入先做 IP 限流、密码校验前判断失败锁定；凭据错误记失败、
 *       成功清零，账号不存在与密码错误统一提示防枚举。正确凭据的登录路径不受影响。</li>
 *   <li>图形验证码（C-3，{@link CaptchaTriggerService} + {@link CaptchaService}）：<b>失败阈值触发</b>，
 *       不是无条件强校验——总开关 {@code fssc.login.captcha-enabled}（默认 false）开启后，
 *       同一身份连续凭据失败累计达 {@code fssc.login.captcha-fail-threshold}（默认 3）才开始强制携带，
 *       登录成功即清零；未达阈值与开关关闭时登录链完全不介入（冒烟脚本程序化登录零影响）。
 *       判定顺序固定：IP 限流 ① → 锁定 ② → 验证码 ③ → 凭据校验，与既有防刷不打架（锁定阈值 5 高于 3）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PfUserMapper pfUserMapper;
    private final PfTenantMapper pfTenantMapper;
    private final PfTenantPackageMapper pfTenantPackageMapper;
    private final SysUserMapper sysUserMapper;
    private final PfLoginLogMapper pfLoginLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UaParser uaParser;
    private final LoginGuardService loginGuard;
    /** 验证码触发判定（C-3）：总开关 + 失败阈值 */
    private final CaptchaTriggerService captchaTrigger;
    /** 验证码本体（C-3）：一次性图片下发与消费 */
    private final CaptchaService captchaService;

    /** 通配权限：平台超管 / 租户管理员 */
    public static final String WILDCARD_PERM = "*:*:*";

    // 租户登录

    public LoginResult tenantLogin(String tenantCode, String username, String password, boolean rememberMe,
                                   String captchaId, String captchaCode, HttpServletRequest req) {
        // ① IP 限流：进入即拦截，早于任何 DB 查询，尽早卸载暴力破解流量
        loginGuard.checkIpRateLimit(ip(req));

        PfTenant tenant = pfTenantMapper.findByCode(tenantCode);
        if (tenant == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "企业编码不存在");
        }
        Long tenantId = tenant.getId();

        // 租户生命周期状态检查（登录入口也守一次，与 TenantFilter 双保险）
        String reason = checkTenantStatus(tenant);
        if (reason != null) {
            saveSysLogin(tenantId, username, false, reason, req);
            throw new BizException(ErrorCode.FORBIDDEN, reason);
        }

        // ② 失败锁定：密码校验前先判断该身份是否处于锁定期（留一条 FAIL 审计）
        try {
            loginGuard.checkLock(tenantCode, username);
        } catch (BizException locked) {
            saveSysLogin(tenantId, username, false, "登录失败次数过多，账号临时锁定", req);
            throw locked;
        }

        // ③ 图形验证码（C-3）：失败阈值之上才强制携带；未达阈值/总开关关闭直接放行
        ErrorCode captchaReject = captchaGate(tenantCode, username, captchaId, captchaCode);
        if (captchaReject != null) {
            saveSysLogin(tenantId, username, false, captchaReject.getMessage(), req);
            throw new BizException(captchaReject);
        }

        final LoginUser[] holder = new LoginUser[1];
        final String[] failMsg = new String[1];
        final boolean[] badCredential = new boolean[1];
        TenantContext.callAs(tenantId, () -> {
            SysUser user = sysUserMapper.findByUserName(username);
            // 账号不存在与密码错误统一提示，防枚举
            if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
                badCredential[0] = true;
                failMsg[0] = "用户名或密码错误";
                return;
            }
            if (!"NORMAL".equals(user.getStatus())) {
                failMsg[0] = "账号已停用或锁定";
                return;
            }
            holder[0] = buildTenantUser(user, tenantId);
        });

        if (failMsg[0] != null) {
            boolean captchaNowRequired = false;
            if (badCredential[0]) {
                loginGuard.recordFailure(tenantCode, username);
                // 同一事件点驱动验证码触发计数；跨过阈值那一拍直接把「需要验证码」标志带在同一条响应里，
                // 前端无需多跑一次请求才知道要显示验证码
                captchaNowRequired = captchaTrigger.recordFailure(tenantCode, username);
            }
            saveSysLogin(tenantId, username, false, failMsg[0], req);
            if (captchaNowRequired) {
                throw new BizException(ErrorCode.CAPTCHA_REQUIRED.getCode(),
                        failMsg[0] + "，连续失败已达阈值，请携带图形验证码重试");
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, failMsg[0]);
        }

        LoginUser u = holder[0];
        loginGuard.resetOnSuccess(tenantCode, username);
        captchaTrigger.resetOnSuccess(tenantCode, username);
        String token = tokenService.createToken(u, rememberMe);
        saveSysLogin(tenantId, username, true, "登录成功", req);
        return new LoginResult(token, u);
    }

    private LoginUser buildTenantUser(SysUser user, Long tenantId) {
        LoginUser u = new LoginUser();
        u.setSubjectType(SubjectType.TENANT);
        u.setTenantId(tenantId);
        u.setUserId(user.getId());
        u.setUsername(user.getUserName());
        u.setRealName(user.getNickName());
        u.setCompanyId(user.getCompanyId());
        u.setDeptId(user.getDeptId());
        u.setLoginAt(System.currentTimeMillis());

        // 权限码：租户管理员给通配；否则按角色关联菜单取
        Set<String> perms;
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            // 注意：Set.of(...) 为不可变集合，后续 removeIf 会抛 UnsupportedOperationException
            perms = new HashSet<>();
            perms.add(WILDCARD_PERM);
        } else {
            perms = StrUtil.expandPerms(sysUserMapper.selectPerms(user.getId()));
        }
        // USG-06: 过滤空白项;有角色但权限装载为空=加载异常(曾致签发全空权限降级 token)——
        // fail-closed 拒绝登录而非放行无权限会话
        perms.removeIf(p -> p == null || p.isBlank());
        if (perms.isEmpty() && (user.getIsAdmin() == null || user.getIsAdmin() != 1)) {
            boolean hasRoles = !sysUserMapper.selectRoles(user.getId()).isEmpty();
            if (hasRoles) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "权限加载异常，请重新登录；持续出现请联系管理员");
            }
        }
        u.setPerms(perms);

        // 数据权限：多角色取最宽（min），查不到默认 5（本人）—— fail-closed
        Integer ds = sysUserMapper.selectDataScope(user.getId());
        u.setDataScope(ds == null ? 5 : ds);

        // 套餐功能码（租户买的套餐里开启的功能）
        u.setFeatures(new HashSet<>(pfTenantPackageMapper.selectEnabledFeatures(tenantId)));
        return u;
    }

    // 平台登录

    public LoginResult platformLogin(String username, String password, boolean rememberMe,
                                     String captchaId, String captchaCode, HttpServletRequest req) {
        // ① IP 限流：进入即拦截
        loginGuard.checkIpRateLimit(ip(req));

        // ② 失败锁定：平台端身份键固定为 PLATFORM
        try {
            loginGuard.checkLock("PLATFORM", username);
        } catch (BizException locked) {
            savePfLogin(username, false, "登录失败次数过多，账号临时锁定", req);
            throw locked;
        }

        // ③ 图形验证码（C-3）：失败阈值之上才强制携带；未达阈值/总开关关闭直接放行
        ErrorCode captchaReject = captchaGate("PLATFORM", username, captchaId, captchaCode);
        if (captchaReject != null) {
            savePfLogin(username, false, captchaReject.getMessage(), req);
            throw new BizException(captchaReject);
        }

        PfUser user = pfUserMapper.findByUsername(username);
        // 账号不存在与密码错误统一提示，防枚举
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            loginGuard.recordFailure("PLATFORM", username);
            boolean captchaNowRequired = captchaTrigger.recordFailure("PLATFORM", username);
            savePfLogin(username, false, "用户名或密码错误", req);
            if (captchaNowRequired) {
                throw new BizException(ErrorCode.CAPTCHA_REQUIRED.getCode(),
                        "用户名或密码错误，连续失败已达阈值，请携带图形验证码重试");
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            savePfLogin(username, false, "账号已停用", req);
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }

        LoginUser u = new LoginUser();
        u.setSubjectType(SubjectType.PLATFORM);
        u.setTenantId(null);
        u.setUserId(user.getId());
        u.setUsername(user.getUsername());
        u.setRealName(user.getRealName());
        u.setLoginAt(System.currentTimeMillis());
        // 平台端权限模型收敛为"超管通配";非超管账号无可授予权限来源(pf_menu RBAC 读路径
        // 已随 pf_menu/pf_role_menu/pf_user_role 下线,见 ),登录后权限为空集
        u.setPerms((user.getIsSuper() != null && user.getIsSuper() == 1)
                ? Set.of(WILDCARD_PERM)
                : Set.of());
        u.setFeatures(Set.of());
        u.setDataScope(null);

        loginGuard.resetOnSuccess("PLATFORM", username);
        captchaTrigger.resetOnSuccess("PLATFORM", username);
        String token = tokenService.createToken(u, rememberMe);
        savePfLogin(username, true, "登录成功", req);
        return new LoginResult(token, u);
    }

    // 图形验证码校验（C-3）

    /**
     * 验证码校验：<b>失败阈值触发</b>，不是无条件强校验。
     * <ul>
     *   <li>总开关 {@code fssc.login.captcha-enabled=false}（默认）或未达阈值 ⇒ 直接放行，
     *       登录链零改动（01 册冒烟脚本 S1-01~S1-25 的程序化登录不受影响）；</li>
     *   <li>已达阈值 ⇒ 必须携带 {@code captchaId + captchaCode}，缺任一项回
     *       {@link ErrorCode#CAPTCHA_REQUIRED}，携带但不对（过期/重复提交/输错）回
     *       {@link ErrorCode#CAPTCHA_INVALID}；两种情形<b>都不计一次凭据失败</b>
     *       （验证码打错不该把用户推向 {@code LoginGuardService} 的 5 次锁定），
     *       但会按既有规则留一条 FAIL 登录日志。</li>
     * </ul>
     * 一次一用由 {@link CaptchaService#validate} 保证：无论对错，取用即从缓存删除，防重放。
     *
     * @param scope 身份域：租户传 tenantCode，平台传常量 "PLATFORM"（与 LoginGuardService 同键）
     * @return null = 放行；非 null = 需要以该错误码拒绝本次登录
     */
    private ErrorCode captchaGate(String scope, String username, String captchaId, String captchaCode) {
        if (!captchaTrigger.requiresCaptcha(scope, username)) {
            return null;
        }
        if (isBlank(captchaId) || isBlank(captchaCode)) {
            return ErrorCode.CAPTCHA_REQUIRED;
        }
        return captchaService.validate(captchaId, captchaCode) ? null : ErrorCode.CAPTCHA_INVALID;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // 租户生命周期校验（登录侧）

    /** 返回 null 表示可登录；否则返回拦截原因 */
    private String checkTenantStatus(PfTenant tenant) {
        return switch (tenant.getStatus() == null ? "" : tenant.getStatus()) {
            case "NORMAL" -> {
                if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
                    yield "租户套餐已过期，请联系平台续费";
                }
                yield null;
            }
            case "APPLYING" -> "租户待平台审核";
            case "FROZEN" -> "租户已冻结，请联系平台";
            case "CANCELLING" -> "租户注销中";
            case "CANCELLED" -> "租户已注销";
            case "REJECTED" -> "租户入驻申请已被拒绝";
            default -> "租户状态异常";
        };
    }

    // 登录日志

    private void savePfLogin(String username, boolean ok, String msg, HttpServletRequest req) {
        PfLoginLog l = new PfLoginLog();
        l.setUsername(username);
        l.setLoginIp(ip(req));
        l.setLoginType("LOGIN");
        l.setStatus(ok ? 1 : 0);
        l.setMsg(msg);
        l.setLoginTime(LocalDateTime.now());
        String ua = req.getHeader("User-Agent");
        if (ua != null) {
            l.setBrowser(uaParser.parseBrowser(ua));
            l.setOs(uaParser.parseOs(ua));
        }
        pfLoginLogMapper.insert(l);
    }

    private void saveSysLogin(Long tenantId, String username, boolean ok, String msg, HttpServletRequest req) {
        TenantContext.callAs(tenantId, () -> {
            SysLoginLog l = new SysLoginLog();
            l.setTenantId(tenantId);
            l.setUserName(username);
            l.setIpaddr(ip(req));
            l.setLoginType("LOGIN");
            l.setStatus(ok ? 0 : 1);
            l.setMsg(msg);
            l.setLoginTime(LocalDateTime.now());
            String ua = req.getHeader("User-Agent");
            if (ua != null) {
                l.setBrowser(uaParser.parseBrowser(ua));
                l.setOs(uaParser.parseOs(ua));
            }
            sysLoginLogMapper.insert(l);
        });
    }

    private String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    // 个人中心

    /** 获取个人资料（含权限） */
    public ProfileVo getProfile(LoginUser ctx) {
        ProfileVo vo = new ProfileVo();
        if (ctx.getSubjectType() == SubjectType.TENANT && ctx.getTenantId() != null) {
            TenantContext.callAs(ctx.getTenantId(), () -> {
                SysUser user = sysUserMapper.selectById(ctx.getUserId());
                vo.setUser(user);
                vo.setRoles(sysUserMapper.selectRoles(ctx.getUserId()));
                vo.setPerms(List.copyOf(ctx.getPerms()));
                vo.setFeatures(List.copyOf(ctx.getFeatures()));
            });
        }
        return vo;
    }

    /** 更新个人资料 */
    public void updateProfile(LoginUser ctx, ProfileUpdateReq req) {
        if (ctx.getSubjectType() != SubjectType.TENANT) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅租户用户可修改个人资料");
        }
        TenantContext.callAs(ctx.getTenantId(), () -> {
            SysUser user = new SysUser();
            user.setId(ctx.getUserId());
            user.setNickName(req.getNickName());
            // 手机号租户内唯一校验
            if (req.getPhone() != null && !req.getPhone().isBlank()) {
                long phoneDup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, req.getPhone())
                        .ne(SysUser::getId, ctx.getUserId()));
                if (phoneDup > 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被占用: " + req.getPhone());
                }
                user.setPhone(req.getPhone());
            }
            user.setEmail(req.getEmail());
            sysUserMapper.updateById(user);
        });
    }

    /** 修改密码 */
    public void changePassword(LoginUser ctx, String oldPassword, String newPassword) {
        if (ctx.getSubjectType() == SubjectType.TENANT && ctx.getTenantId() != null) {
            TenantContext.callAs(ctx.getTenantId(), () -> {
                SysUser user = sysUserMapper.selectById(ctx.getUserId());
                if (user == null) throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在");
                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "旧密码错误");
                }
                SysUser update = new SysUser();
                update.setId(ctx.getUserId());
                update.setPassword(passwordEncoder.encode(newPassword));
                sysUserMapper.updateById(update);
            });
        } else {
            throw new BizException(ErrorCode.FORBIDDEN, "仅租户用户可修改密码");
        }
    }

    /** 更换手机号（简化版本：验证码校验略过，实际生产应接入短信服务） */
    public void changePhone(LoginUser ctx, String newPhone, String verifyCode) {
        if (ctx.getSubjectType() != SubjectType.TENANT) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅租户用户可更换手机号");
        }
        // 验证码校验（简化：默认验证码 888888，生产接入短信服务后替换）
        if (!"888888".equals(verifyCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码错误");
        }
        TenantContext.callAs(ctx.getTenantId(), () -> {
            long phoneDup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, newPhone)
                    .ne(SysUser::getId, ctx.getUserId()));
            if (phoneDup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被占用: " + newPhone);
            }
            SysUser user = new SysUser();
            user.setId(ctx.getUserId());
            user.setPhone(newPhone);
            sysUserMapper.updateById(user);
        });
    }

    /** 更换邮箱（简化版本） */
    public void changeEmail(LoginUser ctx, String newEmail, String verifyCode) {
        if (ctx.getSubjectType() != SubjectType.TENANT) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅租户用户可更换邮箱");
        }
        if (!"888888".equals(verifyCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码错误");
        }
        TenantContext.callAs(ctx.getTenantId(), () -> {
            SysUser user = new SysUser();
            user.setId(ctx.getUserId());
            user.setEmail(newEmail);
            sysUserMapper.updateById(user);
        });
    }
}
