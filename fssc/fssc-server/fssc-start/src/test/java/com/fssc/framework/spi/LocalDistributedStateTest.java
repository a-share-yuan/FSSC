package com.fssc.framework.spi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalDistributedState 单测：限流窗口 / 锁直通与重入 / 缓存 TTL 与前缀失效。
 * 纯逻辑测试，不启动 Spring 上下文（无 JdbcTemplate 构造 → 锁为无锁直通语义）。
 */
class LocalDistributedStateTest {

    private LocalDistributedState state;

    @BeforeEach
    void setUp() {
        state = new LocalDistributedState();
    }

    // ==================== 缓存 ====================

    @Test
    void getOrLoad_未过期命中不重复回源() {
        AtomicInteger loads = new AtomicInteger();
        Object first = state.getOrLoad("1:md:tree", Object.class, loads::incrementAndGet, Duration.ofMinutes(5));
        Object second = state.getOrLoad("1:md:tree", Object.class, loads::incrementAndGet, Duration.ofMinutes(5));
        assertEquals(1, first);
        assertEquals(1, loads.get());
        assertSame(first, second); // 本地实现返回同一对象引用（与旧 TenantCache 一致）
    }

    @Test
    void getOrLoad_TTL过期后重新回源() throws InterruptedException {
        AtomicInteger loads = new AtomicInteger();
        state.getOrLoad("k", Object.class, loads::incrementAndGet, Duration.ofMillis(40));
        state.getOrLoad("k", Object.class, loads::incrementAndGet, Duration.ofMillis(40));
        assertEquals(1, loads.get());
        Thread.sleep(120);
        state.getOrLoad("k", Object.class, loads::incrementAndGet, Duration.ofMillis(40));
        assertEquals(2, loads.get());
    }

    @Test
    void evict精确失效_evictPrefix按前缀清子键() {
        state.getOrLoad("1:md:std:5:1", Object.class, () -> "a", Duration.ofMinutes(5));
        state.getOrLoad("1:md:std:5:2", Object.class, () -> "b", Duration.ofMinutes(5));
        state.getOrLoad("1:md:other", Object.class, () -> "c", Duration.ofMinutes(5));

        state.evict("1:md:std:5:1");
        AtomicInteger reload = new AtomicInteger();
        assertEquals("c", state.getOrLoad("1:md:other", Object.class, reload::incrementAndGet, Duration.ofMinutes(5)));
        assertEquals(0, reload.get(), "other 不应被精确失效波及");

        state.evictPrefix("1:md:std");
        assertEquals(2, state.getOrLoad("1:md:std:5:2", Object.class, () -> 2, Duration.ofMinutes(5)));
    }

    // ==================== 限流窗口 ====================

    @Test
    void tryAcquireRate_窗口内超限拒绝_窗口过期重新放行() throws InterruptedException {
        Duration window = Duration.ofMillis(150);
        assertTrue(state.tryAcquireRate("ip:1.2.3.4", 3, window));
        assertTrue(state.tryAcquireRate("ip:1.2.3.4", 3, window));
        assertTrue(state.tryAcquireRate("ip:1.2.3.4", 3, window));
        assertFalse(state.tryAcquireRate("ip:1.2.3.4", 3, window), "第4次应超限拒绝");
        assertFalse(state.tryAcquireRate("ip:1.2.3.4", 3, window), "窗口内持续拒绝");

        Thread.sleep(250);
        assertTrue(state.tryAcquireRate("ip:1.2.3.4", 3, window), "窗口过期后重新计数放行");
    }

    @Test
    void tryAcquireRate_不同key窗口独立() {
        assertTrue(state.tryAcquireRate("a:t1", 1, Duration.ofMinutes(1)));
        assertFalse(state.tryAcquireRate("a:t1", 1, Duration.ofMinutes(1)));
        assertTrue(state.tryAcquireRate("b:t2", 1, Duration.ofMinutes(1)), "其他 key 不受影响");
    }

    @Test
    void tryAcquireRate_失败锁定口径_第5次失败返回false触发锁定() {
        // 与 LoginGuardService 一致的用法：maxCount = 阈值-1，第 MAX 次返回 false
        Duration win = Duration.ofMinutes(5);
        for (int i = 1; i < 5; i++) {
            assertTrue(state.tryAcquireRate("fail:x", 4, win), "第" + i + "次失败应放行");
        }
        assertFalse(state.tryAcquireRate("fail:x", 4, win), "第5次失败触发锁定");
    }

    // ==================== 锁 ====================

    @Test
    void tryLock_无JdbcTemplate时直通true且可重入_unlock无异常() {
        assertTrue(state.tryLock("fssc_job_DemoJob", Duration.ofHours(2)));
        // 直通/GET_LOCK 同连接重入语义：同 key 再次 tryLock 仍为 true（不自锁死）
        assertTrue(state.tryLock("fssc_job_DemoJob", Duration.ofHours(2)));
        state.unlock("fssc_job_DemoJob");
        state.unlock("fssc_job_DemoJob"); // 未持有/重复释放不抛异常
        assertTrue(state.tryLock("fssc_job_DemoJob", Duration.ofHours(2)), "unlock 后仍可获取");
    }
}
