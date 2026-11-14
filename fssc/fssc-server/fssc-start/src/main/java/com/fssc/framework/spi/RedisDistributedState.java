package com.fssc.framework.spi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Redis 实现（{@code fssc.redis.enabled=true}，多实例部署模式）：StringRedisTemplate + Jackson 序列化。
 *
 * <ul>
 *   <li>getOrLoad：本地 double-check（同进程 TTL 内免 Redis 往返）→ GET 命中反序列化直返 →
 *       未命中回源 loader → SET key json EX ttl。Redis 故障 fail-open（直接回源，不阻断业务）。</li>
 *   <li>evictPrefix：SCAN MATCH prefix*（COUNT 200，游标分批，明确不用 KEYS 全量阻塞）+ 批量 DEL。</li>
 *   <li>tryAcquireRate：INCR + 首次 EXPIRE 设窗（计数超限返回 false）；Redis 故障放行（fail-open）。</li>
 *   <li>tryLock：SET key token NX EX ttl（token=实例 UUID）；unlock 用 Lua compare-and-delete
 *       （只删自己持有的锁，锁在 Redis 模式不可重入，与 GET_LOCK 同连接重入语义的差异已在此注释声明）。</li>
 * </ul>
 *
 * <p>序列化说明：缓存值多态（List&lt;实体&gt; 等），启用 Jackson DefaultTyping 内嵌类名，
 * 并用 PolymorphicTypeValidator 白名单限制到本项目与 JDK 安全包，避免任意类反序列化。</p>
 */
@Component
@ConditionalOnProperty(name = "fssc.redis.enabled", havingValue = "true")
public class RedisDistributedState implements DistributedState {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedState.class);

    /** 全局命名空间前缀：本应用所有分布式状态 key 统一挂 {@code fssc:} 之下，防止与共享 Redis 上其他应用冲突 */
    private static final String NS = "fssc:";
    /** 本地 double-check 缓存条目过期映射（仅减少 Redis 往返，最终以 Redis TTL 为准） */
    private static final int LOCAL_MAX_ENTRIES = 500;
    /** SCAN 每游标批次大小 */
    private static final int SCAN_COUNT = 200;
    /** 批量 DEL 每批 key 数 */
    private static final int DEL_BATCH = 500;

    /** 解锁脚本：compare-and-delete，值等于本实例 token 才删（防误删他人续上的锁） */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    /** 实例唯一标识：作为锁 value，unlock 时比对 */
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    /** 本地 double-check：key → (值, 过期时间) */
    private final Map<String, Local> localCache = new ConcurrentHashMap<>();

    public RedisDistributedState(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = buildMapper();
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 多态缓存值（如 List<实体>）需内嵌类型信息；白名单限定本包与 JDK 常用安全类型
        m.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.fssc.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.math.")
                        .allowIfSubType("org.springframework.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        try {
            m.findAndRegisterModules(); // 有 jsr310 等模块则注册，没有也不影响基本类型
        } catch (Exception ignore) {
            // 模块发现失败不致命：仅影响 LocalDateTime 等类型的读写，运行期 fail-open 回源
        }
        return m;
    }

    // 缓存

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        long now = System.currentTimeMillis();
        Local l = localCache.get(key);
        if (l != null && l.expireAt > now) {
            return (T) l.value; // 本地 double-check 命中
        }
        String rk = NS + key;
        try {
            String json = redis.opsForValue().get(rk);
            if (json != null) {
                T v = (T) mapper.readValue(json, Object.class);
                putLocal(key, v, ttl);
                return v;
            }
        } catch (Exception e) {
            // Redis 读/反序列化失败：fail-open 走回源，不阻断业务
            log.warn("[DistributedState] Redis GET 失败，降级回源 key={}", key, e);
        }
        T value = loader.get();
        if (value != null) {
            putLocal(key, value, ttl);
            try {
                redis.opsForValue().set(rk, mapper.writeValueAsString(value), ttl);
            } catch (Exception e) {
                log.warn("[DistributedState] Redis SET 失败，仅本地缓存 key={}", key, e);
            }
        }
        return value;
    }

    @Override
    public void evict(String key) {
        localCache.remove(key);
        try {
            redis.delete(NS + key);
        } catch (Exception e) {
            log.warn("[DistributedState] Redis DEL 失败 key={}", key, e);
        }
    }

    @Override
    public void evictPrefix(String prefix) {
        localCache.keySet().removeIf(k -> k.startsWith(prefix));
        try {
            // SCAN 游标分批删除（明确不用 KEYS）：RedisTemplate.scan 底层即 SCAN MATCH
            List<String> batch = new ArrayList<>(DEL_BATCH);
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                    .match(NS + prefix + "*").count(SCAN_COUNT).build())) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= DEL_BATCH) {
                        redis.delete(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
        } catch (Exception e) {
            log.warn("[DistributedState] Redis SCAN+DEL 失败 prefix={}", prefix, e);
        }
    }

    // 限流 / 失败计数

    @Override
    public void mark(String key, Duration ttl) {
        try {
            redis.opsForValue().set(NS + key, "1", ttl);
        } catch (Exception e) {
            // 与缓存/限流相反：标记写入（如踢下线）静默失败等于安全策略失效，必须让调用方感知
            log.error("[DistributedState] Redis 标记写入失败 key={}", key, e);
            throw new IllegalStateException("分布式状态写入失败（标记未生效）: " + key, e);
        }
    }

    @Override
    public boolean marked(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(NS + key));
        } catch (Exception e) {
            // fail-closed：存储不可用时视作"已标记"。黑名单是安全设施——宁可短暂拒绝全部请求，
            // 也不能放行一个已被强制下线的用户继续操作资金。
            log.error("[DistributedState] Redis 标记读取失败，fail-closed 视作已标记 key={}", key, e);
            return true;
        }
    }

    @Override
    public boolean tryAcquireRate(String key, int maxCount, Duration window) {
        String rk = NS + key;
        try {
            Long count = redis.opsForValue().increment(rk);
            if (count == null) {
                return true; // 异常计数不误伤，放行
            }
            if (count == 1L) {
                redis.expire(rk, window); // 首次设窗（进程崩溃致 INCR 后未 EXPIRE 的窗口极小，可接受）
            }
            return count <= maxCount;
        } catch (Exception e) {
            log.warn("[DistributedState] Redis 限流计数失败，放行 key={}", key, e);
            return true; // fail-open：限流组件故障不误锁用户
        }
    }

    // 调度锁

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(NS + lockKey, INSTANCE_ID, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void unlock(String lockKey) {
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(NS + lockKey), INSTANCE_ID);
        } catch (Exception e) {
            log.warn("[DistributedState] Redis 解锁失败 lock={}（等待 TTL 自动过期）", lockKey, e);
        }
    }

    // 内部

    private void putLocal(String key, Object value, Duration ttl) {
        if (localCache.size() >= LOCAL_MAX_ENTRIES) {
            localCache.entrySet().removeIf(en -> en.getValue().expireAt <= System.currentTimeMillis());
            if (localCache.size() >= LOCAL_MAX_ENTRIES) {
                localCache.keySet().stream().limit(LOCAL_MAX_ENTRIES / 2L).toList()
                        .forEach(localCache::remove);
            }
        }
        localCache.put(key, new Local(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    private record Local(Object value, long expireAt) {
    }
}
