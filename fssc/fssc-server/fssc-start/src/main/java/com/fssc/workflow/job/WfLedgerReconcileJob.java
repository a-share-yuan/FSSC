// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.notify.OpsAlertService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.WfMirrorDefectRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 流程双账对账作业（G0 止血 · 方案 §二 F13 / §六 G0-③，2026-09-24）。
 *
 * <p><b>为什么需要它</b>：Flowable 的 {@code ACT_*} 是引擎账，{@code wf_instance/wf_task/
 * wf_task_history} 是业务账。两本账由 {@code WfEngineFacade} 手工双写，任何一次写失败或旁路推进
 * 都会让它们悄悄分叉（F13 实测：{@code wf_task_history} 294 行 vs {@code act_hi_taskinst} 211 行）。
 * 在"写失败必须冒泡"（G0-①）之外，还需要一个独立的<b>对账哨兵</b>来回答"现在到底有没有漂移"。
 *
 * <p><b>三条对账向量</b>：
 * <ol>
 *   <li>{@code ACT_RU_TASK} ↔ {@code wf_task(status=PENDING)}：在办任务是否都能被待办列表看见；</li>
 *   <li>{@code ACT_HI_PROCINST} ↔ {@code wf_instance}：历史流程实例是否都有业务实例视图；</li>
 *   <li>{@code ACT_HI_TASKINST} ↔ {@code wf_task_history}：引擎任务历史是否都有审批留痕。
 *       <b>双向口径收窄（2026-09-25）</b>：本向量要比较的是「任务级<b>办结</b>留痕」，两侧都必须先剥掉
 *       「过程态」行，否则差集恒不为零、真信号会被噪声埋掉（单侧收窄时告警还会从反向向量反扑）。
 *       <ul>
 *         <li><b>引擎侧</b>只取「已办结且非删除」（{@code endTime != null} 且 {@code deleteReason} 为空）：
 *             引擎在任务<b>创建时</b>即写历史行，任务被取消/驳回跳转删除后该行仍保留（带
 *             {@code DELETE_REASON_}），这类行天然没有镜像留痕。<b>收窄前实测固定误报 27 条
 *             （5 条在途 + 22 条被删除），其中真漂移 0 条。</b></li>
 *         <li><b>业务侧</b>只取办结类动作（见 {@link #MIRROR_DONE_ACTIONS}）：{@code wf_task_history}
 *             是「任务级留痕 + 过程动作」混表——{@code URGE}/{@code ESCALATE}/{@code CLAIM}
 *             /{@code ASSIGN}/{@code UNCLAIM}/{@code TRANSFER}/{@code REASSIGN} 都带 {@code task_id}
 *             但只是过程动作，任务本身还在办；{@code REJECT_TO}/{@code REJECT_TO_DONE} 是驳回跳转，
 *             引擎把该任务<b>删除</b>（{@code DELETE_REASON_=Change activity to ...}）而非办结。
 *             只收窄引擎侧时，这些行会立刻从反向向量（{@code historyMissingInEngine}）反扑
 *             （实测：单侧收窄后 v3a=0 而 v3b=14，告警仍每轮常亮）。</li>
 *       </ul>
 *       双向收窄后两侧集合同为 184，v3a/v3b 同时归零（2026-09-25 运行期实测）。</li>
 * </ol>
 *
 * <p><b>只报不修</b>（方案明确要求）：对账作业绝不自动改数据——静默修复会把"哪里出了问题"
 * 一起抹掉，反而更难定位。差异按租户聚合后落一条 {@code sys_oper_log}（{@code status=1}，
 * {@code error_msg} 带错误码 {@code WF-MIRROR-002}、计数与样本 ID），同时推一条运维告警；
 * 最近一次报告驻留内存，经 {@code GET /api/tenant/wf/perf/ledger-reconcile} 查看。
 *
 * <p>跨租户扫描：本作业在调度线程运行（无租户上下文），所有 {@code wf_*} 查询一律包在
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfLedgerReconcileJob {

    private final TaskService taskService;
    private final HistoryService historyService;
    private final WfTaskMapper wfTaskMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskHistoryMapper wfTaskHistoryMapper;
    private final JobHeartbeatService jobHeartbeatService;
    private final OpsAlertService opsAlertService;
    private final WfMirrorDefectRecorder mirrorDefectRecorder;

    /** 单条向量保留的样本 ID 数（报告体积上限） */
    private static final int SAMPLE_LIMIT = 50;
    /** 单条向量最多落几个租户的缺陷记录（避免一个坏批次刷爆 sys_oper_log） */
    private static final int MAX_TENANT_DEFECTS = 10;
    /** 引擎历史查询上限：防止历史表膨胀时把对账作业自身拖垮 */
    private static final int HISTORY_SCAN_LIMIT = 20000;

    /**
     * 业务账 {@code wf_task_history.action} 中属于「任务级办结留痕」的动作白名单（口径收窄 2026-09-25）。
     *
     * <p><b>为什么必须按动作白名单</b>：{@code wf_task_history} 是「任务级留痕 + 过程动作」混表，
     * 过程动作也带 {@code task_id}——催办 {@code URGE}、升级 {@code ESCALATE}、认领 {@code CLAIM}、
     * 派单 {@code ASSIGN}、释放 {@code UNCLAIM}、转办 {@code TRANSFER}、改派 {@code REASSIGN}
     * 记录的是「谁对还在办的任务做了什么」，任务本身并未办结；{@code REJECT_TO}/{@code REJECT_TO_DONE}
     * 是驳回跳转，引擎把该任务<b>删除</b>（{@code DELETE_REASON_ = Change activity to ...}）而非办结。
     * 这些行若也算作留痕，会与引擎侧的「已办结且非删除」判据错位，反向向量恒不为零。
     *
     * <p>下列动作对应的引擎任务必然落在「{@code endTime != null} 且 {@code deleteReason} 为空」里：
     * <ul>
     *   <li>{@code APPROVE} / {@code REJECT}：{@code WfProjectionService.onTaskCompleted} 写；</li>
     *   <li>{@code AUTO_PASS}：超时自动通过（{@code WfTimeoutJob}）与空策略 SKIP
     *       （{@code NodeOrgResolver}），引擎侧同样走 {@code taskService.complete()} 办结。</li>
     * </ul>
     * <b>新增办结类动作时必须同步本白名单</b>，否则会出现 v3a 误报（引擎已办结却"缺留痕"）。
     */
    private static final Set<String> MIRROR_DONE_ACTIONS = Set.of("APPROVE", "REJECT", "AUTO_PASS");

    /** 最近一次对账报告（供运维页只读查看；进程内驻留，重启即空） */
    private volatile Map<String, Object> lastReport = Map.of("status", "NEVER_RUN");

    @Scheduled(fixedDelayString = "${fssc.wf.reconcile.interval-ms:600000}",
            initialDelayString = "${fssc.wf.reconcile.initial-delay-ms:180000}")
    public void reconcile() {
        jobHeartbeatService.run("WfLedgerReconcileJob", () -> {
            Map<String, Object> report = doReconcile();
            lastReport = report;
            long drift = asLong(report.get("driftTotal"));
            if (drift > 0) {
                String content = "流程双账对账发现 " + drift + " 条差异（引擎账 ACT_* vs 业务账 wf_*）。\n"
                        + "在办任务缺待办行=" + vectorCount(report, "engineTaskMissingInMirror")
                        + "，幽灵待办=" + vectorCount(report, "mirrorPendingMissingInEngine")
                        + "，实例缺视图=" + vectorCount(report, "engineProcInstMissingInMirror")
                        + "，视图缺引擎实例=" + vectorCount(report, "mirrorInstanceMissingInEngine")
                        + "，历史缺留痕=" + vectorCount(report, "engineTaskInstMissingInHistory")
                        + "，留痕缺引擎任务=" + vectorCount(report, "historyMissingInEngine")
                        + "\n本作业只报不修：请查系统日志（错误码 " + WfMirrorDefectRecorder.CODE_LEDGER_DRIFT
                        + "）核对样本 ID 后再人工处置。";
                opsAlertService.alert("WARN", "流程双账对账发现差异", content);
            }
        });
    }

    /** 最近一次对账报告（只读） */
    public Map<String, Object> lastReport() {
        return lastReport;
    }

    /** 立即跑一轮对账并返回报告（供运维手动触发，不走心跳锁） */
    public Map<String, Object> doReconcile() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runAt", LocalDateTime.now().toString());

        // ---------- 引擎账（ACT_*） ----------
        Set<String> engineTaskIds = new LinkedHashSet<>();
        Map<String, String> taskProcInst = new HashMap<>();       // taskId -> procInstId
        for (Task t : taskService.createTaskQuery().list()) {
            engineTaskIds.add(t.getId());
            taskProcInst.put(t.getId(), t.getProcessInstanceId());
        }
        Set<String> engineProcInstIds = new LinkedHashSet<>();
        for (HistoricProcessInstance h : historyService.createHistoricProcessInstanceQuery()
                .listPage(0, HISTORY_SCAN_LIMIT)) {
            engineProcInstIds.add(h.getId());
        }
        Set<String> engineHistTaskIds = new LinkedHashSet<>();
        for (HistoricTaskInstance h : historyService.createHistoricTaskInstanceQuery()
                .listPage(0, HISTORY_SCAN_LIMIT)) {
            // 口径收窄（2026-09-25）：镜像的任务级留痕只在办结时写，故只有「已办结且非删除」的
            // 历史任务才应当有镜像行；在途任务（endTime 为空）与被删除任务（deleteReason 非空，
            // 如取消、驳回跳转、流程取消）天然无行，不构成本向量的漂移。
            if (h.getEndTime() == null) {
                continue;
            }
            if (h.getDeleteReason() != null && !h.getDeleteReason().isBlank()) {
                continue;
            }
            engineHistTaskIds.add(h.getId());
        }

        // ---------- 业务账（wf_*，跨租户） ----------
        List<WfTask> pending = ignoreTenant(() -> wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>().eq(WfTask::getStatus, "PENDING")));
        Set<String> mirrorPendingTaskIds = new LinkedHashSet<>();
        Map<String, Long> pendingTenantByTask = new HashMap<>();
        for (WfTask t : pending == null ? List.<WfTask>of() : pending) {
            if (t.getTaskId() == null) continue;
            mirrorPendingTaskIds.add(t.getTaskId());
            pendingTenantByTask.put(t.getTaskId(), t.getTenantId());
        }
        List<WfInstance> instances = ignoreTenant(() -> wfInstanceMapper.selectList(null));
        Set<String> mirrorProcInstIds = new LinkedHashSet<>();
        Map<String, Long> tenantByProcInst = new HashMap<>();
        for (WfInstance i : instances == null ? List.<WfInstance>of() : instances) {
            if (i.getProcInstId() == null) continue;
            mirrorProcInstIds.add(i.getProcInstId());
            tenantByProcInst.put(i.getProcInstId(), i.getTenantId());
        }
        List<WfTaskHistory> histories = ignoreTenant(() -> wfTaskHistoryMapper.selectList(null));
        Set<String> mirrorHistTaskIds = new LinkedHashSet<>();
        Map<String, Long> tenantByHistTask = new HashMap<>();
        for (WfTaskHistory h : histories == null ? List.<WfTaskHistory>of() : histories) {
            // taskId 为空的留痕是事件级行（SUBMIT / APPROVED / REJECTED），不参与任务级比对
            if (h.getTaskId() == null) continue;
            // 口径收窄（2026-09-25）：只认办结类动作；催办/升级/认领/转办/驳回跳转等过程动作行
            // 虽带 task_id，但任务本身未办结，计入即与引擎侧判据错位（详见 MIRROR_DONE_ACTIONS）。
            if (!MIRROR_DONE_ACTIONS.contains(h.getAction())) continue;
            mirrorHistTaskIds.add(h.getTaskId());
            tenantByHistTask.put(h.getTaskId(), h.getTenantId());
        }

        // ---------- 三向量求差 ----------
        Vector v1a = diff(engineTaskIds, mirrorPendingTaskIds,
                id -> tenantByProcInst.get(taskProcInst.get(id)));
        Vector v1b = diff(mirrorPendingTaskIds, engineTaskIds, pendingTenantByTask::get);
        Vector v2a = diff(engineProcInstIds, mirrorProcInstIds, tenantByProcInst::get);
        Vector v2b = diff(mirrorProcInstIds, engineProcInstIds, tenantByProcInst::get);
        Vector v3a = diff(engineHistTaskIds, mirrorHistTaskIds, tenantByHistTask::get);
        Vector v3b = diff(mirrorHistTaskIds, engineHistTaskIds, tenantByHistTask::get);

        report.put("engineTaskCount", engineTaskIds.size());
        report.put("mirrorPendingCount", mirrorPendingTaskIds.size());
        report.put("engineProcInstCount", engineProcInstIds.size());
        report.put("mirrorInstanceCount", mirrorProcInstIds.size());
        report.put("engineHistTaskCount", engineHistTaskIds.size());
        report.put("mirrorHistTaskCount", mirrorHistTaskIds.size());
        putVector(report, "engineTaskMissingInMirror", v1a);
        putVector(report, "mirrorPendingMissingInEngine", v1b);
        putVector(report, "engineProcInstMissingInMirror", v2a);
        putVector(report, "mirrorInstanceMissingInEngine", v2b);
        putVector(report, "engineTaskInstMissingInHistory", v3a);
        putVector(report, "historyMissingInEngine", v3b);
        long driftTotal = v1a.size() + v1b.size() + v2a.size() + v2b.size() + v3a.size() + v3b.size();
        report.put("driftTotal", driftTotal);

        // ---------- 缺陷留痕（按租户聚合，只报不修） ----------
        recordDrift("engineTaskMissingInMirror", v1a);
        recordDrift("mirrorPendingMissingInEngine", v1b);
        recordDrift("engineProcInstMissingInMirror", v2a);
        recordDrift("mirrorInstanceMissingInEngine", v2b);
        recordDrift("engineTaskInstMissingInHistory", v3a);
        recordDrift("historyMissingInEngine", v3b);

        if (driftTotal == 0) {
            log.info("[双账对账] 无差异：引擎在办任务 {} / 历史实例 {} / 历史任务 {}",
                    engineTaskIds.size(), engineProcInstIds.size(), engineHistTaskIds.size());
        } else {
            log.error("[双账对账] 发现差异 {} 条：{}", driftTotal, report);
        }
        return report;
    }

    private void recordDrift(String vectorName, Vector v) {
        if (v.size() == 0) {
            return;
        }
        int written = 0;
        for (Map.Entry<Long, List<String>> e : v.byTenant.entrySet()) {
            if (written++ >= MAX_TENANT_DEFECTS) {
                break;
            }
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_LEDGER_DRIFT,
                    "WfLedgerReconcileJob." + vectorName, e.getKey(),
                    "count=" + v.size() + " tenantCount=" + e.getValue().size()
                            + " sample=" + String.join(",", e.getValue()), null);
        }
    }

    private void putVector(Map<String, Object> report, String key, Vector v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", v.size());
        m.put("sample", v.allSample);
        report.put(key, m);
    }

    @SuppressWarnings("unchecked")
    private long vectorCount(Map<String, Object> report, String key) {
        Object o = report.get(key);
        if (o instanceof Map<?, ?> m) {
            return asLong(((Map<String, Object>) m).get("count"));
        }
        return 0L;
    }

    private long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    /** 差集结果：总量 + 全量样本（上限 {@link #SAMPLE_LIMIT}）+ 按租户分组的样本 */
    private static final class Vector {
        final List<String> allSample = new ArrayList<>();
        final Map<Long, List<String>> byTenant = new LinkedHashMap<>();
        int total;

        int size() {
            return total;
        }
    }

    private Vector diff(Set<String> left, Set<String> right, java.util.function.Function<String, Long> tenantOf) {
        Vector v = new Vector();
        for (String id : left) {
            if (right.contains(id)) {
                continue;
            }
            v.total++;
            if (v.allSample.size() < SAMPLE_LIMIT) {
                v.allSample.add(id);
            }
            Long tid = tenantOf == null ? null : tenantOf.apply(id);
            long key = tid == null ? -1L : tid;
            List<String> bucket = v.byTenant.computeIfAbsent(key, k -> new ArrayList<>());
            if (bucket.size() < SAMPLE_LIMIT) {
                bucket.add(id);
            }
        }
        return v;
    }

    private <T> T ignoreTenant(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
