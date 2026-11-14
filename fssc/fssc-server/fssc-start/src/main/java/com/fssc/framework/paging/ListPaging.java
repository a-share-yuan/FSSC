package com.fssc.framework.paging;

import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表端点的分页契约。
 *
 * <p><b>为什么 JSON 载荷保持数组形状</b>：既有消费方（{@code items.value = await api.xxx()} 后直接
 * {@code .map/.filter/v-for}）都假定 {@code data} 是数组。若改成 {@code PageResult} 对象，
 * 这些调用方会拿到对象而崩（{@code map is not a function}）。故分页元数据一律走<b>响应头</b>，
 * 载荷形状不变。</p>
 *
 * <p><b>与 BCP {@code PartnerListPaging} 同契约</b>：1..500 钳位、默认 200、多取一行探测下一页、
 * 元数据走 {@code X-Page-Num}/{@code X-Page-Size}/{@code X-Returned-Count}/{@code X-Truncated}。
 * 两套系统同契约，跨系统联调与压测脚本只需认一套头名。</p>
 *
 * <p><b>为什么不回总数</b>：总数要额外一条 {@code COUNT(*)}，在六位数单量下比本页取数还贵；
 * 没有 total 就画不出页码，硬凑一个「当前页 + 1」会让用户以为末页之后还有。
 * 前端用「上一页 / 下一页 + 是否还有下一页」的翻页器（对照 BCP {@code MailPackagePage}）。</p>
 *
 * <p><b>契约变更风险</b>：默认上限 200 是<b>行为变更</b>——此前不传分页参数会返回全量。
 * 凡是消费方依赖「全量」的，必须改成显式分页或加关键字检索，否则会静默少数据。
 * 这正是本类存在的意义：把「无上限」变成「有上限且有信号」。</p>
 */
public final class ListPaging {

    /** 默认页大小。 */
    public static final int DEFAULT_SIZE = 200;
    /** 页大小上限：与 BCP PartnerListPaging 同值，防止单请求拉全表。 */
    public static final int MAX_SIZE = 500;
    /** 页码上限：防止 {@code pageNum} 巨大时 OFFSET 溢出。 */
    public static final int MAX_PAGE_NUM = 100_000;

    private final int pageNum;
    private final int pageSize;

    private ListPaging(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * 入参按字符串收：缺省、非数字一律回落默认值，负数与超界按 1..上限钳位 ——
     * 老客户端把这两个参数当未知键传任意值时，行为与不传一致，不会新增 400。
     */
    public static ListPaging of(String pageNum, String pageSize) {
        return new ListPaging(clamp(rawInt(pageNum, 1), 1, MAX_PAGE_NUM),
                clamp(rawInt(pageSize, DEFAULT_SIZE), 1, MAX_SIZE));
    }

    /** 数字入参重载：供已用 {@code @RequestParam(defaultValue="1") int} 的端点直接调用。 */
    public static ListPaging of(Integer pageNum, Integer pageSize) {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? DEFAULT_SIZE : pageSize;
        return new ListPaging(clamp(pn, 1, MAX_PAGE_NUM), clamp(ps, 1, MAX_SIZE));
    }

    public int pageNum() {
        return pageNum;
    }

    public int pageSize() {
        return pageSize;
    }

    /** 本页第一条在结果集里的下标（= (pageNum-1) * pageSize）。 */
    public long offset() {
        return (long) (pageNum - 1) * pageSize;
    }

    /** MySQL 片段：多取一行用于探测「是否还有下一页」。 */
    public String limitClause() {
        return "LIMIT " + (pageSize + 1) + " OFFSET " + offset();
    }

    /**
     * 裁掉探测行、写响应头，返回对外可见的行集。
     *
     * @param fetched 带探测行的取数结果（长度 ≤ pageSize+1）
     */
    public <T> List<T> describe(List<T> fetched, HttpServletResponse response) {
        boolean truncated = fetched.size() > pageSize;
        List<T> rows = truncated ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        writeHeaders(response, pageNum, pageSize, rows.size(), truncated);
        return rows;
    }

    /** 只写响应头不改行集（供「明细随主表裁剪」这类场景）。 */
    public void writeHeaders(HttpServletResponse response, int returned, boolean truncated) {
        writeHeaders(response, pageNum, pageSize, returned, truncated);
    }

    public static void writeHeaders(HttpServletResponse response, int pageNum, int pageSize,
                                     int returned, boolean truncated) {
        if (response == null) {
            return;
        }
        response.setHeader("X-Page-Num", String.valueOf(pageNum));
        response.setHeader("X-Page-Size", String.valueOf(pageSize));
        response.setHeader("X-Returned-Count", String.valueOf(returned));
        response.setHeader("X-Truncated", String.valueOf(truncated));
    }

    private static int rawInt(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }
}
