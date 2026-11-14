package com.fssc.image.paging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 影像统计快照：同一租户 60 秒内只跑一次全表 GROUP BY。 */
class ImageStatsSnapshotTest {

    @AfterEach
    void tearDown() {
        ImageStatsSnapshot.clear();
    }

    @Test
    void 同一key只算一次() {
        AtomicInteger calls = new AtomicInteger();
        List<Map<String, Object>> rows = List.of(Map.of("bizType", "CLAIM", "batchCnt", 500002L));
        for (int i = 0; i < 3; i++) {
            assertEquals(rows, ImageStatsSnapshot.of("img_batch/stats|1|-", () -> {
                calls.incrementAndGet();
                return rows;
            }));
        }
        assertEquals(1, calls.get());
    }

    @Test
    void 不同key互不串味() {
        ImageStatsSnapshot.of("a", () -> List.of(Map.of("n", 1)));
        List<Map<String, Object>> b = ImageStatsSnapshot.of("b", () -> List.of(Map.of("n", 2)));
        assertEquals(2, b.get(0).get("n"));
    }

    @Test
    void loader返回null时给空列表而不是NPE() {
        assertTrue(ImageStatsSnapshot.of("k", () -> null).isEmpty());
    }
}
