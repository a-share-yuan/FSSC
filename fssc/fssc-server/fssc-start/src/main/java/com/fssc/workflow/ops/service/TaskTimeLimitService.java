// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import com.fssc.workflow.ops.entity.OpTaskTimeLimit;
import com.fssc.workflow.ops.mapper.OpTaskTimeLimitMapper;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WorkCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G3普通/加急双时限：计算并回写 {@code wf_task.due_time}，让它成为真值。
 *
 * <p><b>为什么必须做这件事</b>：方案 §二 F6 指出 {@code wf_task.due_time} 是"写了没人读的死列"——
 * 全仓只有一处 {@code setDueTime} 写入、零处读取，真超时判断在 {@code WfTimeoutJob} 里用
 * {@code create_time + timeout_hours} 现算。本类做两件事把该列变成真值：
 * <ol>
 *   <li><b>写</b>：按优先级（NORMAL/URGENT）取节点级策略的时限，算出 {@code due_time} 并写回
 *       {@code wf_task.due_time}，同时在 {@code op_task_time_limit} 留一份可核对的副本；</li>
 *   <li><b>读</b>：{@link #dueScan} 与任务池视图都<b>直接读 {@code due_time}</b> 判超时，
 *       不再用 create_time 现算（规则漂移的根源就是"两处各算一遍"）。</li>
 * </ol>
 *
 * <p><b>规则边界（G2 第 3 条已办结）</b>：到期时间改由 {@link WorkCalendarService} 按<b>工作小时</b>
 * 计算（跨周末/节假日顺延），与 BPMN 边界定时器共用同一份日历实现——此前"门面按自然小时算、
 * 定时器按 {@code PT{N}H} 算"的两处漂移即由此消除。
 * <p><b>按组织取日历</b>：本类按单据所属<b>公司</b>（{@code NodeOrgResolver.companyIdOfBill}）
 * 把组织下传给日历服务；<b>解析不到组织（或组织列尚未建）时按 {@code null} 调用，回落租户规则</b>，
 * 与改造前逐字一致。组织维＝公司（见 {@code WfManageOrgScope} 类注释）；组织列由
 * {@code fssc/sql.2.0_g2_calendar_org_20260924.sql} 新增，服务端在列不存在时自动降级。
 * <p>代价（如实说明）：解析组织需按 {@code bizType/bizId} 反查单据表（公司列，取不到再按建单人默认处理），
 * 单任务路径一次查询可接受；{@link #backfillMissingDueTime} 批量补账时逐任务解析，属一次性维护动作
 * 上限 2000 条，成本可接受但非零。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTimeLimitService {

    private final OpTaskTimeLimitMapper timeLimitMapper;
    private final WfTaskMapper taskMapper;
    private final DispatchPolicyService policyService;
    /** G2 第 3 条：到期时间按共享工作日历（op_calendar）以"工作小时"计算 */
    private final WorkCalendarService workCalendarService;
    /**
     * G2 第 3 条：单据所属组织（公司）解析——{@code companyIdOfBill} 为全库唯一组织维解析入口
     * （规则同 NodeOrgResolver/WfManageOrgScope）。解析不到返回 null，日历服务会回落租户规则。
     */
    private final NodeOrgResolver nodeOrgResolver;

    /** 优先级的两个取值（与 op_task_time_limit.priority 列注释一致） */
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_URGENT = "URGENT";

    /**
     * 计算到期时间：{@code 任务创建时间 + 该优先级的时限工作小时}。
     * 创建时间为空时用当前时间默认处理（宁可给出一个偏晚的到期时间，也不留空导致"永不超时"）。
     *
     * <p>G2 第 3 条：分钟数交由 {@link WorkCalendarService} 按工作日历推进（休息日/节假日整天跳过，
     * 每日只在在岗时段内计工时），而不是简单 {@code plusMinutes}。日历读取失败时该服务内部已
     * 退化为自然分钟，故这里不需要再兜一层。
     */
    public LocalDateTime calcDueTime(LocalDateTime createTime, BigDecimal limitHours) {
        return calcDueTime(createTime, limitHours, null);
    }

    /**
     * 计算到期时间（<b>按组织（公司）取工作日历</b>，G2 第 3 条）。
     *
     * @param companyId 单据所属公司 ID；{@code null} = 解析不到组织，回落租户规则（与旧重载逐字一致）
     * @see #calcDueTime(LocalDateTime, BigDecimal)
     */
    public LocalDateTime calcDueTime(LocalDateTime createTime, BigDecimal limitHours, Long companyId) {
        LocalDateTime base = createTime == null ? LocalDateTime.now() : createTime;
        BigDecimal hours = limitHours == null ? new BigDecimal("24") : limitHours;
        long minutes = hours.multiply(new BigDecimal("60")).setScale(0, RoundingMode.HALF_UP).longValue();
        return workCalendarService.plusWorkingMinutes(base, minutes, companyId);
    }

    /**
     * 单据所属组织（公司）：{@code bizType/bizId} → 单据表 company_id（取不到再按建单人默认处理）；
     * 解析不到一律返回 {@code null}，调用方按租户规则处理（零影响）。
     */
    private Long resolveOrg(WfTask task) {
        if (task == null) {
            return null;
        }
        try {
            return nodeOrgResolver.companyIdOfBill(task.getBizType(), task.getBizId());
        } catch (Exception ex) {
            log.warn("按组织取日历失败，回落租户口径 taskId={}: {}", task.getTaskId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 给任务打优先级并落双时限（去重：同一任务重复调用只更新不改写历史）。
     *
     * <p>做三件事：① 解析该节点的生效策略；② 按优先级取时限算出 due_time；
     * ③ 写回 {@code wf_task.due_time}（既有列，成为真值）＋ upsert {@code op_task_time_limit}（可核对副本）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpTaskTimeLimit markPriority(Long taskRowId, String priority) {
        if (taskRowId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务行 ID 必填");
        }
        WfTask task = taskMapper.selectById(taskRowId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "待办任务不存在: " + taskRowId);
        }
        return applyTimeLimit(task, priority);
    }

    /**
     * 按任务行打优先级（内部/批量入口）。抢单、自动分配、超时扫描都走这一个方法，
     * 保证 due_time 只有一份算法。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpTaskTimeLimit applyTimeLimit(WfTask task, String priority) {
        String p = OpTaskTimeLimit.normalizePriority(priority);
        OpDispatchPolicy policy = policyService.resolvePolicy(task.getModelKey(), task.getNodeKey());
        BigDecimal hours = policyService.limitHours(policy, p);
        // G2 第 3 条：按单据所属公司取工作日历；解析不到组织时 resolveOrg 返回 null → 回落租户规则
        LocalDateTime due = calcDueTime(task.getCreateTime(), hours, resolveOrg(task));

        // 写回 wf_task.due_time —— 死列变真值的唯一写点
        WfTask upd = new WfTask();
        upd.setId(task.getId());
        upd.setDueTime(due);
        upd.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(upd);

        OpTaskTimeLimit row = timeLimitMapper.selectOne(new LambdaQueryWrapper<OpTaskTimeLimit>()
                .eq(OpTaskTimeLimit::getTaskId, task.getTaskId())
                .last("limit 1"));
        if (row == null) {
            row = new OpTaskTimeLimit();
            row.setTaskId(task.getTaskId());
            row.setProcInstId(task.getProcInstId());
            row.setModelKey(task.getModelKey());
            row.setNodeKey(task.getNodeKey());
            row.setPriority(p);
            row.setPolicyId(policy.getId());
            row.setLimitHours(hours);
            row.setDueTime(due);
            timeLimitMapper.insert(row);
        } else {
            row.setPriority(p);
            row.setPolicyId(policy.getId());
            row.setLimitHours(hours);
            row.setDueTime(due);
            timeLimitMapper.updateById(row);
        }
        log.info("G3 任务时限落账: taskId={}, priority={}, limit={}h, dueTime={}, 策略来源={}",
                task.getTaskId(), p, hours, due,
                policy.getId() == null ? "内置兜底" : "policy#" + policy.getId());
        return row;
    }

    /** 任务当前优先级（无落账记录时按普通） */
    public String priorityOf(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return PRIORITY_NORMAL;
        }
        OpTaskTimeLimit row = timeLimitMapper.selectOne(new LambdaQueryWrapper<OpTaskTimeLimit>()
                .eq(OpTaskTimeLimit::getTaskId, taskId)
                .last("limit 1"));
        return row == null ? PRIORITY_NORMAL : OpTaskTimeLimit.normalizePriority(row.getPriority());
    }

    /** 批量取优先级（任务池视图用，避免逐行查库） */
    public Map<String, String> priorityIndex(List<String> taskIds) {
        Map<String, String> out = new LinkedHashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return out;
        }
        // 10-29 万级在办实测：IN 参数按 1000 一批，避免 3 万+ 参数的单条 SQL 撑爆解析器。
        for (int i = 0; i < taskIds.size(); i += 1000) {
            List<String> chunk = taskIds.subList(i, Math.min(i + 1000, taskIds.size()));
            List<OpTaskTimeLimit> rows = timeLimitMapper.selectList(new LambdaQueryWrapper<OpTaskTimeLimit>()
                    .in(OpTaskTimeLimit::getTaskId, chunk));
            for (OpTaskTimeLimit r : rows) {
                out.put(r.getTaskId(), OpTaskTimeLimit.normalizePriority(r.getPriority()));
            }
        }
        return out;
    }

    /**
     * 超时扫描（<b>直接读 {@code wf_task.due_time} 真值</b>，不再 create_time 现算）。
     *
     * <p>只报不修：返回超时任务清单与统计，是否催办/升级由既有 {@code WfTimeoutJob} 链路决定。
     * {@code due_time} 为空的行单独计数——它们是"尚未落时限"的历史历史，
     * 由 {@code /ops/time-limit/backfill} 补账，不在这里静默补。
     */
    public Map<String, Object> dueScan(int limit) {
        int cap = limit <= 0 ? 200 : Math.min(limit, 2000);
        LocalDateTime now = LocalDateTime.now();
        // 万级在办实测：原写法把全部 PENDING 行（4.4 万，含 30 列正文）读进内存
        // 再逐条判超时，本 Job 每 5 分钟一次，实测 73,692 ms，期间报表/待办请求被挤到 20~240 s。
        // 本方法只对外报「三个计数 + 最早期限的 cap 条样本」，故三个计数走 count(*)、样本走
        // due_time 升序 limit cap：与原实现「按超时小时数降序取前 cap」同序（due_time 升序 ⇔ 超时越久越靠前），
        // 且彻底不再把在办全量装进堆。
        Long pendingTotal = taskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING"));
        Long noDueL = taskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .isNull(WfTask::getDueTime));
        Long overdueTotalL = taskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .isNotNull(WfTask::getDueTime)
                .lt(WfTask::getDueTime, now));
        int noDue = noDueL == null ? 0 : noDueL.intValue();
        int overdueTotal = overdueTotalL == null ? 0 : overdueTotalL.intValue();
        List<WfTask> overdueTasks = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .isNotNull(WfTask::getDueTime)
                .lt(WfTask::getDueTime, now)
                .orderByAsc(WfTask::getDueTime)
                .last("limit " + cap));
        // 优先级仍走批量 IN（每 1000 一批），只是样本最多 cap 条；无落账记录的任务按「普通」。
        List<String> overdueIds = new ArrayList<>(overdueTasks.size());
        for (WfTask t : overdueTasks) {
            overdueIds.add(t.getTaskId());
        }
        Map<String, String> priorities = priorityIndex(overdueIds);
        List<Map<String, Object>> overdue = new ArrayList<>(overdueTasks.size());
        for (WfTask t : overdueTasks) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("taskId", t.getTaskId());
            one.put("procInstId", t.getProcInstId());
            one.put("modelKey", t.getModelKey());
            one.put("nodeKey", t.getNodeKey());
            one.put("nodeName", t.getNodeName());
            one.put("bizNo", t.getBizNo());
            one.put("assigneeId", t.getAssigneeId());
            one.put("dueTime", t.getDueTime());
            one.put("overdueHours", Duration.between(t.getDueTime(), now).toHours());
            one.put("priority", priorities.getOrDefault(t.getTaskId(), PRIORITY_NORMAL));
            overdue.add(one);
        }
        overdue.sort(Comparator.comparingLong(o -> -((Number) o.get("overdueHours")).longValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scannedAt", now);
        out.put("pendingTotal", pendingTotal == null ? 0 : pendingTotal.intValue());
        out.put("overdueTotal", overdueTotal);
        out.put("noDueTimeCount", noDue);
        out.put("overdue", overdue.size() > cap ? overdue.subList(0, cap) : overdue);
        out.put("note", noDue > 0
                ? "有 " + noDue + " 条「待处理」任务尚无应完成时间（历史遗留），请调用 /api/tenant/wf/ops/time-limit/backfill 补账"
                : "全部 PENDING 任务均有 due_time，超时判定以该列为唯一真值");
        return out;
    }

    /**
     * 补账：给尚无 {@code due_time} 的 PENDING 任务按当前策略补算时限（去重，可重复执行）。
     * 上限 {@code cap} 条，避免一次请求扫全表。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> backfillMissingDueTime(int cap) {
        int limit = cap <= 0 ? 200 : Math.min(cap, 2000);
        List<WfTask> rows = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .isNull(WfTask::getDueTime)
                .orderByAsc(WfTask::getId)
                .last("limit " + limit));
        int done = 0;
        for (WfTask t : rows) {
            applyTimeLimit(t, priorityOf(t.getTaskId()));
            done++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backfilled", done);
        out.put("note", "按节点生效策略的普通/加急时限补算 due_time；本接口可重复调用，直到返回 0 条");
        return out;
    }
}
