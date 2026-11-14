package com.fssc.image.paging;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.framework.paging.CountSnapshot;
import com.fssc.framework.paging.ListPaging;
import com.fssc.framework.tenant.TenantContext;

import java.util.function.Supplier;

/**
 * 影像域分页工具：把「每翻一页重付一次全表 COUNT」换成 60 秒总数快照。
 *
 * <p><b>为什么要单独一层</b>：主域的 {@link CountSnapshot} 只负责缓存数值，调用方仍需自己
 * 关掉 MyBatis-Plus 分页插件的 {@code searchCount}、自己拼「租户 + 权限 + 全部筛选」签名。
 * 影像域有 5 个分页端点（任务 / 调阅 / 异常 / OCR 记录 / 设置），漏掉任一处就会出现
 * 「COUNT 关了但没缓存」或「缓存了但签名漏条件」——后者更危险：不同筛选共用一份总数，
 * 页码画错、翻到空页，属于静默少数据。故统一收口到本类。</p>
 *
 * <p><b>实测依据（2026-11-14，{@code fssc_image_perf} 198 万行）</b>：取一页数据恒 0.6ms，
 * 而分页 {@code COUNT(*)} 恒 190~540ms，一轮混合读压测累计扫 5.5 亿行，10 并发即饱和
 * ~70 RPS；加 5 条索引后 RPS 不涨。快照把每签名每 60 秒只留一次 COUNT，是唯一有效解。</p>
 *
 * <p><b>代价</b>：总数最多陈旧 60 秒，列表内容实时；总数只用于画页码，不参与业务判断。</p>
 */
public final class ImagePageKit {

    private ImagePageKit() {
    }

    /**
     * 构造「不查 COUNT」的分页对象，同时把 pageSize 钳到 1..500。
     *
     * <p>{@code PageQuery} 只校验 {@code @Min(1)}，没有上限：前端误传或恶意传
     * {@code pageSize=1000000} 会一次拉走整张影像表。钳位上限与
     * {@link ListPaging#MAX_SIZE} 保持一致，跨系统一套口径。</p>
     */
    public static <T> Page<T> page(long pageNum, long pageSize) {
        Page<T> p = Page.of(Math.max(1, pageNum), clampSize(pageSize));
        p.setSearchCount(false);
        return p;
    }

    /** 页大小钳位（供自定义 SQL 分页共用，避免两处口径漂移）。 */
    public static int clampSize(long pageSize) {
        if (pageSize < 1) {
            return ListPaging.DEFAULT_SIZE;
        }
        return (int) Math.min(pageSize, ListPaging.MAX_SIZE);
    }

    /**
     * 总数快照签名：视图名 + 租户 + 数据权限指纹 + 全部筛选值。
     *
     * <p>视图名必须带（否则任务页与异常页会共用一份总数）；筛选值必须逐一列入，
     * 空值归一为 {@code "-"}，避免 {@code null} 与空串拼出不同 key 却查同一结果集。</p>
     */
    public static String countKey(String view, Object... filters) {
        StringBuilder sb = new StringBuilder(view)
                .append('|').append(TenantContext.peekTenantId())
                .append('|').append(CountSnapshot.scopeFingerprint());
        for (Object f : filters) {
            sb.append('|').append(norm(f));
        }
        return sb.toString();
    }

    /**
     * 取总数快照：同一签名 60 秒内只执行一次 {@code loader}（{@code compute} 防击穿）。
     *
     * @param view    视图名，如 {@code img_batch/task}
     * @param loader  真实计数（与取数使用完全相同的筛选条件）
     * @param filters 影响结果集的全部筛选值，顺序固定
     */
    public static long total(String view, Supplier<Long> loader, Object... filters) {
        return CountSnapshot.of(countKey(view, filters), loader);
    }

    private static String norm(Object v) {
        if (v == null) {
            return "-";
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "-" : s;
    }
}
