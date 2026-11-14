package com.fssc.image.paging;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.framework.paging.CountSnapshot;
import com.fssc.framework.paging.ListPaging;
import com.fssc.framework.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 影像域分页工具的回归防线。
 *
 * <p>钉住三件最容易悄悄退化、且线上只会表现成「台账变慢 / 页码不对」的事：
 * ① COUNT 必须关掉由分页插件自动执行；② 同一筛选签名 60 秒内只数一次；
 * ③ 签名必须带视图名/租户/筛选值，否则不同筛选共用总数＝画错页码。</p>
 */
class ImagePageKitTest {

    @AfterEach
    void tearDown() {
        CountSnapshot.clear();
        TenantContext.clear();
    }

    @Test
    void 分页对象必须关闭自动COUNT() {
        Page<Object> p = ImagePageKit.page(3, 20);
        assertFalse(p.searchCount(), "不关 searchCount 就等于没做优化：每页仍会跑一次全表 COUNT");
        assertEquals(3, p.getCurrent());
        assertEquals(20, p.getSize());
    }

    @Test
    void 页大小必须钳位防止拉全表() {
        assertEquals(ListPaging.DEFAULT_SIZE, ImagePageKit.clampSize(0));
        assertEquals(ListPaging.DEFAULT_SIZE, ImagePageKit.clampSize(-1));
        assertEquals(ListPaging.MAX_SIZE, ImagePageKit.clampSize(1_000_000));
        assertEquals(20, ImagePageKit.clampSize(20));
    }

    @Test
    void 同一筛选签名在有效期内只数一次() {
        TenantContext.set(1L);
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            long total = ImagePageKit.total("img_batch/task", () -> {
                calls.incrementAndGet();
                return 500_002L;
            }, null, null, null, null);
            assertEquals(500_002L, total);
        }
        assertEquals(1, calls.get(), "翻页/刷新/并发打开同一视图都应复用同一份快照");
    }

    @Test
    void 视图名与筛选值必须进入签名() {
        TenantContext.set(1L);
        String base = ImagePageKit.countKey("img_batch/task", null, null, null, null);
        String otherView = ImagePageKit.countKey("img_review/page", null, null, null, null);
        String otherFilter = ImagePageKit.countKey("img_batch/task", "CLAIM", null, null, null);

        assertNotEquals(base, otherView, "不同视图共用一份总数会让页码按别人的表画");
        assertNotEquals(base, otherFilter, "不同筛选共用一份总数＝筛选后页码仍然画全量");
        assertTrue(base.contains("1"), "签名必须带租户");
    }

    @Test
    void 空串与null归一为同一个签名() {
        TenantContext.set(1L);
        assertEquals(ImagePageKit.countKey("v", null, ""),
                ImagePageKit.countKey("v", "", null));
    }

    @Test
    void 不同租户签名必须不同() {
        TenantContext.set(1L);
        String a = ImagePageKit.countKey("img_batch/task");
        TenantContext.set(2L);
        String b = ImagePageKit.countKey("img_batch/task");
        assertNotEquals(a, b, "跨租户复用总数＝公司账号看到集团总量、点页码翻空");
    }
}
