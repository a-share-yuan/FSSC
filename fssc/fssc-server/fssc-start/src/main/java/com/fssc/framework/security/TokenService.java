// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.util.StrUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * JWT 工具。负责把 LoginUser 编码进 Token，以及验签后还原。
 * <p>
 * 安全要点：
 * <ul>
 *   <li>HS256 + 强密钥（配置项 fssc.jwt.secret，生产环境 ≥ 32 字节且必须外部注入）。</li>
 *   <li>tenantId / subjectType 等关键信息全部封装在 Token claims 里，
 *       由服务端签发并验签；前端无法伪造，也无法通过请求头篡改租户身份。</li>
 *   <li>解析时一律 verifyWith（验签）后再读 claims，任何篡改都会抛异常。</li>
 * </ul>
 */
@Slf4j
@Component
public class TokenService {

    @Value("${fssc.jwt.secret:fssc-default-secret-please-change-32bytes!!}")
    private String secret;

    @Value("${fssc.jwt.expire-seconds:7200}")
    private long expireSeconds;

    /** 当前激活的 profile（逗号分隔），用于启动自检判断是否为 dev 环境 */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * C-2：内置默认密钥<b>放行分支</b>的开关（{@code fssc.jwt.allow-default-secret}）。
     * <p>缺省 {@code false} = fail-closed：默认/缺失密钥一律走失败分支（拒绝启动、拒绝签发、拒绝验签）。
     * 只有 dev profile <b>且</b>本项显式为 true（见 {@code application-dev.yml}）才降级为一行 WARN 后继续，
     * 保持本地/演示与 01 册冒烟脚本零影响。生产 profile 在 {@code application-prod.yml} 里写死 false，
     * 配置层"把默认密钥放开"这件事在生产不可能发生。</p>
     */
    @Value("${fssc.jwt.allow-default-secret:false}")
    private boolean allowDefaultSecret;

    /** 与 application.yml 中保持一致的默认占位密钥；生产禁止沿用 */
    private static final String DEFAULT_SECRET = "fssc-default-secret-please-change-32bytes!!";

    /**
     * 启动自检：防止生产环境沿用默认/缺失的 JWT 密钥。
     * <p>判定与 {@link #key()} 内层守卫<b>同一份判据</b>（{@link #assertSecretUsable}），
     * 区别只在于启动期报"启动自检"、签名期报"签发/验签"。"dev 且默认密钥 ⇒ 仅告警仍签发"
     * 的分支由 {@link #allowDefaultSecret} 控制，缺省关闭。</p>
     */
    @PostConstruct
    public void checkSecret() {
        assertSecretUsable("启动自检");
    }

    /** 密钥是否缺失（null 或全空白）—— 生产键值写成 {@code ${FSSC_JWT_SECRET:}} 时，环境变量没给就是空串。 */
    private boolean isMissingSecret() {
        return secret == null || secret.trim().isEmpty();
    }

    /** 密钥是否等于内置默认占位值（trim 后比较，避免"前后加空格"绕过判定）。 */
    private boolean isDefaultSecret() {
        return secret != null && DEFAULT_SECRET.equals(secret.trim());
    }

    /**
     * 密钥可用性判定（唯一一份判据，启动期与签名期共用）。
     *
     * @param when 失败上下文（进异常文案，便于运维定位是启动就没过还是运行期没过）
     * @throws IllegalStateException 密钥缺失／沿用内置默认值且未获 dev 放行／长度不足 32 字节
     */
    private void assertSecretUsable(String when) {
        if (isMissingSecret()) {
            throw new IllegalStateException("fssc.jwt.secret 未注入（" + when
                    + "）：必须由环境变量 FSSC_JWT_SECRET 提供 32 字节以上的强随机密钥；"
                    + "密钥缺失一律拒绝启动、拒绝签发，绝不回落到内置默认值");
        }
        if (isDefaultSecret()) {
            boolean dev = activeProfiles != null && activeProfiles.contains("dev");
            if (!(dev && allowDefaultSecret)) {
                throw new IllegalStateException("当前仍为内置默认 JWT 密钥，已按 fail-closed 拒绝（" + when
                        + "）：生产必须通过环境变量 FSSC_JWT_SECRET 注入 32 字节以上密钥；"
                        + "仅 dev profile 且显式配置 fssc.jwt.allow-default-secret=true 时允许沿用");
            }
            log.warn("当前使用默认 JWT 密钥，仅限开发");
            return;
        }
        if (secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("fssc.jwt.secret 长度必须 >= 32 字节（" + when + "）");
        }
    }

