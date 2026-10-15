package com.fssc.framework.security;

import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-2（04 册 §3「令牌签名默认密钥放行分支」）回归单测。
 *
 * <p>被改前的真实行为：{@code TokenService#checkSecret} 里 {@code dev && usingDefault} 这一支
 * 只打一行 WARN 就照常签发，而生产 jar 当时也是以 dev profile 起跑的（application.yml
 * {@code spring.profiles.active: dev}）——于是"内置默认密钥"在生产也是<b>放行</b>的。
 * 本测试用「默认密钥 / 缺失密钥」两类输入实证失败分支，不依赖 Spring 容器与数据库。</p>
 *
 * <p>字段注入用 {@link ReflectionTestUtils}（与本仓既有单测同一依赖线：spring-boot-starter-test），
 * 目的是脱离容器直调 {@code checkSecret()} 与签发/验签两条链路。</p>
 */
class TokenSecretGuardTest {

    /**
     * 内置默认占位密钥（与 {@code application.yml} 的 {@code fssc.jwt.secret} 默认值、
     * {@code TokenService.DEFAULT_SECRET} 同值）。
     * <p>直接从被测类取，<b>不在本文件复述这份字面值</b>（交付纪律：新增文件不留任何密钥字面量）；
     * 取不到即说明判据前提被改动，测试应当失败。</p>
     */
    private static String defaultSecret() {
        try {
            Field f = TokenService.class.getDeclaredField("DEFAULT_SECRET");
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "TokenService.DEFAULT_SECRET 常量缺失/不可读 —— C-2 的判定前提（存在一份内置默认值）已变化", e);
        }
    }

    /** 测试用非默认密钥：运行时拼出来，源码里不留任何形似真实密钥的字面量。 */
    private static String nonDefaultSecret() {
        return "unit-test-only-".repeat(4);
    }

    private static TokenService service(String secret, String activeProfiles, boolean allowDefaultSecret) {
        TokenService svc = new TokenService();
        ReflectionTestUtils.setField(svc, "secret", secret);
        ReflectionTestUtils.setField(svc, "activeProfiles", activeProfiles);
        ReflectionTestUtils.setField(svc, "allowDefaultSecret", allowDefaultSecret);
        ReflectionTestUtils.setField(svc, "expireSeconds", 7200L);
        return svc;
    }

    private static LoginUser tenantUser() {
        LoginUser u = new LoginUser();
        u.setSubjectType(SubjectType.TENANT);
        u.setTenantId(1L);
        u.setUserId(1001L);
        u.setUsername("u_test");
        u.setRealName("测试用户");
        u.setPerms(Set.of("exp:claim:list"));
        u.setFeatures(Set.of());
        u.setDataScope(3);
        u.setCompanyId(10L);
        u.setDeptId(20L);
        return u;
    }

    // ------------------------------------------------ 失败分支：默认密钥

    @Test
    @DisplayName("C-2：默认密钥 + 非 dev profile ⇒ 启动自检抛异常（不放行）")
    void defaultSecret_nonDev_rejected() {
        TokenService svc = service(defaultSecret(), "prod", false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, svc::checkSecret);
        assertTrue(ex.getMessage().contains("默认"), "文案应说明拒绝原因，实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("C-2：默认密钥 + dev 但开关未显式放开 ⇒ 仍拒绝（开关缺省即 fail-closed）")
    void defaultSecret_devWithoutSwitch_rejected() {
        TokenService svc = service(defaultSecret(), "dev", false);
        assertThrows(IllegalStateException.class, svc::checkSecret);
    }

    @Test
    @DisplayName("C-2：默认密钥 + prod ⇒ 连签发也被拒绝（绕过 @PostConstruct 也过不去）")
    void defaultSecret_signingAlsoRejected() {
        TokenService svc = service(defaultSecret(), "prod", false);
        // 不经 Spring 装配（单测直接 new）时 @PostConstruct 不会跑，签发链路必须自己拦住
        assertThrows(IllegalStateException.class, () -> svc.createToken(tenantUser()));
    }

    @Test
    @DisplayName("C-2：默认密钥 + dev 且开关 true ⇒ 允许（本地/演示与冒烟脚本零影响）")
    void defaultSecret_devWithSwitch_allowed() {
        TokenService svc = service(defaultSecret(), "dev", true);
        svc.checkSecret();
        String token = svc.createToken(tenantUser());
        assertNotNull(token);
        assertEquals("u_test", svc.parseToken(token).getUsername());
    }

    // ------------------------------------------------ 失败分支：缺失/过短密钥

    @Test
    @DisplayName("C-2：密钥为空串（${FSSC_JWT_SECRET:} 未注入即此值）⇒ 启动自检抛异常，绝不回落内置默认值")
    void blankSecret_rejected() {
        TokenService svc = service("", "prod", false);
        assertThrows(IllegalStateException.class, svc::checkSecret);
    }

    @Test
    @DisplayName("C-2：密钥为 null ⇒ 启动自检与签发两处都拒绝")
    void nullSecret_rejected() {
        TokenService svc = service(null, "prod", false);
        assertThrows(IllegalStateException.class, svc::checkSecret);
        assertThrows(IllegalStateException.class, () -> svc.createToken(tenantUser()));
    }

    @Test
    @DisplayName("C-2：密钥缺失时验签侧同样拒绝（返回 BizException 401，绝不产出可用身份）")
    void blankSecret_verifyRejected() {
        TokenService svc = service("", "prod", false);
        // 先拿一份正常密钥签出的令牌，再换成"密钥缺失"的服务去验：必须失败而非放行
        String token = service(nonDefaultSecret(), "prod", false).createToken(tenantUser());
        assertThrows(BizException.class, () -> svc.parseToken(token));
    }

    @Test
    @DisplayName("C-2：注入非默认密钥 ⇒ 自检通过、签发可用（生产路径不被误伤）")
    void injectedSecret_allowed() {
        TokenService svc = service(nonDefaultSecret(), "prod", false);
        svc.checkSecret();
        LoginUser parsed = svc.parseToken(svc.createToken(tenantUser()));
        assertEquals(SubjectType.TENANT, parsed.getSubjectType());
        assertEquals(Long.valueOf(1L), parsed.getTenantId());
    }

    @Test
    @DisplayName("C-2：密钥不足 32 字节 ⇒ 启动即拒绝（不再拖到首次签发才炸）")
    void shortSecret_rejectedAtStartup() {
        TokenService svc = service("too-short-secret", "prod", false);
        assertThrows(IllegalStateException.class, svc::checkSecret);
    }

    @Test
    @DisplayName("C-2：默认密钥前后加空格 ⇒ 仍判为默认值（不能靠空白绕过判定）")
    void defaultSecretWithSpaces_rejected() {
        TokenService svc = service("  " + defaultSecret() + "  ", "prod", false);
        assertThrows(IllegalStateException.class, svc::checkSecret);
    }

    // ------------------------------------------------ 发布单断言：prod 配置文件本身

    @Test
    @DisplayName("C-2/C-3 发布断言：application-prod.yml 关掉放行分支、密钥只留环境变量引用、不含任何默认密钥字面值")
    void prodYmlIsFailClosed() throws Exception {
        String yml = readClasspath("application-prod.yml");
        assertTrue(yml.contains("allow-default-secret: false"),
                "prod 必须显式关闭默认密钥放行分支");
        assertTrue(yml.contains("secret: ${FSSC_JWT_SECRET:}"),
                "prod 密钥必须是环境变量引用且缺省为空（未注入即拒绝启动）");
        assertTrue(!yml.contains(defaultSecret()),
                "prod 配置内不得出现内置默认密钥字面值");

        // C-3：生产开验证码、阈值沿用代码默认 3、明文回传必须关闭
        assertTrue(yml.contains("captcha-enabled: true"), "prod 必须开启验证码总开关");
        assertTrue(yml.contains("captcha-fail-threshold: 3"), "prod 阈值沿用代码默认 3");
        assertTrue(yml.contains("captcha-debug-text: false"), "prod 必须关闭验证码明文回传");
    }

    @Test
    @DisplayName("C-2 发布断言：dev 侧显式放行开关存在，且缺省值（application.yml）不含放行开关⇒ 缺省即 fail-closed")
    void devYmlKeepsLocalUsable() throws Exception {
        String dev = readClasspath("application-dev.yml");
        assertTrue(dev.contains("allow-default-secret: true"),
                "dev 必须显式置 true，否则本地/演示会在启动自检处断裂");
        String base = readClasspath("application.yml");
        assertTrue(!base.contains("allow-default-secret"),
                "application.yml 不得写该开关（写了就等于把放行分支做成公共默认值）");
    }

    private static String readClasspath(String resource) throws Exception {
        try (InputStream in = TokenSecretGuardTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "classpath 资源缺失：" + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
