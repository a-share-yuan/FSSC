// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.sql.DataSource;

/**
 * 进程内实现（{@code fssc.redis.enabled=false} 或缺省）：单机部署零外部依赖，
 * 语义与改造前的 TenantCache / LoginGuardService.ipWindows / JobHeartbeatService GET_LOCK 逐一对齐。
 *
 * <ul>
 *   <li>缓存：收编原 TenantCache 的 ConcurrentHashMap TTL map——命中未过期直返，
 *       并发允许重复加载，粗容量 {@value #MAX_ENTRIES} 条超限清最旧一半；返回值按"只读"约定使用。</li>
 *   <li>限流：收编原 LoginGuardService 的 IP 计数 map——原子递增 + 窗口过期（窗口自首次递增起算，
 *       原实现为自然分钟分桶，判定口径"窗口内超 maxCount 拒绝"不变）。</li>
 *   <li>锁：走 MySQL {@code GET_LOCK(name, 0)}/{@code RELEASE_LOCK}，<b>取锁与解锁必须落在同一条连接上</b>
 *       （会话级锁，详见 {@link #tryLock} 的类内注释）；持有中若连接被服务端掐断，检测到后<b>销毁</b>该连接
 *       （{@link #discardQuietly}，不可 close 归还，否则池会把死连接再借回来）并重新竞争；
 *       无 JdbcTemplate（如纯逻辑单测）时退化为"无锁直通"恒 true。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "fssc.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalDistributedState implements DistributedState {

    private static final Logger log = LoggerFactory.getLogger(LocalDistributedState.class);

    /** 粗容量上限：超限按写入时间清最旧一半（原 TenantCache 口径，非严格 LRU） */
    private static final int MAX_ENTRIES = 500;

    private final JdbcTemplate jdbcTemplate; // 可为 null（无 DataSource 的纯逻辑环境）

    /** 缓存：key → 值条目（收编自 TenantCache.CACHE） */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    /** 限流窗口：key → 当前窗口计数（收编自 LoginGuardService.ipWindows） */
    private final Map<String, RateWindow> rates = new ConcurrentHashMap<>();

    /** 标记存储（令牌黑名单等仅"存在/不存在"的语义）：key → 过期时间戳（毫秒） */
    private final Map<String, Long> marks = new ConcurrentHashMap<>();

    /** 纯逻辑环境（静态兜底/单测）：无 JdbcTemplate，锁退化为无锁直通 */
    public LocalDistributedState() {
        this.jdbcTemplate = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LocalDistributedState(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    // ==================== 缓存 ====================

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        long now = System.currentTimeMillis();
        CacheEntry e = cache.get(key);
        if (e != null && e.expireAt > now) {
            return (T) e.value;
        }
        // 未命中/过期：回源加载（并发下允许重复加载，最终一致即可）
        T value = loader.get();
        if (value != null) {
            cache.put(key, new CacheEntry(value, now, now + ttl.toMillis()));
            evictOldestHalfIfOverCapacity();
        }
        return value;
    }

    @Override
    public void evict(String key) {
        cache.remove(key);
    }

    @Override
    public void evictPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 运维兜底：清空进程内缓存（仅本地实现有全量视图；对应原 TenantCache.clear） */
    public void clearCache() {
        cache.clear();
    }

    /** 当前缓存条目数（对应原 TenantCache.size，含未过期与已过期未清理条目） */
    public int cacheSize() {
        return cache.size();
    }

    /** 粗略容量控制：超上限按写入时间清最旧一半（原 TenantCache.evictOldestHalfIfOverCapacity） */
    private void evictOldestHalfIfOverCapacity() {
        if (cache.size() <= MAX_ENTRIES) {
            return;
        }
        cache.entrySet().stream()
                .sorted(Comparator.comparingLong(en -> en.getValue().writeAt))
                .limit(cache.size() / 2L)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(cache::remove);
    }

    // ==================== 限流 / 失败计数 ====================

    @Override
    public void mark(String key, Duration ttl) {
        if (key == null || key.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) return;
        marks.put(key, System.currentTimeMillis() + ttl.toMillis());
        trimMarksIfOverCapacity();
    }

    @Override
    public boolean marked(String key) {
        if (key == null || key.isBlank()) return false;
        Long expireAt = marks.get(key);
        if (expireAt == null) return false;
        if (System.currentTimeMillis() > expireAt) {
            marks.remove(key);
            return false;
        }
        return true;
    }

    /** 标记超容量时先清过期项，仍超限则淘汰最早到期的一半（与 clearCache 同口径，防内存泄漏） */
    private void trimMarksIfOverCapacity() {
        if (marks.size() <= MAX_ENTRIES) return;
        long now = System.currentTimeMillis();
        marks.entrySet().removeIf(e -> e.getValue() <= now);
        if (marks.size() > MAX_ENTRIES) {
            int drop = marks.size() / 2;
            marks.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByValue())
                    .limit(drop)
                    .forEach(e -> marks.remove(e.getKey()));
        }
    }

    @Override
    public boolean tryAcquireRate(String key, int maxCount, Duration window) {
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1L, window.toMillis());
        RateWindow w = rates.compute(key, (k, old) -> {
            if (old == null || old.expireAt <= now) {
                return new RateWindow(now + windowMs);
            }
            return old;
        });
        // compute 原子性保证同一 key 的窗口切换与递增不交错；对返回引用的 count++ 也在同 key 锁段内安全
        int count;
        synchronized (w) {
            count = ++w.count;
        }
        return count <= maxCount;
    }

    // ==================== 调度锁（MySQL GET_LOCK，连接亲和） ====================

    /** 持有中的调度锁：lockKey → (独占连接, 取锁线程) */
    private record HeldLock(Connection conn, long threadId) {
    }

    /**
     * 持有中的调度锁表。
     *
     * <p><b>为什么必须独占连接（本类是 2026-09-24 锁泄漏缺陷的修复点）</b>：{@code GET_LOCK} 是
     * <b>会话级</b>锁——它挂在"执行这条 SQL 的那条 MySQL 连接"上，释放也必须在<b>同一会话</b>上执行
     * {@code RELEASE_LOCK}。改造前用 {@code jdbcTemplate} 分别执行这两句，两次调用由连接池<b>各自</b>
     * 分配连接，取锁与解锁很可能落在<b>两条不同连接</b>上；此时 {@code RELEASE_LOCK} 返回 0 且静默无效，
     * 锁就遗留在池中那条空闲连接上、再也无人释放。
     *
     * <p><b>实测证据</b>：2026-09-24 深夜连库可见 MySQL 连接 {@code 17419}（{@code COMMAND=Sleep}，
     * 即池中空闲连接）<b>同时持有</b> {@code fssc_job_WfOutboxConsumerJob} 与
     * {@code fssc_job_WfLedgerReconcileJob} 两把锁，导致这两个（乃至经 {@code JobHeartbeatService}
     * 包装的<b>全部</b>）定时任务连续 33 轮被判为"已被其他实例持有调度锁，跳过本轮"而被饿死；
     * 其中含 G0 双账对账作业——它被饿死时双账漂移整段时间不可见。
     *
     * <p><b>代价</b>：持锁期间占用池中 1 条连接（主库池 {@code maximum-pool-size: 40}）；
     * 任务体与心跳另有连接，不影响 30 并发口径（PERF-01）。
     */
    private final Map<String, HeldLock> heldLocks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        if (jdbcTemplate == null) {
            // 无 JDBC 环境（纯逻辑单测等）：无锁直通，行为等价"单实例永远拿得到锁"
            return true;
        }
        HeldLock held = heldLocks.get(lockKey);
        if (held != null) {
            if (!alive(held.conn())) {
                // 连接已失效（MySQL 重启 / wait_timeout 断连）：服务端已随会话终止自动释放该锁，
                // 回收本地句柄后继续往下走，重新竞争一把新的。
                // 必须 discard（销毁）而非 close（归还池）：实证见 {@link #discardQuietly}。
                heldLocks.remove(lockKey, held);
                discardQuietly(held.conn());
            } else if (held.threadId() == Thread.currentThread().threadId()) {
                // 同线程重入：GET_LOCK 在同一会话上可重入，复用已持有的连接，不额外占第二条连接
                return true;
            } else {
                // 已被本实例的其它线程持有：直接判"占用"，不再取第二条连接去试探
                return false;
            }
        }
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) {
            return true;
        }
        Connection conn = null;
        try {
            conn = ds.getConnection();
            Integer r = callLockFn(conn, "SELECT GET_LOCK(?, 0)", lockKey);
            if (r != null && r == 1) {
                // 锁已挂在这条连接上，必须一直留着它直到 unlock——归还池就等于把锁丢进池里
                heldLocks.put(lockKey, new HeldLock(conn, Thread.currentThread().threadId()));
                return true;
            }
            // 未拿到（他实例持有）：这条连接没沾上任何会话锁，正常归还池
            closeQuietly(conn);
            return false;
        } catch (Exception e) {
            // 取锁异常：连接状态未知，按"未持有"处理并归还，交由 JobHeartbeatService 降级为不加锁执行
            closeQuietly(conn);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("[DistributedState] GET_LOCK 执行失败 lock=" + lockKey, e);
        }
    }

    @Override
    public void unlock(String lockKey) {
        HeldLock held = heldLocks.remove(lockKey);
        if (held == null) {
            // 未持有（非本实例、或已因断连自动释放）：无操作——绝不用别人的连接去 RELEASE_LOCK
            return;
        }
        Connection conn = held.conn();
        try {
            Integer r = callLockFn(conn, "SELECT RELEASE_LOCK(?)", lockKey);
            if (r == null || r != 1) {
                // 0/NULL ⇒ 该锁已不在本会话名下（多为连接曾断开、服务端已自动释放），此时无锁可泄
                log.warn("[DistributedState] RELEASE_LOCK 未返回 1 lock={} result={}（锁大概率已随断连释放）",
                        lockKey, r);
            }
        } catch (Exception e) {
            // 连接已不可用 ⇒ 服务端会话已终止 ⇒ MySQL 已自动释放该会话的全部具名锁，不存在残留
            log.warn("[DistributedState] RELEASE_LOCK 失败 lock={}（连接不可用，锁随会话终止自动释放）",
                    lockKey, e);
        } finally {
            // 归还池：此时锁要么已释放（返回值 1）、要么本就已不在本会话
            closeQuietly(conn);
        }
    }

    /** 在指定连接上执行锁函数，返回 MySQL 三值语义（1 / 0 / NULL→null） */
    private static Integer callLockFn(Connection conn, String sql, String lockKey) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    /** 连接是否仍然可用（断连/wait_timeout 后为 false，此时其上的会话锁已被服务端释放） */
    private static boolean alive(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    /** 正常归还一条<b>仍可用</b>的连接给池（锁未拿到、释放完毕等场景） */
    private static void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (Exception ignore) {
            // 归还失败不影响调用方；连接由池自行回收
        }
    }

    /**
     * 销毁一条<b>已确认不可用</b>的连接（绝不复用、绝不归还池）。
     *
     * <p><b>为什么不能只 close()（2026-09-25 harness 实证，非推断）</b>：HikariCP 的
     * {@code ProxyConnection.close()} 只把条目置回"未占用"并放回列表，<b>不做存活探测</b>；
     * 而 {@code getConnection()} 在 {@code aliveBypassWindowMs}（默认 500ms）内<b>跳过 ping</b>，
     * 且刚归还的条目位于列表首位（LIFO）。三者叠加的实测结果：把被 KILL 的连接 close() 之后
     * <b>立刻再借，拿回来的正是同一条死连接</b>——随后 {@code GET_LOCK} 抛
     * {@code SQLNonTransientConnectionException}，本轮被 {@code JobHeartbeatService} 降级为
     * "不加锁直接执行"（多实例下即互斥失效一拍）。改用 {@code abort()} 后立刻再借即为健康连接。
     *
     * <p>实测对照（HikariCP + MySQL，pool max=3）：
     * <pre>
     * close() ：KILL 后 isValid=false → close() → 立刻再借 isValid=false（同一条死连接）→ 隔 700ms 才恢复
     * abort() ：KILL 后 isValid=false → abort() → 立刻再借 isValid=true （服务端 id 已换新）
     * </pre>
     *
     * <p>{@code abort} 为 JDBC 4.1 标准方法（"终止连接并释放资源"）；池实现若不支持则退回 close()。
     */
    private static void discardQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.abort(Runnable::run);
        } catch (Exception e) {
            // abort() 不被池实现支持时退回 close()；两者均为「尽力释放」，失败不影响主流程
            closeQuietly(conn);
        }
    }

    /** 缓存条目（与原 TenantCache.Entry 同构：值 + 写入时间 + 过期时间） */
    private record CacheEntry(Object value, long writeAt, long expireAt) {
    }

    /** 限流窗口计数：到期后由 compute 重建（GET_LOCK 同连接重入由 MySQL 语义保证） */
    private static final class RateWindow {
        final long expireAt;
        int count;

        RateWindow(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}
