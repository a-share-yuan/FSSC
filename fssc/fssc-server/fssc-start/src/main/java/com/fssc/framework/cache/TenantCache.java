package com.fssc.framework.cache;

import com.fssc.framework.spi.DistributedState;
import com.fssc.framework.spi.LocalDistributedState;
import com.fssc.framework.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 主数据缓存门面（P1-3 主数据缓存分层），内部委托 {@link DistributedState} 可插拔实现：
 * 单实例（默认）走进程内 {@link LocalDistributedState}（原 ConcurrentHashMap TTL 缓存语义原样收编），
 * 多实例部署置 {@code fssc.redis.enabled=true} 切 Redis 集中式缓存（GET/SET EX + SCAN 前缀失效）。
 *
 * <p>key 约定：{@code tenantId + ":md:" + 业务域}（{@link #mdKey}/{@link #mdPrefix} 统一拼装），
 * 业务域可带子后缀（如 {@code expenseStandard:5:1}），失效按域前缀清（同域 key 全清）。</p>
 *
 * <p>行为不变项：{@link #get} 命中且未过期直接返回，过期/未命中回源 loader 并重填（并发下允许
 * 重复加载，最终一致即可）；缓存值按"只读"约定使用：仅缓存查询返回的 List/对象，
 * 调用方不得原地修改返回值。</p>
 *
 * <p>Spring 启动后由 {@link TenantCacheBinder} 注入条件化实现（bind）；未绑定时
 * 使用进程内兜底实现，脱离容器的静态调用方行为与旧版完全一致。</p>
 */
public final class TenantCache {

    private static final Logger log = LoggerFactory.getLogger(TenantCache.class);

    /** 默认 TTL：5 分钟（与 CacheUtil 分层口径一致） */
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    /** 委托的分布式状态实现：默认进程内兜底（语义与原静态 map 相同），容器就绪后 bind 替换 */
    private static volatile DistributedState state = new LocalDistributedState();

    private TenantCache() {
    }

    /** 绑定 Spring 条件化实现（{@code fssc.redis.enabled} 决定 Local/Redis），仅框架内部调用 */
    static void bind(DistributedState s) {
        if (s != null) {
            state = s;
        }
    }

    /** 当前租户某主数据域的缓存 key（可加 ":子键" 后缀细化） */
    public static String mdKey(String domain) {
        return TenantContext.getTenantId() + ":md:" + domain;
    }

    /** 当前租户某主数据域的失效前缀（该域全部 key，含子后缀） */
    public static String mdPrefix(String domain) {
        return TenantContext.getTenantId() + ":md:" + domain;
    }

    /** 读取缓存，未命中/过期则调用 loader 回源并缓存（默认 TTL） */
    public static <T> T get(String key, Supplier<T> loader) {
        return get(key, loader, DEFAULT_TTL_MS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T get(String key, Supplier<T> loader, long ttlMs) {
        // 本地实现直接返回原对象引用（与旧版一致）；Redis 实现反序列化回同型对象
        return (T) state.getOrLoad(key, (Class) Object.class, loader, Duration.ofMillis(ttlMs));
    }

    /** 精确失效 */
    public static void evict(String key) {
        state.evict(key);
    }

    /** 前缀失效（如按 mdPrefix("expenseStandard") 清该域全部子键） */
    public static void evictPrefix(String prefix) {
        state.evictPrefix(prefix);
    }

    /** 清空全部（运维兜底；仅进程内实现支持，Redis 模式为避免全命名空间误删不放开） */
    public static void clear() {
        if (state instanceof LocalDistributedState lds) {
            lds.clearCache();
        } else {
            log.warn("[TenantCache] 分布式模式下不支持 clear 全量，请用 evictPrefix 按域失效");
        }
    }

    /** 当前缓存条目数（仅进程内实现可观测；Redis 模式返回 -1） */
    public static int size() {
        return state instanceof LocalDistributedState lds ? lds.cacheSize() : -1;
    }
}
