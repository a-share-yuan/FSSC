package com.fssc.framework.security;

import com.fssc.auth.CaptchaService;
import com.fssc.auth.CaptchaTriggerService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.spi.DistributedState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录防刷守卫：失败锁定 + IP 限流 + 验证码开关读取。内部计数/锁定状态已委托
 * {@link DistributedState}（多实例可插拔）：单机（默认）走进程内实现，行为与旧版逐一对齐；
 * 置 {@code fssc.redis.enabled=true} 后计数与锁定标记进 Redis（INCR+EXPIRE / 缓存标记），全实例共享。
 * <p>
 * 安全要点（对外语义不变）：
 * <ul>
 *   <li>失败锁定：同一身份（租户=tenantCode+username，平台=PLATFORM+username）在
 *       {@link #FAIL_WINDOW} 内连续失败 {@link #MAX_FAIL_COUNT} 次 → 锁定
 *       {@link #LOCK_TTL}；锁定期间 {@link #checkLock} 直接拒绝，成功登录 {@link #resetOnSuccess} 清零。</li>
 *   <li>IP 限流：同一 IP 在 {@link #IP_WINDOW}（1 分钟，自窗口内首次尝试起算——旧版为自然分钟
 *       分桶，判定规则"窗口内超 {@link #IP_LIMIT_PER_MINUTE} 次拒绝"不变）内尝试超限抛业务异常。</li>
 *   <li>验证码开关 {@code fssc.login.captcha-enabled} 默认关闭；置 true 后由
 *       {@code CaptchaTriggerService} 判定"该身份在本窗口内连续凭据失败是否已达
 *       {@code fssc.login.captcha-fail-threshold}（默认 3）"，达阈值后的登录必须携带一次性图形验证码
 *       （下发端点 {@code GET /api/auth/captcha}，实体见 {@code CaptchaService}）。
 *       本服务只提供开关读数与失败计数来源，不参与验证码的生成与比对。</li>
 * </ul>
 * <p>
 * key 约定（相对 key，Redis 实现统一加 {@code fssc:} 命名空间前缀）：{@code login:ip:<ip>}（IP 计数）、
 * {@code login:fail:<scope><username>}
 * 失败计数、{@code login:lock:<scope><username>}（锁定标记，getOrLoad 写入 Boolean 并随 TTL 自动过期）。
 */
@Service
public class LoginGuardService {

    /** 失败计数统计窗口：5 分钟 */
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(5);
    /** 触发锁定的连续失败次数 */
    private static final int MAX_FAIL_COUNT = 5;
    /** 锁定时长：15 分钟 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    /** 单 IP 每窗口登录尝试上限 */
    private static final int IP_LIMIT_PER_MINUTE = 20;
    /** IP 限流窗口：1 分钟 */
    private static final Duration IP_WINDOW = Duration.ofMinutes(1);

    /**
     * 图形验证码总开关（{@code fssc.login.captcha-enabled}，默认 false）。
     * <p>true 时并非"每次登录都要验证码"：只有同一身份连续凭据失败达
     * {@code fssc.login.captcha-fail-threshold}（默认 3）之后的登录才必须携带，
     * 判定在 {@code CaptchaTriggerService#requiresCaptcha}；验证码本体（生成/一次性校验）
     * 在 {@code CaptchaService}，下发端点 {@code GET /api/auth/captcha}。</p>
     */
    @Value("${fssc.login.captcha-enabled:false}")
    private boolean captchaEnabled;

    private final DistributedState state;

    public LoginGuardService(DistributedState state) {
        this.state = state;
    }

    /**
     * 验证码总开关读数（{@code fssc.login.captcha-enabled}）。
     * <p>本方法只回"开关开没开"，不代表"本次登录要不要填验证码" —— 后者由
     * {@code CaptchaTriggerService#requiresCaptcha} 叠加失败次数判定（阈值默认 3，登录成功清零）。
     * 开关关闭时登录链完全不介入验证码。</p>
     */
    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    /**
     * IP 限流：同一 IP 每分钟尝试超限抛业务异常。应在每次登录入口最先调用（早于任何 DB 查询，尽快卸载流量）。
     */
    public void checkIpRateLimit(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        if (!state.tryAcquireRate("login:ip:" + ip, IP_LIMIT_PER_MINUTE, IP_WINDOW)) {
            throw new BizException(ErrorCode.FORBIDDEN, "请求过于频繁，请稍后再试");
        }
    }

    /**
     * 锁定检查：处于锁定期直接抛业务异常。应在解析出租户/身份后、校验密码前调用。
     * <p>锁定标记以 Boolean 值缓存（TTL=锁定时长），到期自动消失即视为解锁，等价旧版"锁定期过清理重计"。</p>
     *
     * @param scope 租户端传 tenantCode，平台端传常量 "PLATFORM"
     */
    public void checkLock(String scope, String username) {
        Boolean locked = state.getOrLoad(lockKey(scope, username), Boolean.class,
                () -> Boolean.FALSE, LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已临时锁定，请15分钟后重试或联系管理员");
        }
    }

    /**
     * 记录一次凭据失败。当窗口内累计达到阈值即进入锁定；已在锁定期内的后续尝试由 {@link #checkLock} 提前拦截。
     * <p>计数走 {@link DistributedState#tryAcquireRate}（maxCount=阈值-1：第 5 次失败返回 false 即触发锁定），
     * 触发时先清旧标记再写入锁定标记（TTL=15 分钟）。</p>
     */
    public void recordFailure(String scope, String username) {
        String fail = failKey(scope, username);
        String lock = lockKey(scope, username);
        if (!state.tryAcquireRate(fail, MAX_FAIL_COUNT - 1, FAIL_WINDOW)) {
            state.evict(lock);
            state.getOrLoad(lock, Boolean.class, () -> Boolean.TRUE, LOCK_TTL);
        }
    }

    /** 登录成功清零该身份的失败/锁定状态 */
    public void resetOnSuccess(String scope, String username) {
        state.evict(failKey(scope, username));
        state.evict(lockKey(scope, username));
    }

    private String failKey(String scope, String username) {
        return "login:fail:" + key(scope, username);
    }

    private String lockKey(String scope, String username) {
        return "login:lock:" + key(scope, username);
    }

    private String key(String scope, String username) {
        return (scope == null ? "" : scope) + ":" + username;
    }
}
