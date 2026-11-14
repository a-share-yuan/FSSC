package com.fssc.framework.paging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 总数快照的回归防线。
 *
 * <p>这个类存在的意义只有一个：<b>一份签名在有效期内只数一次</b>。
 * 它一旦失效，表现不是报错而是「每翻一页多一次 519ms 的 COUNT」——
 * 线上只会觉得"台账慢"，很难反查到是快照没命中，所以必须有测试钉住：
 * <ul>
 *   <li>同签名两次调用 ⇒ loader 只执行一次（这是全部价值所在）；</li>
 *   <li>不同签名互不干扰（否则会把 A 公司的总数给 B 公司）；</li>
 *   <li>loader 返回 null ⇒ 按 0 处理（mapper 异常兜底，不让 NPE 冒到接口层）。</li>
 * </ul>
 */
class CountSnapshotTest {

    @AfterEach
    void tearDown() {
        CountSnapshot.clear();
    }

    @Test
    void 同签名在有效期内只执行一次loader() {
        AtomicInteger calls = new AtomicInteger();
        String key = "test|same|key";

        long first = CountSnapshot.of(key, () -> {
            calls.incrementAndGet();
            return 1024002L;
        });
        long second = CountSnapshot.of(key, () -> {
            calls.incrementAndGet();
            return 1024002L;
        });
        long third = CountSnapshot.of(key, () -> {
            calls.incrementAndGet();
            return 1024002L;
        });

        assertEquals(1024002L, first);
        assertEquals(1024002L, second);
        assertEquals(1024002L, third);
        assertEquals(1, calls.get(), "同签名重复调用不应重复计数，否则每翻一页都白付一次 COUNT");
    }

    @Test
    void 不同签名各自独立() {
        AtomicInteger calls = new AtomicInteger();
        long a = CountSnapshot.of("test|tenant-1|COMPANY-3", () -> {
            calls.incrementAndGet();
            return 653L;
        });
        long b = CountSnapshot.of("test|tenant-1|TENANT", () -> {
            calls.incrementAndGet();
            return 1024002L;
        });

        assertEquals(653L, a);
        assertEquals(1024002L, b);
        assertEquals(2, calls.get(), "权限指纹不同必须分开计数，否则公司账号会拿到集团总数");
    }

    @Test
    void loader返回null时按0处理() {
        // MyBatis-Plus 的 selectCount 正常返回 0 而不是 null；此处只防封装层被换成别的实现
        assertEquals(0L, CountSnapshot.of("test|null", () -> (Long) null));
    }

    @Test
    void clear之后重新计数() {
        AtomicInteger calls = new AtomicInteger();
        CountSnapshot.of("test|clear", () -> {
            calls.incrementAndGet();
            return 1L;
        });
        CountSnapshot.clear();
        CountSnapshot.of("test|clear", () -> {
            calls.incrementAndGet();
            return 2L;
        });
        assertEquals(2, calls.get());
    }

    @Test
    void 无数据权限载体时指纹为短横线() {
        // 非 @DataScope 方法（如内部任务）不该因为指纹缺失而与有权限的请求共用快照
        assertEquals("-", CountSnapshot.scopeFingerprint());
    }

    @Test
    void 条目数不会无界增长() {
        for (int i = 0; i < 1200; i++) {
            final long v = i;
            CountSnapshot.of("test|bulk|" + i, () -> v);
        }
        assertTrue(CountSnapshot.size() <= 1200, "超出上限后应清理过期项，实际 " + CountSnapshot.size());
    }
}
