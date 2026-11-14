package com.fssc.workflow.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.compliance.mapper.ReviewerMapper;
import com.fssc.sharing.entity.OpDispatchRule;
import com.fssc.sharing.entity.ShGroup;
import com.fssc.sharing.mapper.OpDispatchRuleMapper;
import com.fssc.sharing.mapper.ShGroupMapper;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.ops.dto.AssignRequest;
import com.fssc.workflow.ops.dto.AssignmentDecision;
import com.fssc.workflow.ops.dto.CandidateLoad;
import com.fssc.workflow.ops.dto.RuleHit;
import com.fssc.workflow.ops.entity.OpDispatchLog;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import com.fssc.workflow.ops.entity.OpTaskTimeLimit;
import com.fssc.workflow.ops.mapper.OpDispatchLogMapper;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * G3分配规则顺序解析 ＋ 自动分配执行 ＋ 自动分配日志。
 *
 * <p>{@code 分配规则表}「条件命中→处理人，多条件逐个解析直到命中」，
 * 复用既有 {@code op_dispatch_rule} 表（该表在 {@code fssc/db/Dump20260911/fssc_dispatch_rule}
 * 中已有真实列定义，本类不新增列）：
 * <ol>
 *   <li><b>顺序解析</b>：按 {@code priority} <b>升序</b>逐条评估，命中即止，
 *       未命中的规则也进轨迹（{@link RuleHit}），使"为什么没走那条规则"可回答。
 *       既有 {@code DispatchService.findMatchingRule} 用的是 {@code orderByDesc(priority)}，
 *       与本规则相反；本类不改动它，只在新链路上按"数值小优先"实现，差异已如实标注。</li>
 *   <li><b>条件维度</b>：类型化列（task_type/company_id/expense_item_id/currency_code，为空＝不限制）
 *       与「维度＋条件」对（TYPE/AMOUNT/LEGAL/COMPLEXITY/REGION）两类条件<b>取与</b>；
 *       金额区间支持 {@code "min,max"} 与 {@code "threshold"} 两种写法。</li>
 *   <li><b>处理人</b>：规则的 {@code target_group_id} → {@code reviewer} 表在岗成员 →
 *       交给 {@link DispatchPolicyService#decide} 按四模式策略选人。</li>
 * </ol>
 *
 * <p><b>执行规则</b>：策略为 PICK 时<b>不指派</b>，任务留在池里待领取（这是抢单语义）；
 * 其余策略把 Flowable 受理人改成选中人并同步镜像。机器路径不做人工校验校验
 * （与方案 §二 F4 描述的既有机器路径同一取舍：自动分配不是"人办节点"，没有越权概念）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssignmentService {

    private final OpDispatchRuleMapper ruleMapper;
    private final ShGroupMapper groupMapper;
    private final ReviewerMapper reviewerMapper;
    private final WfTaskMapper taskMapper;
    private final OpDispatchLogMapper logMapper;
    private final DispatchPolicyService policyService;
    private final TaskTimeLimitService timeLimitService;
    private final WfQueryService wfQueryService;
    private final TaskService taskService;

    // 1. 规则顺序解析

    /**
     * 按顺序解析分配规则：返回第一条命中的规则（无命中返回 null），
     * 轨迹里含全部被评估过的规则（含未命中原因）。
     *
     * @param taskType 共享作业项
     * @param bizType 单据类型
     * @param amount 金额
     * @param companyId 公司
     * @param expenseItemId 费用项目
     * @param currencyCode 币种
     * @param legalEntityId 法人主体
     */
    public RuleResolution resolveRule(String taskType, String bizType, BigDecimal amount, Long companyId,
                                     Long expenseItemId, String currencyCode, Long legalEntityId) {
        List<OpDispatchRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<OpDispatchRule>()
                .eq(OpDispatchRule::getStatus, "NORMAL")
                .orderByAsc(OpDispatchRule::getPriority)
                .orderByAsc(OpDispatchRule::getId));
        List<RuleHit> trace = new ArrayList<>();
        OpDispatchRule hit = null;
        for (OpDispatchRule rule : rules) {
            String reason;
            boolean matched;
            if (hit != null) {
                matched = false;
                reason = "已命中更高优先级规则，按「命中即止」不再评估";
            } else {
                reason = evaluate(rule, taskType, bizType, amount, companyId, expenseItemId, currencyCode, legalEntityId);
                matched = reason == null;
                if (matched) {
                    hit = rule;
                    reason = "条件全部满足，命中";
                }
            }
            trace.add(new RuleHit(rule.getId(), rule.getRuleCode(), rule.getRuleName(), rule.getPriority(),
                    rule.getDispatchDimension(), rule.getMatchCondition(), matched, reason));
        }
        return new RuleResolution(hit, trace);
    }

    /** 规则解析结果：命中的规则（可空）＋ 完整评估轨迹 */
    public record RuleResolution(OpDispatchRule matched, List<RuleHit> trace) {
    }

    /** 返回 null 表示命中，否则返回未命中原因 */
    private String evaluate(OpDispatchRule rule, String taskType, String bizType, BigDecimal amount,
                            Long companyId, Long expenseItemId, String currencyCode, Long legalEntityId) {
        if (notBlank(rule.getTaskType()) && !rule.getTaskType().equalsIgnoreCase(trim(taskType))) {
            return "作业项不符：规则=" + rule.getTaskType() + "，单据=" + taskType;
        }
        if (rule.getCompanyId() != null && !rule.getCompanyId().equals(companyId)) {
            return "公司不符：规则=" + rule.getCompanyId() + "，单据=" + companyId;
        }
        if (rule.getExpenseItemId() != null && !rule.getExpenseItemId().equals(expenseItemId)) {
            return "费用项目不符：规则=" + rule.getExpenseItemId() + "，单据=" + expenseItemId;
        }
        if (notBlank(rule.getCurrencyCode()) && !rule.getCurrencyCode().equalsIgnoreCase(trim(currencyCode))) {
            return "币种不符：规则=" + rule.getCurrencyCode() + "，单据=" + currencyCode;
        }
        String dimension = trim(rule.getDispatchDimension());
        String condition = trim(rule.getMatchCondition());
        if (!notBlank(dimension) || !notBlank(condition)) {
            return null;   // 只配了类型化列，视为命中（与既有 DispatchService 同一规则）
        }
        return switch (dimension.toUpperCase()) {
            case "TYPE" -> condition.equalsIgnoreCase(trim(bizType)) || condition.equalsIgnoreCase(trim(taskType))
                    ? null : "类型条件不符：条件=" + condition + "，单据类型=" + bizType + "/作业项=" + taskType;
            case "AMOUNT" -> matchAmount(condition, amount);
            case "LEGAL" -> {
                if (legalEntityId == null) {
                    yield "法人主体条件需要单据提供法人主体，但本次未提供";
                }
                yield condition.equals(String.valueOf(legalEntityId))
                        ? null : "法人主体不符：条件=" + condition + "，单据=" + legalEntityId;
            }
            case "COMPLEXITY", "REGION" -> null;   // 复杂度/区域维度尚无数据来源，不参与判定（不臆造条件）
            default -> null;
        };
    }

    private String matchAmount(String condition, BigDecimal amount) {
        if (amount == null) {
            return "金额条件需要单据金额，但本次未提供";
        }
        try {
            String[] parts = condition.split(",");
            if (parts.length == 1) {
                BigDecimal threshold = new BigDecimal(parts[0].trim());
                return amount.compareTo(threshold) >= 0 ? null
                        : "金额未达阈值：条件>=" + threshold + "，单据=" + amount;
            }
            if (parts.length == 2) {
                BigDecimal min = new BigDecimal(parts[0].trim());
                BigDecimal max = new BigDecimal(parts[1].trim());
                return amount.compareTo(min) >= 0 && amount.compareTo(max) <= 0 ? null
                        : "金额不在区间：条件[" + min + "," + max + "]，单据=" + amount;
            }
            return "金额条件格式无法解析：" + condition;
        } catch (NumberFormatException e) {
            return "金额条件格式无法解析：" + condition;
        }
    }

    // 2. 自动分配（试算 / 执行）

    /**
     * 自动分配：规则顺序解析 → 目标分组 → 候选画像 → 策略选人 → （非试算时）指派 + 落日志。
     *
     * @return 决策明细（含规则轨迹、候选快照、决策理由、是否写入数据库）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoAssign(AssignRequest req) {
        if (req == null || req.taskRowId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskRowId（wf_task.id）必填");
        }
        WfTask task = taskMapper.selectById(req.taskRowId());
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "待办任务不存在: " + req.taskRowId());
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "任务当前状态不允许再分配（仅待办任务可分配）");
        }

        BigDecimal amount = req.amount() != null ? req.amount() : task.getAmount();
        RuleResolution resolution = resolveRule(req.taskType(), task.getBizType(), amount, req.companyId(),
                req.expenseItemId(), req.currencyCode(), req.legalEntityId());

        OpDispatchPolicy policy = policyService.resolvePolicy(task.getModelKey(), task.getNodeKey());
        String priority = OpTaskTimeLimit.normalizePriority(req.priority());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", req.isDryRun());
        out.put("taskId", task.getTaskId());
        out.put("taskRowId", task.getId());
        out.put("modelKey", task.getModelKey());
        out.put("nodeKey", task.getNodeKey());
        out.put("priority", priority);
        out.put("ruleTrace", resolution.trace());
        out.put("matchedRuleCode", resolution.matched() == null ? null : resolution.matched().getRuleCode());
        out.put("policyId", policy.getId());
        out.put("strategy", policy.normalizedStrategy());

        if (resolution.matched() == null) {
            // 无规则命中：不擅自改派，保持原态（候选组/池），只如实报"无匹配规则"
            AssignmentDecision none = new AssignmentDecision(policy.normalizedStrategy(), policy.getId(),
                    null, null, "", "无分配规则命中（已按优先级升序评估 " + resolution.trace().size()
                    + " 条规则），保持任务原态不做指派");
            out.put("decision", none);
            if (!req.isDryRun()) {
                writeLog(task, resolution, policy, none, priority, null, 0);
            }
            return out;
        }

        OpDispatchRule rule = resolution.matched();
        ShGroup group = rule.getTargetGroupId() == null ? null : groupMapper.selectById(rule.getTargetGroupId());
        List<Long> candidateUserIds = candidateUserIds(rule.getTargetGroupId());
        List<CandidateLoad> loads = policyService.loadCandidates(candidateUserIds);
        AssignmentDecision decision = policyService.decide(policy, priority, loads, LocalDateTime.now());

        out.put("targetGroupId", rule.getTargetGroupId());
        out.put("targetGroupName", group == null ? null : group.getGroupName());
        out.put("candidates", loads);
        out.put("decision", decision);

        if (req.isDryRun()) {
            out.put("applied", false);
            return out;
        }

        boolean applied = false;
        if (decision.assigned()) {
            applyAssignee(task, decision.chosenUserId());
            applied = true;
            // 时限落账（写 due_time + op_task_time_limit）：指派后立即起算，与"任务到达待办列表开始倒计时"同一规则
            OpTaskTimeLimit limit = timeLimitService.applyTimeLimit(task, priority);
            out.put("dueTime", limit.getDueTime());
            out.put("limitHours", limit.getLimitHours());
        } else {
            // 抢单/空池：仍落时限（池内任务同样有倒计时）
            OpTaskTimeLimit limit = timeLimitService.applyTimeLimit(task, priority);
            out.put("dueTime", limit.getDueTime());
            out.put("limitHours", limit.getLimitHours());
        }
        writeLog(task, resolution, policy, decision, priority, rule.getTargetGroupId(), applied ? 1 : 0);
        out.put("applied", applied);
        return out;
    }

    /** 目标分组的在岗审核员用户 ID（reviewer 表；无分组或无人返回空表，由上层按"空池"处理） */
    private List<Long> candidateUserIds(Long groupId) {
        if (groupId == null) {
            return List.of();
        }
        List<Reviewer> reviewers = reviewerMapper.selectList(new LambdaQueryWrapper<Reviewer>()
                .eq(Reviewer::getReviewerGroupId, groupId)
                .eq(Reviewer::getStatus, 1));
        Set<Long> ids = new LinkedHashSet<>();
        for (Reviewer r : reviewers) {
            if (r.getUserId() != null) {
                ids.add(r.getUserId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 改派受理人：Flowable 引擎受理人 ＋ 待办镜像（两处必须同批改，只改一处就会出现
     * "待办显示是他、引擎里点不动"的经典双真值症状）。
     */
    private void applyAssignee(WfTask task, Long targetUserId) {
        taskService.setAssignee(task.getTaskId(), String.valueOf(targetUserId));
        wfQueryService.updateTaskAssignee(task.getTaskId(), targetUserId);
        log.info("G3 自动分配：taskId={} 指派给 userId={}（节点 {}/{}）",
                task.getTaskId(), targetUserId, task.getModelKey(), task.getNodeKey());
    }

    /** 落一条自动分配日志（写失败必须可见，不得静默） */
    private void writeLog(WfTask task, RuleResolution resolution, OpDispatchPolicy policy,
                          AssignmentDecision decision, String priority, Long groupId, int success) {
        OpDispatchLog row = new OpDispatchLog();
        row.setTaskId(task.getTaskId());
        row.setProcInstId(task.getProcInstId());
        row.setBizType(task.getBizType());
        row.setBizId(task.getBizId());
        row.setBizNo(task.getBizNo());
        row.setModelKey(task.getModelKey());
        row.setNodeKey(task.getNodeKey());
        row.setNodeName(task.getNodeName());
        row.setDispatchMode("PICK".equals(decision.strategy()) ? "PICK" : "STRATEGY");
        OpDispatchRule rule = resolution.matched();
        if (rule != null) {
            row.setMatchedRuleId(rule.getId());
            row.setMatchedRuleCode(rule.getRuleCode());
            row.setMatchedRuleName(rule.getRuleName());
        }
        row.setPolicyId(policy.getId());
        row.setStrategy(decision.strategy());
        row.setTargetGroupId(groupId);
        row.setCandidateSnapshot(decision.candidateSnapshot());
        row.setChosenUserId(decision.chosenUserId());
        row.setChosenUserName(decision.chosenUserName());
        row.setDecisionReason("[" + priority + "] " + decision.reason());
        row.setSuccess(success);
        try {
            logMapper.insert(row);
        } catch (Exception ex) {
            log.error("G3 自动分配日志写入失败（分配结果已生效，请人工核对）taskId={}, chosen={}, reason={}",
                    task.getTaskId(), decision.chosenUserId(), decision.reason(), ex);
        }
    }

    // 3. 日志查询

    public PageResult<OpDispatchLog> logPage(PageQuery q,
                                                                 String taskId, String bizNo, String strategy) {
        LambdaQueryWrapper<OpDispatchLog> w = new LambdaQueryWrapper<>();
        if (notBlank(taskId)) {
            w.eq(OpDispatchLog::getTaskId, taskId.trim());
        }
        if (notBlank(bizNo)) {
            w.like(OpDispatchLog::getBizNo, bizNo.trim());
        }
        if (notBlank(strategy)) {
            w.eq(OpDispatchLog::getStrategy, strategy.trim().toUpperCase());
        }
        w.orderByDesc(OpDispatchLog::getId);
        Page<OpDispatchLog> p =
                logMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    /** "这单为什么派给了他"：按任务 ID 取全部决策记录（时间正序，读成一条决策链） */
    public List<OpDispatchLog> decisionChain(String taskId) {
        if (!notBlank(taskId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 必填");
        }
        return logMapper.selectList(new LambdaQueryWrapper<OpDispatchLog>()
                .eq(OpDispatchLog::getTaskId, taskId.trim())
                .orderByAsc(OpDispatchLog::getId));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
