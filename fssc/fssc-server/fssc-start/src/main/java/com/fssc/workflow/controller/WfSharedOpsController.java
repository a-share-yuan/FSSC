// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.ops.dto.AssignRequest;
import com.fssc.workflow.ops.dto.RuleHit;
import com.fssc.workflow.ops.entity.OpDispatchLog;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import com.fssc.workflow.ops.entity.OpTaskTimeLimit;
import com.fssc.workflow.ops.job.OpTaskDueTimeScanJob;
import com.fssc.workflow.ops.service.DispatchPolicyService;
import com.fssc.workflow.ops.service.TaskAssignmentService;
import com.fssc.workflow.ops.service.TaskQualityReportService;
import com.fssc.workflow.ops.service.TaskTimeLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * G3（2026-09-24）共享作业层（{@code /api/tenant/wf/ops/**}）：分配策略四模式 ＋ 双时限 ＋
 * 分配规则顺序解析 ＋ 自动分配日志 ＋ 批退原因/质量报表。
 *
 * <p><b>权限码的选择（为什么不新造 wf:ops:*）</b>：新造权限码意味着上线后所有角色都拿不到，
 * 页面直接 403；本组接口按"语义同族复用既有码"落地——配置类复用 {@code wf:config:*}，
 * 执行类复用 {@code wf:task:reassign}（改派/自动分配同属"替别人定办理人"），
 * 聚合读数复用 {@code wf:instance:list}（与 {@code /wf/perf} 同档），
 * 待办查询复用 {@code wf:task:list}。若后续要拆细粒度权限，只需替换注解值，不影响业务逻辑。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf/ops")
@RequiredArgsConstructor
@Validated
public class WfSharedOpsController {

    private final DispatchPolicyService policyService;
    private final TaskAssignmentService assignmentService;
    private final TaskTimeLimitService timeLimitService;
    private final TaskQualityReportService qualityService;
    private final OpTaskDueTimeScanJob dueTimeScanJob;

    // ------------------------------------------------------------------
    // 1. 分配策略配置（四模式 + 普通/加急双时限）
    // ------------------------------------------------------------------

    /** 策略配置分页 */
    @RequiresPerm("wf:config:list")
    @GetMapping("/policy/page")
    public Result<PageResult<OpDispatchPolicy>> policyPage(PageQuery q,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String strategy) {
        return Result.success(policyService.page(q, keyword, strategy));
    }

    /** 策略配置详情 */
    @RequiresPerm("wf:config:list")
    @GetMapping("/policy/{id}")
    public Result<OpDispatchPolicy> policyGet(@PathVariable Long id) {
        return Result.success(policyService.get(id));
    }

    /**
     * 某节点当前<b>生效</b>的策略（只读，不落库）：节点级 → 全局默认行 → 内置兜底（抢单 + 24h/4h）。
     * 让运营能直接回答"这条流程的这个节点到底按什么策略在派单"。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/policy/effective")
    public Result<OpDispatchPolicy> policyEffective(@RequestParam(required = false) String modelKey,
                                                    @RequestParam(required = false) String nodeKey) {
        return Result.success(policyService.resolvePolicy(modelKey, nodeKey));
    }

    /** 新增策略配置（同一 modelKey:nodeKey 只允许一条，避免两条互相矛盾的策略并存） */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_OPS", operType = "INSERT", desc = "新增分配策略")
    @PostMapping("/policy/create")
    public Result<Long> policyCreate(@RequestBody OpDispatchPolicy policy) {
        return Result.success(policyService.create(policy));
    }

    /** 修改策略配置 */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_OPS", operType = "UPDATE", desc = "修改分配策略")
    @PostMapping("/policy/update")
    public Result<Void> policyUpdate(@RequestBody OpDispatchPolicy policy) {
        policyService.update(policy);
        return Result.success();
    }

    /** 删除策略配置（删除后该节点回落到全局默认行） */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_OPS", operType = "DELETE", desc = "删除分配策略")
    @PostMapping("/policy/{id}/delete")
    public Result<Void> policyDelete(@PathVariable Long id) {
        policyService.delete(id);
        return Result.success();
    }

    // ------------------------------------------------------------------
    // 2. 分配规则顺序解析（只读试算）
    // ------------------------------------------------------------------

    /**
     * 规则顺序解析试算（只读）：按 {@code priority} 升序逐条评估、命中即止，
     * 返回命中的规则与<b>完整评估轨迹</b>（含未命中的原因），回答"这张单为什么没走那条规则"。
     */
    @RequiresPerm("wf:task:list")
    @GetMapping("/rule/resolve")
    public Result<Map<String, Object>> ruleResolve(@RequestParam(required = false) String taskType,
                                                   @RequestParam(required = false) String bizType,
                                                   @RequestParam(required = false) BigDecimal amount,
                                                   @RequestParam(required = false) Long companyId,
                                                   @RequestParam(required = false) Long expenseItemId,
                                                   @RequestParam(required = false) String currencyCode,
                                                   @RequestParam(required = false) Long legalEntityId) {
        TaskAssignmentService.RuleResolution r = assignmentService.resolveRule(
                taskType, bizType, amount, companyId, expenseItemId, currencyCode, legalEntityId);
        List<RuleHit> trace = r.trace();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("matchedRuleId", r.matched() == null ? null : r.matched().getId());
        out.put("matchedRuleCode", r.matched() == null ? null : r.matched().getRuleCode());
        out.put("matchedRuleName", r.matched() == null ? null : r.matched().getRuleName());
        out.put("evaluatedCount", trace.size());
        out.put("trace", trace);
        out.put("note", "按 priority 升序评估，命中即止；未命中的规则同样进轨迹并给出原因");
        return Result.success(out);
    }

    // ------------------------------------------------------------------
    // 3. 自动分配（试算 / 执行）＋ 自动分配日志
    // ------------------------------------------------------------------

    /**
     * 自动分配：{@code dryRun=true} 只试算不落库（前端可先给运营看决策依据）；
     * 否则执行指派并落 {@code op_dispatch_log}。抢单（PICK）策略不指派，任务留池待领取。
     */
    @RequiresPerm("wf:task:reassign")
    @OperLog(module = "WF_OPS", operType = "UPDATE", desc = "自动分配任务")
    @PostMapping("/assign")
    public Result<Map<String, Object>> assign(@RequestBody AssignRequest req) {
        return Result.success(assignmentService.autoAssign(req));
    }

    /** 自动分配日志分页（"谁在什么时候被谁派了哪条单、为什么"） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/dispatch-log/page")
    public Result<PageResult<OpDispatchLog>> dispatchLogPage(PageQuery q,
                                                            @RequestParam(required = false) String taskId,
                                                            @RequestParam(required = false) String bizNo,
                                                            @RequestParam(required = false) String strategy) {
        return Result.success(assignmentService.logPage(q, taskId, bizNo, strategy));
    }

    /** 决策链：某一任务的全部分配决策记录（时间正序，读成一条链） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/dispatch-log/chain")
    public Result<List<OpDispatchLog>> decisionChain(@RequestParam String taskId) {
        return Result.success(assignmentService.decisionChain(taskId));
    }

    // ------------------------------------------------------------------
    // 4. 双时限（优先级标记 / 补账 / 超时扫描）
    // ------------------------------------------------------------------

    /**
     * 给任务打优先级并落双时限：写回 {@code wf_task.due_time}（既有列，成为唯一真值）
     * ＋ upsert {@code op_task_time_limit}（可核对副本）。幂等，可重复调用。
     */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_OPS", operType = "UPDATE", desc = "标记任务优先级与时限")
    @PostMapping("/time-limit/mark")
    public Result<OpTaskTimeLimit> markPriority(@RequestParam Long taskRowId,
                                                @RequestParam(defaultValue = "NORMAL") String priority) {
        return Result.success(timeLimitService.markPriority(taskRowId, priority));
    }

    /** 补账：给尚无 {@code due_time} 的历史存量 PENDING 任务补算时限（幂等，可重复调用直到 0 条） */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_OPS", operType = "UPDATE", desc = "补算任务时限")
    @PostMapping("/time-limit/backfill")
    public Result<Map<String, Object>> backfillDueTime(@RequestParam(defaultValue = "200") int cap) {
        return Result.success(timeLimitService.backfillMissingDueTime(cap));
    }

    /** 超时扫描（只读）：直接读 {@code wf_task.due_time} 真值，列出超时任务与统计 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/time-limit/due-scan")
    public Result<Map<String, Object>> dueScan(@RequestParam(defaultValue = "200") int limit) {
        return Result.success(timeLimitService.dueScan(limit));
    }

    /** 定时超时扫描任务最近一次报告（只读；与 {@code /wf/perf/ledger-reconcile} 同一"只报不修"风格） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/time-limit/scan-job-report")
    public Result<Map<String, Object>> scanJobReport() {
        return Result.success(dueTimeScanJob.lastReport());
    }

    // ------------------------------------------------------------------
    // 5. 批退原因 / 任务质量报表（口径见 TaskQualityReportService）
    // ------------------------------------------------------------------

    /** 质量总览：批退率、一次通过率、平均节点停留、批退原因 Top 榜 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/quality/summary")
    public Result<Map<String, Object>> qualitySummary(@RequestParam(defaultValue = "30") int days) {
        return Result.success(qualityService.summary(days));
    }

    /** 按节点质量：办理量/批退量/批退率/平均停留 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/quality/nodes")
    public Result<List<Map<String, Object>>> qualityByNode(@RequestParam(defaultValue = "30") int days,
                                                          @RequestParam(defaultValue = "30") int limit) {
        return Result.success(qualityService.byNode(days, limit));
    }

    /** 按办理人质量：办理量/批退量/批退率/平均停留 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/quality/handlers")
    public Result<List<Map<String, Object>>> qualityByHandler(@RequestParam(defaultValue = "30") int days,
                                                             @RequestParam(defaultValue = "30") int limit) {
        return Result.success(qualityService.byHandler(days, limit));
    }

    /** 批退原因分布（按审批意见原文归桶） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/quality/reject-reasons")
    public Result<List<Map<String, Object>>> rejectReasons(@RequestParam(defaultValue = "30") int days,
                                                           @RequestParam(required = false) String nodeKey,
                                                           @RequestParam(defaultValue = "20") int limit) {
        return Result.success(qualityService.rejectReasons(days, nodeKey, limit));
    }

    /** 批退明细分页（质量抽查逐条看意见原文） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/quality/reject/page")
    public Result<PageResult<Map<String, Object>>> rejectPage(PageQuery q,
                                                             @RequestParam(defaultValue = "30") int days,
                                                             @RequestParam(required = false) String nodeKey,
                                                             @RequestParam(required = false) String keyword) {
        return Result.success(qualityService.rejectPage(q, days, nodeKey, keyword));
    }
}
