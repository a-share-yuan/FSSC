package com.fssc.connector.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接域弹性治理（形态 B · E 步：限流 / 熔断 / 超时分层）。
 *
 * <p>按<b>能力域</b>（category）维护独立的熔断与限流状态，避免单通道故障拖垮整体、或某通道被打爆：</p>
 * <ul>
 *   <li><b>熔断</b>：连续失败达到阈值即打开，打开窗口内快速失败；窗口过后自动放行试探，成功即复位；</li>
 *   <li><b>限流</b>：每秒放行上限（令牌窗口），0 表示不限流；</li>
 *   <li><b>超时</b>：仍由底层网关按 {@code extra_config.timeout}（默认 15s）执行，本类不改变既有超时语义。</li>
 * </ul>
 *
 * <p><b>默认关闭</b>（{@code fssc.connector.resilience.enabled=false}）：{@link #allow(String)} 恒真、
 * 记录方法为空操作，与原先行为完全一致，零回归。开启后仅作用于连接域入口
 * {@code DefaultConnectorFacade}，不改动任何既有网关逻辑。</p>
 */
@Slf4j
@Component
public class ResilienceGuard {

    private final boolean enabled;
    private final int failureThreshold;
    private final long openMillis;
    private final int ratePerSecond;

    private final Map<String, Breaker> breakers = new ConcurrentHashMap<>();

    public ResilienceGuard(
            @Value("${fssc.connector.resilience.enabled:false}") boolean enabled,
            @Value("${fssc.connector.resilience.failure-threshold:5}") int failureThreshold,
            @Value("${fssc.connector.resilience.open-seconds:30}") long openSeconds,
            @Value("${fssc.connector.resilience.rate-per-second:0}") int ratePerSecond) {
        this.enabled = enabled;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(1, openSeconds) * 1000L;
        this.ratePerSecond = Math.max(0, ratePerSecond);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否放行本次调用；未启用时恒为 true。 */
    public boolean allow(String capability) {
        if (!enabled) {
            return true;
        }
        return breaker(capability).allow(System.currentTimeMillis(), ratePerSecond);
    }

    public void recordSuccess(String capability) {
        if (!enabled) {
            return;
        }
        breaker(capability).onSuccess();
    }

    public void recordFailure(String capability) {
        if (!enabled) {
            return;
        }
        breaker(capability).onFailure(System.currentTimeMillis());
    }

    private Breaker breaker(String capability) {
        return breakers.computeIfAbsent(capability == null ? "" : capability,
                k -> new Breaker(failureThreshold, openMillis));
    }

    /** 单能力域熔断/限流状态（本类内部使用）。 */
    private static final class Breaker {
        private final int failureThreshold;
        private final long openMillis;
        private int consecutiveFailures;
        private long openUntil;
        private long windowStart;
        private int windowCount;

        Breaker(int failureThreshold, long openMillis) {
            this.failureThreshold = failureThreshold;
            this.openMillis = openMillis;
        }

        synchronized boolean allow(long now, int ratePerSecond) {
            if (openUntil > now) {
                return false;
            }
            if (ratePerSecond > 0) {
                if (now - windowStart >= 1000L) {
                    windowStart = now;
                    windowCount = 0;
                }
                if (windowCount >= ratePerSecond) {
                    return false;
                }
                windowCount++;
            }
            return true;
        }

        synchronized void onSuccess() {
            consecutiveFailures = 0;
            openUntil = 0;
        }

        synchronized void onFailure(long now) {
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                openUntil = now + openMillis;
            }
        }
    }
}
