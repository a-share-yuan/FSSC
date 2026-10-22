package com.fssc.framework.spi;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式状态抽象（多实例部署前置项）：统一收编三类进程内状态——
 * 主数据 TTL 缓存（原 TenantCache 的 map）、登录防刷计数（原 LoginGuardService 的 IP 计数 map）、
 * 调度互斥锁（原 JobHeartbeatService 的 MySQL GET_LOCK）。
 *
 * <p>实现按配置 {@code fssc.redis.enabled} 切换（@ConditionalOnProperty）：</p>
 * <ul>
 *   <li>{@link LocalDistributedState}（false 或缺省）：进程内实现，行为与改造前完全一致
 *       （锁走 MySQL GET_LOCK，与单机现状相同）；</li>
 *   <li>{@link RedisDistributedState}（true）：StringRedisTemplate + Jackson 序列化，
 *       缓存 GET/SET EX + 本地 double-check，前缀失效 SCAN+DEL，限流 INCR+EXPIRE，
 *       锁 SETNX+EX + Lua compare-and-delete 释放。</li>
 * </ul>
 *
 * <p>约定：key 由调用方保证全局唯一且自带命名空间（如 {@code tenantId:md:域}、
 * {@code fssc:login:ip:...}）；实现不得改写 key 语义（Redis 实现仅做整体前缀命名空间）。</p>
 */
public interface DistributedState {

    /**
     * 缓存语义：命中直接返回；未命中/过期调用 loader 回源并按 ttl 写入。
     * 并发下允许重复加载（最终一致即可，与原 TenantCache 口径相同）。
     *
     * @param key    缓存 key
     * @param type   值的类型（Redis 实现用于反序列化；本地实现直接返回原对象引用，type 仅作声明）
     * @param loader 回源加载器
     * @param ttl    存活时间
     */
    <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl);

    /** 精确失效一个 key */
    void evict(String key);

    /** 前缀失效：清掉所有以 prefix 开头的 key（如按 {@code tenantId:md:域} 清该域全部子键） */
    void evictPrefix(String prefix);

    /**
     * 限流/失败计数语义：对 key 原子递增，窗口从该窗口内第一次递增起算（首次设 TTL）。
     *
     * @return 递增后计数 ≤ maxCount 返回 true（放行）；超限返回 false（拒绝），不抛异常
     */
    boolean tryAcquireRate(String key, int maxCount, Duration window);

    /**
     * 标记语义：写入一个带 TTL 的存在标记（如 JWT 令牌黑名单）。已存在则刷新 TTL。
     * <p>
     * 与缓存语义的区别：只有"存在/不存在"两种状态，不承载值、不需要回源 loader。
     *
     * @param key 标记 key（调用方保证全局唯一并自带命名空间）
     * @param ttl 存活时间；到期自动清除，不长期占用存储
     */
    void mark(String key, Duration ttl);

    /**
     * 标记是否存在（未过期返回 true）。
     * <p>
     * <b>失败语义与缓存/限流不同，本方法 fail-closed</b>：存储不可用时返回 true（视作"已标记"）
     * 并告警。令牌黑名单是安全设施而非可用性设施——存储故障时应拒绝访问，而不是放行一个已被
     * 强制下线的用户去操作资金。其他三个语义（缓存、限流、锁）仍按各自注释 fail-open。
     */
    boolean marked(String key);

    /**
     * 互斥锁：获取成功返回 true（调用方必须 finally unlock）；已被占用立即返回 false（不阻塞）。
     *
     * @param ttl 锁最长持有时间（实例崩溃兜底，到期自动释放；本地 GET_LOCK 模式忽略该参数）
     */
    boolean tryLock(String lockKey, Duration ttl);

    /** 释放锁（Redis 实现为 Lua compare-and-delete，只释放自己持有的；未持有时为无操作） */
    void unlock(String lockKey);
}
