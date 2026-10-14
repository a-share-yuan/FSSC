// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.ops.service.TaskTimeLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * G3（2026-09-24）任务时限超时扫描（以 {@code wf_task.due_time} 为唯一真值）。
 *
 * <p><b>本 Job 只报不修，为什么</b>：催办、超时自动通过这两件"动作"已由既有
 * {@code WfTimeoutJob} 承担（它按 {@code wf_node_config.timeout_hours} 现算）。若本 Job 也去催办，
 * 同一张单会被两套口径各催一次（口径还不一样），正是方案 §二 F6 说的"两处各算一遍"的病根。
 * 因此本 Job 只做一件事：把"按落账时限已超时"的在办任务<b>聚合成可见读数</b>（日志 + 最近报告接口），
 * 让运维能核对"双时限是否真的在生效"，动作仍走原通道。
 *
 * <p><b>租户铁律</b>：跨租户聚合扫描用 {@link TenantContext#runIgnoreTenant}（只读、不落库），
 * 逐租户统计时 {@link TenantContext#callAs} 恢复上下文——否则租户过滤失效、误把别家单子算进来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskDueTimeScanJob {

    private final WfTaskMapper taskMapper;
    private final TaskTimeLimitService timeLimitService;
    private final JobHeartbeatService jobHeartbeatService;

    /** 最近一次报告（volatile：调度线程写、HTTP 线程读） */
    private volatile Map<String, Object> lastReport = Map.of();

    /** 每租户报告里保留的超时明细条数上限 */
    private static final int SAMPLE_PER_TENANT = 10;

    /**
     * 每 5 分钟扫一次。为什么不是 60 秒：这是"读数型"任务，扫的是全租户在办任务，
     * 5 分钟的可见性延迟对运维核对完全够用，没必要每分钟给数据库加一次全表过滤。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void scan() {
        jobHeartbeatService.run("OpTaskDueTimeScanJob", () -> {
            try {
                Set<Long> tenantIds = tenantsWithOverdue();
                if (tenantIds.isEmpty()) {
                    lastReport = Map.of("scannedAt", LocalDateTime.now(), "overdueTenantCount", 0,
                            "note", "无租户存在按 due_time 已超时的在办任务");
                    return;
                }
                Map<String, Object> perTenant = new LinkedHashMap<>();
                int overdueTotal = 0;
                int pendingTotal = 0;
                int noDueTotal = 0;
                for (Long tenantId : tenantIds) {
                    try {
                        TenantContext.callAs(tenantId, () -> {
                            Map<String, Object> r = timeLimitService.dueScan(SAMPLE_PER_TENANT);
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("pendingTotal", r.get("pendingTotal"));
                            row.put("overdueTotal", r.get("overdueTotal"));
                            row.put("noDueTimeCount", r.get("noDueTimeCount"));
                            row.put("sample", r.get("overdue"));
                            perTenant.put(String.valueOf(tenantId), row);
                        });
                    } catch (Exception ex) {
                        log.error("G3 超时扫描：租户 {} 统计失败", tenantId, ex);
                    }
                }
                for (Object v : perTenant.values()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) v;
                    overdueTotal += num(row.get("overdueTotal"));
                    pendingTotal += num(row.get("pendingTotal"));
                    noDueTotal += num(row.get("noDueTimeCount"));
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("scannedAt", LocalDateTime.now());
                out.put("overdueTenantCount", perTenant.size());
                out.put("pendingTotal", pendingTotal);
                out.put("overdueTotal", overdueTotal);
                out.put("noDueTimeCount", noDueTotal);
                out.put("byTenant", perTenant);
                out.put("note", "超时判定唯一真值为 wf_task.due_time（G3 双时限落账）；"
                        + "本任务只报不修，催办/超时自动通过仍由 WfTimeoutJob 按节点 timeout_hours 执行");
                lastReport = out;
                if (overdueTotal > 0) {
                    log.warn("G3 时限超时扫描：{} 个租户共 {} 条在办任务已超时（无 due_time 存量 {} 条）",
                            perTenant.size(), overdueTotal, noDueTotal);
                } else {
                    log.info("G3 时限超时扫描：无超时在办任务（无 due_time 存量 {} 条）", noDueTotal);
                }
            } catch (Exception ex) {
                log.error("G3 时限超时扫描异常", ex);
            }
        });
    }

    /**
     * 跨租户只读扫描：找出存在"PENDING 且 due_time 已过"任务的租户。
     * 只 select tenant_id 一列，不把任务正文读进内存。
     */
    private Set<Long> tenantsWithOverdue() {
        List<WfTask>[] holder = new List[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .select(WfTask::getTenantId)
                        .eq(WfTask::getStatus, "PENDING")
                        .isNotNull(WfTask::getDueTime)
                        .lt(WfTask::getDueTime, LocalDateTime.now())));
        List<WfTask> rows = holder[0];
        Set<Long> ids = new LinkedHashSet<>();
        if (rows != null) {
            for (WfTask t : rows) {
                if (t.getTenantId() != null) {
                    ids.add(t.getTenantId());
                }
            }
        }
        ids.removeIf(Objects::isNull);
        return ids;
    }

    private int num(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** 最近一次扫描报告（只读，供 {@code GET /api/tenant/wf/ops/time-limit/scan-job-report}） */
    public Map<String, Object> lastReport() {
        return lastReport.isEmpty() ? Map.of("note", "本实例尚未执行过扫描（或首轮还未完成）") : lastReport;
    }
}
