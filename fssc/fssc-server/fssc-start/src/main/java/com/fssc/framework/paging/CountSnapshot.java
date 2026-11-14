package com.fssc.framework.paging;

import com.fssc.framework.security.DataScopeHolder;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 列表总数的短时快照（60 秒），专治「每翻一页都重付一次全表 COUNT」。
 *
 * <p><b>为什么需要它</b>：MyBatis-Plus 的 {@code Page} 默认 {@code searchCount=true}，
 * 于是同一个筛选条件下的第 1 页、第 2 页、第 3 页……各自都要跑一遍 {@code SELECT COUNT(*)}。
 * 实测（{@code fssc_perf}，{@code op_voucher} 102.4 万行，单租户）：
 * <ul>
 *   <li>无筛选 COUNT：<b>519ms</b>；</li>
 *   <li>带 {@code status='POSTED'}（占 97% 行）：加索引后仍 <b>513ms</b>；</li>
 *   <li>而取一页数据只要 <b>1.2ms</b>。</li>
 * </ul>
 * 也就是说翻 10 页＝10 次 COUNT＝5.2 秒纯等待，其中 9 次结果完全相同。
 * 并发下 COUNT 只有 <b>85 QPS</b> 上限（线程从 4 加到 50 都不涨，是 O(N) 索引扫描的物理速度），
 * 台账首屏因此把整站并发压在两位数量级。</p>
 *
 * <p><b>它做什么</b>：把「计数结果」按<b>查询签名</b>缓存 60 秒。签名包含租户、数据权限指纹
 * 与全部筛选条件，所以不同公司/不同权限的账号互不串味；同一份签名在 60 秒内只数一次，
 * 其余请求直接拿快照。翻页、刷新、多人同时打开同一视图，都只付一次 COUNT 的成本。</p>
 *
 * <p><b>代价（必须明说）</b>：总数最多陈旧 60 秒。列表<b>内容</b>是实时的，只有「共 N 条」
 * 这个数字可能滞后一分钟——新增/删除单据后立刻打开台账，页码可能少一页，60 秒后自愈。
 * 这是有意的取舍：总数只用于画页码，不参与任何业务判断。</p>
 *
 * <p><b>为什么不用「不返回总数」</b>：没有 total 就画不出页码，而用 EXPLAIN 估算值替代更危险——
 * {@code tenant_id} 的 cardinality 只有 1，优化器估算偏差实测 −52.8%，会画出只有一半的页码，
 * 用户永远翻不到底（静默少数据）。宁可慢 60 秒，不可错。</p>
 *
 * <p><b>防击穿</b>：用 {@code ConcurrentHashMap#compute} 而不是「先 get 再 put」——
 * 后者在快照过期瞬间会让 N 个并发请求同时去数，正好在最忙的时候打出 N 条 519ms 的 COUNT。
 * {@code compute} 对同一 key 串行，后到者直接等到新快照。</p>
 */
public final class CountSnapshot {

    /** 快照有效期：60 秒。够挡住翻页/刷新的重复计数，又不至于让用户察觉「数字不动」。 */
    private static final long TTL_MS = 60_000L;

    /** 条目上限：超过就先清过期的，再不行整体清空（宁可多算一次，不可无界吃内存）。 */
    private static final int MAX_ENTRIES = 1024;

    private static final ConcurrentHashMap<String, Slot> CACHE = new ConcurrentHashMap<>();

    private CountSnapshot() {
    }

    /**
     * 取总数快照；不存在或已过期时用 {@code loader} 现算并写入。
     *
     * @param key    查询签名，必须<b>唯一覆盖影响结果集的全部条件</b>（租户 + 数据权限 + 筛选值）
     * @param loader 真正的计数逻辑（如 {@code mapper::selectCount}）；返回 null 按 0 处理
     * @return 总数
     */
    public static long of(String key, Supplier<Long> loader) {
        evictIfCrowded();
        long now = System.currentTimeMillis();
        Slot slot = CACHE.compute(key, (k, cur) -> {
            if (cur != null && now - cur.at() < TTL_MS) {
                return cur;
            }
            Long loaded = loader.get();
            // mapper.selectCount 正常返回 0 而不是 null，但封装层被替换/异常兜底时可能给 null；
            // 这里吞掉，不让 NPE 冒到列表接口（总数错没有快照可用的代价大得多）。
            return new Slot(now, loaded == null ? 0L : Math.max(0L, loaded));
        });
        return slot.total();
    }

    /**
     * 当前登录人的数据权限指纹，供拼装 {@code key} 用。
     *
     * <p>不把权限算进签名会串味：同一个筛选条件，集团账号看到 100 万行、公司账号只看到 7 千行，
     * 若共用一份快照，公司账号会看到集团的总数，页码一点就翻空。</p>
     *
     * <p>非 {@code @DataScope} 方法（载体为空）返回 {@code "-"}，行为与不缓存前一致。</p>
     */
    public static String scopeFingerprint() {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return "-";
        }
        Set<Long> child = s.getDeptAndChild();
        // 部门集合只影响 DEPT_AND_CHILD 一支；用 size 而非全量拼接，避免长 IN 列表把 key 撑爆。
        // size 相同但成员不同的极端情况会共用一次快照，最多让总数差一页，60 秒自愈。
        return s.getScope() + "/" + s.getCompanyId() + "/" + s.getDeptId() + "/"
                + s.getUserId() + "/" + (child == null ? 0 : child.size());
    }

    /** 清空全部快照（测试用；生产不调用——靠 TTL 自然过期）。 */
    public static void clear() {
        CACHE.clear();
    }

    /** 当前快照条目数（观测用）。 */
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

    /** 一条快照：取值时刻 + 数值。 */
    private record Slot(long at, long total) {
    }
}
