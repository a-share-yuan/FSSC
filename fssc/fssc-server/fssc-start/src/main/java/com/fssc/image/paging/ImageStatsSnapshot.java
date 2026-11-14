package com.fssc.image.paging;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 影像统计端点的 60 秒结果快照（{@code stats/by-biz-type} 专用）。
 *
 * <p><b>为什么单独一个</b>：{@code CountSnapshot} 只缓存 long 总数，而按业务类型汇总是
 * {@code List<Map<String,Object>>}。2026-11-14 压测实测该查询在 50 万行
 * {@code img_batch} 上平均 <b>537ms</b>（全表 GROUP BY，已走覆盖索引、无索引可救），
 * 且它是报表服务的远程取数面，重复打开报表会反复付这笔开销。</p>
 *
 * <p><b>语义</b>：与 {@code CountSnapshot} 一致 —— 同一租户 + 同一数据权限下 60 秒内只算一次，
 * {@link ConcurrentHashMap#compute} 防击穿（过期瞬间 N 个并发不会同时打出 N 条 537ms 的
 * GROUP BY）。统计数字最多陈旧 60 秒，仅用于展示。</p>
 *
 * <p><b>key 由调用方拼装</b>，必须带租户与 {@code CountSnapshot.scopeFingerprint()}，
 * 否则不同权限的账号会拿到别人的汇总。</p>
 */
public final class ImageStatsSnapshot {

    private static final long TTL_MS = 60_000L;
    private static final int MAX_ENTRIES = 256;

    private static final ConcurrentHashMap<String, Slot> CACHE = new ConcurrentHashMap<>();

    private ImageStatsSnapshot() {
    }

    public static List<Map<String, Object>> of(String key, Supplier<List<Map<String, Object>>> loader) {
        evictIfCrowded();
        long now = System.currentTimeMillis();
        Slot slot = CACHE.compute(key, (k, cur) -> {
            if (cur != null && now - cur.at() < TTL_MS) {
                return cur;
            }
            List<Map<String, Object>> loaded = loader.get();
            return new Slot(now, loaded == null ? List.of() : loaded);
        });
        return slot.rows();
    }

    /** 清空（测试用；生产靠 TTL 自然过期）。 */
    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    private static void evictIfCrowded() {
        if (CACHE.size() <= MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(e -> now - e.getValue().at() >= TTL_MS);
        if (CACHE.size() > MAX_ENTRIES) {
            CACHE.clear();
        }
    }

    private record Slot(long at, List<Map<String, Object>> rows) {
    }
}
