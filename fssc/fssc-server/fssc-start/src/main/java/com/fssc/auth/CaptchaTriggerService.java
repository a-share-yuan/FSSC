// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import com.fssc.framework.security.LoginGuardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码触发判定（C-3）：失败阈值触发，而不是无条件强校验。
 *
 * <p>为什么必须按阈值触发：本仓有大量自动化脚本/冒烟清单（01 册 S1-01~S1-25）用真实业务账号
 * 程序化登录，验证码若无条件必拦会打断每日开市。故规则为 ——
 * 同一身份（租户 {@code tenantCode + 账号}、平台 {@code PLATFORM + 账号}）连续<b>凭据</b>失败
 * 累计达到 {@code fssc.login.captcha-fail-threshold}（缺省 3）之后，后续登录才必须携带验证码；
 * 登录成功即清零。</p>
 *
 * <p><b>与既有防刷逻辑的关系（不打架）</b>：身份键、统计窗口（5 分钟）、失败事件点
 * 全部沿用 {@link LoginGuardService}（{@code AuthService} 里 {@code recordFailure} /
 * {@code resetOnSuccess} 的同两处调用点），本组件只是那份失败计数的<b>镜像投影</b>，
 * 不替代它、也不与它竞争：
 * 阈值 3（本组件）&lt; 锁定阈值 5（{@code LoginGuardService.MAX_FAIL_COUNT}）——
 * 第 3、4 次失败要求验证码，第 5 次仍由既有逻辑临时锁定 15 分钟，锁定期内先由
 * {@code checkLock} 拦截（{@code AuthService} 里 ② 早于 ③），顺序不变。
 * 之所以是镜像而不是直读它的计数：{@code DistributedState} 只有
 * {@code tryAcquireRate}（读即自增）没有"只读取当前值"的接口，直接借读会把用户的失败计数提前推高。</p>
 *
 * <p><b>存储介质</b>：进程内 Map + TTL，与 {@link CaptchaService} 同级（BCP 同款规则）；
 * 多实例部署需与验证码缓存一并迁到共享态。</p>
 */
@Slf4j
@Service
public class CaptchaTriggerService {

    /** 失败计数统计窗口：与 {@code LoginGuardService.FAIL_WINDOW}（5 分钟）对齐，窗口起算点同为首个失败。 */
    private static final long FAIL_WINDOW_MILLIS = 300_000L;
    /** 身份条目上限：超量先清过期再淘汰最早到期的半数（防内存无界增长）。 */
    private static final int MAX_IDENTITIES = 5000;

    /** scope:username → 当前窗口内的连续凭据失败次数 */
    private final Map<String, Streak> streaks = new ConcurrentHashMap<>();

    private final LoginGuardService loginGuard;
    /** 触发验证码所需的连续失败次数（配置缺失取 3） */
    private final int failThreshold;

    public CaptchaTriggerService(LoginGuardService loginGuard,
                                 @Value("${fssc.login.captcha-fail-threshold:3}") int failThreshold) {
        this.loginGuard = loginGuard;
        this.failThreshold = failThreshold > 0 ? failThreshold : 3;
    }

    /** 总开关（{@code fssc.login.captcha-enabled}）：false ⇒ 登录链完全不介入验证码。 */
    public boolean enabled() {
        return loginGuard.isCaptchaEnabled();
    }

    /** 生效的失败阈值（配置缺失时为 3）。 */
    public int failThreshold() {
        return failThreshold;
    }

    /**
     * 本次登录是否必须携带验证码：总开关开启 且 该身份在本窗口内已连续失败达阈值。
     * 纯判定，不改任何计数（不"读即自增"）。
     */
    public boolean requiresCaptcha(String scope, String username) {
        if (!loginGuard.isCaptchaEnabled()) {
            return false;
        }
        Streak s = streaks.get(key(scope, username));
        return s != null && !s.expired() && s.count >= failThreshold;
    }

    /**
     * 记录一次凭据失败（与 {@code LoginGuardService.recordFailure} 同一事件点调用）。
     *
     * @return true = 该身份已达阈值，<b>本次失败响应就应带出「需要验证码」标志</b>
     *         （开关关闭时恒 false，错误码/文案维持现状，冒烟脚本零感知）
     */
    public boolean recordFailure(String scope, String username) {
        String k = key(scope, username);
        long now = System.currentTimeMillis();
        int count = streaks.compute(k, (id, old) ->
                (old == null || old.expired()) ? new Streak(1, now + FAIL_WINDOW_MILLIS)
                        : new Streak(old.count + 1, old.expiresAt)).count;
        if (streaks.size() > MAX_IDENTITIES) {
            trimIfOverCapacity();
        }
        if (log.isDebugEnabled() && loginGuard.isCaptchaEnabled() && count == failThreshold) {
            // 只在跨过阈值那一拍留一行 DEBUG：阈值之上每次登录都命中，别刷日志
            log.debug("[验证码] 身份 {} 连续失败达阈值 {}，后续登录需图形验证码", k, failThreshold);
        }
        return loginGuard.isCaptchaEnabled() && count >= failThreshold;
    }

    /** 登录成功清零该身份的失败计数（与 {@code LoginGuardService.resetOnSuccess} 同一事件点调用）。 */
    public void resetOnSuccess(String scope, String username) {
        streaks.remove(key(scope, username));
    }

    private String key(String scope, String username) {
        return (scope == null ? "" : scope) + ":" + username;
    }

    private void trimIfOverCapacity() {
        streaks.entrySet().removeIf(en -> en.getValue().expired());
        if (streaks.size() <= MAX_IDENTITIES) {
            return;
        }
        streaks.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue().expiresAt, b.getValue().expiresAt))
                .limit(streaks.size() / 2L)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(streaks::remove);
        if (log.isDebugEnabled()) {
            log.debug("[验证码] 失败计数条目超上限，已清理至 {} 条", streaks.size());
        }
    }

    /** 某身份当前窗口内的连续失败计数（不可变条目，compute 整体替换保证原子）。 */
    private static final class Streak {
        final int count;
        final long expiresAt;

        Streak(int count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