    /** 记住我 Token 有效期（秒），默认 7 天 */
    private static final long REMEMBER_ME_EXPIRE_SECONDS = 7 * 24 * 3600;

    private SecretKey key() {
        // C-2：签名/验签前再判一次。@PostConstruct 只在 Spring 装配路径上跑，
        // 直接 new TokenService()（单测、将来内嵌复用）也必须走同一失败分支，
        // 否则"启动自检被绕过 ⇒ 默认密钥照样签发"的口子还在。
        assertSecretUsable("签发/验签");
        // 密钥不足 256 位时 HS256 会报错，这里保证长度
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("fssc.jwt.secret 长度必须 >= 32 字节");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(LoginUser user) {
        return createToken(user, false);
    }

    /** 创建 Token，支持记住我（延长有效期） */
    public String createToken(LoginUser user, boolean rememberMe) {
        long now = System.currentTimeMillis();
        long ttl = rememberMe ? REMEMBER_ME_EXPIRE_SECONDS : expireSeconds;
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("typ", user.getSubjectType().getCode())
                .claim("tid", user.getTenantId())
                .claim("uid", user.getUserId())
                .claim("nam", user.getRealName())
                .claim("per", StrUtil.join(user.getPerms()))
                .claim("ftr", StrUtil.join(user.getFeatures()))
                .claim("dsc", user.getDataScope())
                .claim("cid", user.getCompanyId())
                .claim("did", user.getDeptId())
                .claim("lat", now)
                .claim("rmm", rememberMe ? 1 : 0)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl * 1000L))
                .signWith(key())
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims c;
        try {
            c = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期");
        }
        LoginUser u = new LoginUser();
        u.setUsername(c.getSubject());
        u.setSubjectType(SubjectType.of(c.get("typ", String.class)));
        u.setTenantId(c.get("tid", Long.class));
        u.setUserId(c.get("uid", Long.class));
        u.setRealName(c.get("nam", String.class));
        // USG-06: 空 per 声明不产生 [""] 伪权限——splitToSet 已过滤空白且恒返回可变集合（缺陷D1修复：
        // 原先对不可变空集 removeIf 会抛 UnsupportedOperationException，导致零角色用户所有请求500）
        u.setPerms(StrUtil.splitToSet(c.get("per", String.class)));
        u.setFeatures(StrUtil.splitToSet(c.get("ftr", String.class)));
        u.setDataScope(c.get("dsc", Integer.class));
        u.setCompanyId(c.get("cid", Long.class));
        u.setDeptId(c.get("did", Long.class));
        u.setLoginAt(c.get("lat", Long.class));
        return u;
    }

    /**
     * 解析已验签 Token 的剩余有效毫秒数，用于令牌黑名单按 Token 原有效期设置 TTL
     * （黑名单不需要比 Token 活得更久，避免长期占用存储）。
     *
     * @return 剩余毫秒数；已过期返回 0
     * @throws BizException Token 验签失败或格式非法
     */
    public long parseRemainingMillis(String token) {
        Claims c;
        try {
            c = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期");
        }
        Date exp = c.getExpiration();
        if (exp == null) {
            return 0L;
        }
        long remain = exp.getTime() - System.currentTimeMillis();
        return remain > 0 ? remain : 0L;
    }
}
