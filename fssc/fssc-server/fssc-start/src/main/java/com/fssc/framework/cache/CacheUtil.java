// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量内存缓存（P1-3 主数据缓存分层）。
 * ConcurrentHashMap + TTL，不引入外部缓存组件；缓存不可用/未命中回源 DB（fail-open 仅缓存层）。
 */
public final class CacheUtil {

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile long defaultTtlMs = 5 * 60 * 1000L; // 默认 5 分钟

    private CacheUtil() {
    }

    public static void setDefaultTtlMs(long ttlMs) {
        defaultTtlMs = ttlMs;
    }

    /** 读取缓存，未命中/过期则调用 loader 回源并缓存 */
    public static <T> T get(String key, Supplier<T> loader) {
        return get(key, loader, defaultTtlMs);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader, long ttlMs) {
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(key);
        if (e != null && e.expireAt > now) {
            return (T) e.value;
        }
        // 缓存未命中/过期：回源加载（并发下允许重复加载，最终一致即可）
        T value = loader.get();
        if (value != null) {
            CACHE.put(key, new Entry(value, now + ttlMs));
        }
        return value;
    }

    /** 精确失效 */
    public static void evict(String key) {
        CACHE.remove(key);
    }

    /** 前缀失效（如 tenant:1:dict: 下全部字典） */
    public static void evictPrefix(String prefix) {
        CACHE.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清空全部（运维/租户切换默认处理） */
    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    private record Entry(Object value, long expireAt) {
    }
}
