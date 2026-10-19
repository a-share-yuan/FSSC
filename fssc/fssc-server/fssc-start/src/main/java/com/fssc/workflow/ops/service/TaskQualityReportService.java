// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * <p><b>口径来源与边界（如实说明）</b>：本报表的唯一数据源是 {@code wf_task_history}：
 * <ul>
 *   <li>批退 = {@code action='REJECT'}（驳回），办理 = {@code action='APPROVE'}；
 *       <b>依赖 G2 驳回语义</b>——G2 之前的驳回是"终止本流程"，语义上更接近撤销，
 *       因此本报表对 G2 上线前的历史区间会把"终止型驳回"一并计入批退，属已知偏差；</li>
 *   <li>批退原因 = {@code REJECT} 行的 {@code comment}（审批意见原文）。系统不强制审批人填意见，
 *       故存在 {@code (未填写意见)} 桶；这一桶的大小本身就是一条质量读数（有多少驳回说不清理由）；</li>
 *   <li>平均停留 = {@code duration}（毫秒，既有真值）按节点/按人求平均；</li>
 *   <li>一次通过率 = 统计窗口内有审批历史的流程实例中，<b>从未出现 REJECT</b> 的实例占比。</li>
 * </ul>
 *
 * <p>本类<b>只读</b>，不写任何表（审计表 {@code wf_task_history} 也只增不改，这里绝不触碰）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQualityReportService {

    private final WfTaskHistoryMapper historyMapper;

    /** 统计窗口默认天数（与 {@code WfPerfService} 同一默认，便于看板并列阅读） */
    public static final int DEFAULT_DAYS = 30;

    /** 单次聚合读取的历史行数上限：避免大租户把整段历史读进内存 */
    private static final int MAX_SCAN_ROWS = 20000;

    /** 批退原因文本截断长度（原始意见可能很长，按前 N 字归桶） */
    private static final int REASON_MAX_LEN = 60;

    /** 未填写意见的占位桶名 */
    private static final String EMPTY_REASON = "(未填写意见)";

    // ------------------------------------------------------------------
    // 1. 总览
    // ------------------------------------------------------------------

    /**
     * 质量总览：批退率、一次通过率、平均节点停留、批退原因 Top 榜。
     *
     * @param days 统计窗口天数，&lt;=0 时取 {@link #DEFAULT_DAYS}
     */
    public Map<String, Object> summary(int days) {
        int d = days <= 0 ? DEFAULT_DAYS : days;
        LocalDateTime from = LocalDateTime.now().minusDays(d);
        List<WfTaskHistory> rows = scan(from);

        int approve = 0;
        int reject = 0;
        long durationSum = 0;
        int durationCount = 0;
        Set<String> touchedInstances = new LinkedHashSet<>();
        Set<String> rejectedInstances = new LinkedHashSet<>();
        Map<String, Integer> reasonBuckets = new HashMap<>();

        for (WfTaskHistory h : rows) {
            String action = h.getAction() == null ? "" : h.getAction().toUpperCase();
            if ("APPROVE".equals(action)) {
                approve++;
            } else if ("REJECT".equals(action)) {
                reject++;
                reasonBuckets.merge(reasonOf(h), 1, Integer::sum);
            } else {
                continue;
            }
            if (h.getProcInstId() != null) {
                touchedInstances.add(h.getProcInstId());
                if ("REJECT".equals(action)) {
                    rejectedInstances.add(h.getProcInstId());
                }
            }
            if (h.getDuration() != null && h.getDuration() > 0) {
                durationSum += h.getDuration();
                durationCount++;
            }
        }

        int handled = approve + reject;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", d);
        out.put("since", from);
        out.put("scannedRows", rows.size());
        out.put("approveCount", approve);
        out.put("rejectCount", reject);
        out.put("rejectRate", rate(reject, handled));
        out.put("instanceCount", touchedInstances.size());
        out.put("firstPassInstanceCount", touchedInstances.size() - rejectedInstances.size());
        out.put("firstPassRate", rate(touchedInstances.size() - rejectedInstances.size(), touchedInstances.size()));
        out.put("avgDwellMinutes", durationCount == 0 ? null
                : BigDecimal.valueOf(durationSum).divide(BigDecimal.valueOf(durationCount * 60000L), 1, RoundingMode.HALF_UP));
        out.put("topRejectReasons", topReasons(reasonBuckets, 10));
        out.put("note", "批退口径为 wf_task_history.action='REJECT'（依赖 G2 驳回语义）；"
                + "rejectRate=REJECT/(APPROVE+REJECT)，firstPassRate=窗口内无任何 REJECT 的流程实例占比");
        return out;
    }

    // ------------------------------------------------------------------
    // 2. 按节点 / 按人
    // ------------------------------------------------------------------

    /** 按节点：办理量、批退量、批退率、平均停留（分钟） */
    public List<Map<String, Object>> byNode(int days, int limit) {
        return aggregate(scan(LocalDateTime.now().minusDays(days <= 0 ? DEFAULT_DAYS : days)),
                h -> key(h.getNodeKey(), h.getNodeName()), 30, "nodeKey", "nodeName", limit);
    }

    /** 按办理人：办理量、批退量、批退率、平均停留（分钟） */
    public List<Map<String, Object>> byHandler(int days, int limit) {
        return aggregate(scan(LocalDateTime.now().minusDays(days <= 0 ? DEFAULT_DAYS : days)),
                h -> key(h.getOperUserId() == null ? null : String.valueOf(h.getOperUserId()), h.getOperUserName()),
                30, "operUserId", "operUserName", limit);
    }

    /**
     * 聚合：按分组键累计 APPROVE/REJECT 与停留时长。
     *
     * @param groupKeyOf 分组键提取器（joinKey 相同即同组）
     */
    private List<Map<String, Object>> aggregate(List<WfTaskHistory> rows, GroupKeyFn groupKeyOf,
                                               int defaultLimit, String idField, String nameField, int limit) {
        Map<String, int[]> actionAgg = new LinkedHashMap<>();     // joinKey -> [approve, reject]
        Map<String, long[]> dwellAgg = new LinkedHashMap<>();     // joinKey -> [sum, count]
        Map<String, String[]> labels = new LinkedHashMap<>();     // joinKey -> [id, name]
        for (WfTaskHistory h : rows) {
            String action = h.getAction() == null ? "" : h.getAction().toUpperCase();
            if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
                continue;
            }
            String[] key = groupKeyOf.apply(h);
            String joinKey = key[0] + "\u0001" + key[1];
            labels.putIfAbsent(joinKey, key);
            int[] a = actionAgg.computeIfAbsent(joinKey, k -> new int[2]);
            if ("APPROVE".equals(action)) {
                a[0]++;
            } else {
                a[1]++;
            }
            if (h.getDuration() != null && h.getDuration() > 0) {
                long[] dw = dwellAgg.computeIfAbsent(joinKey, k -> new long[2]);
                dw[0] += h.getDuration();
                dw[1]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : actionAgg.entrySet()) {
            String[] label = labels.get(e.getKey());
            int approve = e.getValue()[0];
            int reject = e.getValue()[1];
            long[] dw = dwellAgg.getOrDefault(e.getKey(), new long[2]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(idField, label[0].isEmpty() ? null : label[0]);
            row.put(nameField, label[1]);
            row.put("approveCount", approve);
            row.put("rejectCount", reject);
            row.put("handledCount", approve + reject);
            row.put("rejectRate", rate(reject, approve + reject));
            row.put("avgDwellMinutes", dw[1] == 0 ? null
                    : BigDecimal.valueOf(dw[0]).divide(BigDecimal.valueOf(dw[1] * 60000L), 1, RoundingMode.HALF_UP));
            out.add(row);
        }
        // 质量看板先看"批退最多的人/节点"：按批退量降序，其次办理量降序
        out.sort(Comparator.comparingInt((Map<String, Object> r) -> -((Number) r.get("rejectCount")).intValue())
                .thenComparingInt(r -> -((Number) r.get("handledCount")).intValue()));
        int cap = limit <= 0 ? defaultLimit : limit;
        return out.size() > cap ? out.subList(0, cap) : out;
    }

    /** 分组键提取器：返回 [id, 显示名] */
    private interface GroupKeyFn {
        String[] apply(WfTaskHistory h);
    }

    private static String[] key(String id, String name) {
        return new String[]{id == null ? "" : id, name == null || name.isBlank() ? "(未知)" : name};
    }

    // ------------------------------------------------------------------
    // 3. 批退原因分布 / 批退明细
    // ------------------------------------------------------------------

    /**
     * 批退原因分布（按意见原文归桶）。
     *
     * @param nodeKey 可选：只看某个节点的批退原因
     */
    public List<Map<String, Object>> rejectReasons(int days, String nodeKey, int limit) {
        int d = days <= 0 ? DEFAULT_DAYS : days;
        List<WfTaskHistory> rejects = rejects(LocalDateTime.now().minusDays(d), nodeKey);
        Map<String, Integer> buckets = new HashMap<>();
        for (WfTaskHistory h : rejects) {
            buckets.merge(reasonOf(h), 1, Integer::sum);
        }
        return topReasons(buckets, limit <= 0 ? 20 : limit);
    }

    /** 批退明细分页（审批意见原文，供质量抽查逐条看） */
    public PageResult<Map<String, Object>> rejectPage(PageQuery q, int days, String nodeKey, String keyword) {
        int d = days <= 0 ? DEFAULT_DAYS : days;
        LambdaQueryWrapper<WfTaskHistory> w = new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getAction, "REJECT")
                .ge(WfTaskHistory::getOperTime, LocalDateTime.now().minusDays(d));
        if (nodeKey != null && !nodeKey.isBlank()) {
            w.eq(WfTaskHistory::getNodeKey, nodeKey.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(x -> x.like(WfTaskHistory::getComment, kw)
                    .or().like(WfTaskHistory::getNodeName, kw)
                    .or().like(WfTaskHistory::getOperUserName, kw)
                    .or().like(WfTaskHistory::getBizType, kw));
        }
        w.orderByDesc(WfTaskHistory::getId);
        Page<WfTaskHistory> p = historyMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WfTaskHistory h : p.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("procInstId", h.getProcInstId());
            row.put("taskId", h.getTaskId());
            row.put("nodeKey", h.getNodeKey());
            row.put("nodeName", h.getNodeName());
            row.put("bizType", h.getBizType());
            row.put("bizId", h.getBizId());
            row.put("operUserId", h.getOperUserId());
            // 姓名取历史冗余列：人员离职/改名后仍能看到当时是谁驳的
            row.put("operUserName", h.getOperUserName());
            row.put("comment", h.getComment());
            row.put("reasonBucket", reasonOf(h));
            row.put("durationMinutes", h.getDuration() == null ? null : h.getDuration() / 60000L);
            row.put("operTime", h.getOperTime());
            rows.add(row);
        }
        return PageResult.of(rows, p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    // ------------------------------------------------------------------
    // 4. 内部工具
    // ------------------------------------------------------------------

    /**
     * 扫描窗口内的办理/驳回历史。
     *
     * <p>只取 APPROVE/REJECT 两类并限定 oper_time 窗口（该表按月分区，带时间条件才能落在分区上），
     * 再按 id 上限截断，避免大租户把整段历史读进内存。
     */
    private List<WfTaskHistory> scan(LocalDateTime from) {
        return historyMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .in(WfTaskHistory::getAction, "APPROVE", "REJECT")
                .ge(WfTaskHistory::getOperTime, from)
                .orderByDesc(WfTaskHistory::getId)
                .last("limit " + MAX_SCAN_ROWS));
    }

    private List<WfTaskHistory> rejects(LocalDateTime from, String nodeKey) {
        LambdaQueryWrapper<WfTaskHistory> w = new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getAction, "REJECT")
                .ge(WfTaskHistory::getOperTime, from)
                .orderByDesc(WfTaskHistory::getId)
                .last("limit " + MAX_SCAN_ROWS);
        if (nodeKey != null && !nodeKey.isBlank()) {
            w.eq(WfTaskHistory::getNodeKey, nodeKey.trim());
        }
        return historyMapper.selectList(w);
    }

    private List<Map<String, Object>> topReasons(Map<String, Integer> buckets, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(buckets.entrySet());
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reason", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        return out;
    }

    /** 批退原因归桶：意见原文前 {@value #REASON_MAX_LEN} 字；空意见给显式占位而不是空串 */
    private String reasonOf(WfTaskHistory h) {
        String c = h.getComment();
        if (c == null || c.isBlank()) {
            return EMPTY_REASON;
        }
        String t = c.trim().replaceAll("\\s+", " ");
        return t.length() > REASON_MAX_LEN ? t.substring(0, REASON_MAX_LEN) + "…" : t;
    }

    /** 比率（百分比，1 位小数）；分母为 0 时返回 null（不返回 0.0 —— 无样本不等于 0%） */
    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }
}