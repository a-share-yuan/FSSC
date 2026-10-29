// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.DispatchTask;
import com.fssc.compliance.entity.EmployeeCredit;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.compliance.mapper.DispatchTaskMapper;
import com.fssc.compliance.mapper.ReviewerMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.sharing.entity.OpDispatchRule;
import com.fssc.sharing.entity.ShGroup;
import com.fssc.sharing.mapper.OpDispatchRuleMapper;
import com.fssc.sharing.mapper.ShGroupMapper;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final OpDispatchRuleMapper opDispatchRuleMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final ReviewerMapper reviewerMapper;
    private final ReviewerService reviewerService;
    private final ShGroupMapper shGroupMapper;
    private final SlaMonitorService slaMonitorService;
    private final EmployeeCreditService employeeCreditService;
    private final NumberRuleService numberRuleService;

    @Transactional(rollbackFor = Exception.class)
    public DispatchTask dispatch(String billId, String billType, String expenseType,
                                 BigDecimal amount, Long legalEntityId) {
        // 必填守卫（前端 ARCHIVE_DISPATCH 登记表同一规则）：dispatch_task 的 expense_type / amount
        // 为 NOT NULL，且这四个字段同时是派单规则 findMatchingRule 的匹配键——缺任一项只会得到
        // 「未找到匹配的派单规则」这种误导性的结论，掩盖真正原因（用户没填）。
        if (billType == null || billType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择单据类型");
        }
        if (expenseType == null || expenseType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入费项");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入金额（不能为负）");
        }
        if (legalEntityId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择法人主体");
        }
        // 缺陷修复（H-04 / DA-06）：非数字单号原 Long.valueOf 直接抛 NumberFormatException → 500。
        // 统一转友好业务异常（400 + 明确提示）。
        Long billIdNum = parseBillId(billId);
        OpDispatchRule rule = findMatchingRule(billType, expenseType, amount, legalEntityId);
        if (rule == null) {
            log.warn("未找到匹配的派单规则，billId={}, billType={}", billId, billType);
            throw new BizException(ErrorCode.PARAM_INVALID, "未找到匹配的派单规则");
        }

        // 派单去重——同一 (bill_id, matched_rule_id) 已有任务即复用，不再重复落 dispatch_task
        //（改前每次调用都新插一行，重复点「发起派单/自动派单」得到多条任务，审核人收到重复工单；
        // 库级唯一键 uk_dispatch_task_bill_rule 属 DDL 变更，转人工在人工清单登记）
        DispatchTask existing = dispatchTaskMapper.selectOne(new LambdaQueryWrapper<DispatchTask>()
                .eq(DispatchTask::getBillId, billIdNum)
                .eq(DispatchTask::getMatchedRuleId, rule.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("派单已存在（幂等复用）: billId={}, ruleId={}, taskCode={}", billIdNum, rule.getId(), existing.getTaskCode());
            return existing;
        }

        // 检查目标分组是否开启了自动派单
        boolean autoDispatch = isGroupAutoDispatchEnabled(rule.getTargetGroupId());

        if (autoDispatch) {
            // 自动派单：匹配规则后直接分配审核员
            List<Reviewer> candidates = listCandidates(rule.getTargetGroupId());
            if (candidates.isEmpty()) {
                log.warn("目标审核组无可用成员，groupId={}", rule.getTargetGroupId());
                throw new BizException(ErrorCode.PARAM_INVALID, "目标审核组无可用成员");
            }

            Reviewer selected = selectReviewer(candidates, rule.getDispatchStrategy());
            if (selected == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "无法选择审核员");
            }

            DispatchTask task = new DispatchTask();
            task.setTaskCode(generateTaskCode());
            task.setBillId(billIdNum);
            task.setBillType(billType);
            task.setExpenseType(expenseType);
            task.setAmount(amount);
            task.setLegalEntityId(legalEntityId);
            task.setMatchedRuleId(rule.getId());
            task.setAssignedGroupId(rule.getTargetGroupId());
            task.setAssignedReviewerId(selected.getId());
            task.setDispatchStrategy(rule.getDispatchStrategy());
            task.setStatus("ASSIGNED");
            task.setAssignedAt(LocalDateTime.now());
            dispatchTaskMapper.insert(task);

            // 派单后设置 SLA 到期时间
            slaMonitorService.setSlaDueTime(task);

            // 记录提交人信用
            Long submitterId = userId();
            if (submitterId != null) {
                employeeCreditService.onSubmitted(submitterId);
            }

            reviewerService.incrementPending(selected.getId());

            // 不再发起 FM_DOC_FLOW 单据流转审批（流程清单瘦身 §六 X 类）：该流 0 实例、无最终状态收尾钩子，
            // 派单本身的记录由 dispatch_task + op_dispatch_log 承担。

            log.info("自动派单完成: taskCode={}, reviewer={}, strategy={}",
                    task.getTaskCode(), selected.getReviewerName(), rule.getDispatchStrategy());
            return task;
        } else {
            // 人工抢单模式：创建 PENDING 任务，不分配审核员，等待人工认领
            DispatchTask task = new DispatchTask();
            task.setTaskCode(generateTaskCode());
            task.setBillId(billIdNum);
            task.setBillType(billType);
            task.setExpenseType(expenseType);
            task.setAmount(amount);
            task.setLegalEntityId(legalEntityId);
            task.setMatchedRuleId(rule.getId());
            task.setAssignedGroupId(rule.getTargetGroupId());
            task.setDispatchStrategy(rule.getDispatchStrategy());
            task.setStatus("PENDING");
            dispatchTaskMapper.insert(task);

            log.info("人工抢单模式: 已创建待认领任务 taskCode={}, groupId={}",
                    task.getTaskCode(), rule.getTargetGroupId());
            return task;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignManual(Long taskId, Long reviewerId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派单任务不存在: " + taskId);
        }
        if (!"PENDING".equals(task.getStatus()) && !"ASSIGNED".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务状态不允许指派");
        }

        Reviewer reviewer = reviewerMapper.selectById(reviewerId);
        if (reviewer == null || reviewer.getStatus() == null || reviewer.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不可用: " + reviewerId);
        }

        if ("ASSIGNED".equals(task.getStatus()) && task.getAssignedReviewerId() != null) {
            reviewerService.decrementPending(task.getAssignedReviewerId());
        }

        task.setAssignedReviewerId(reviewerId);
        task.setStatus("ASSIGNED");
        task.setAssignedAt(LocalDateTime.now());
        dispatchTaskMapper.updateById(task);

        reviewerService.incrementPending(reviewerId);
        log.info("手动指派: taskId={}, reviewer={}", taskId, reviewer.getReviewerName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派单任务不存在: " + taskId);
        }
        task.setStatus("COMPLETED");
        dispatchTaskMapper.updateById(task);

        if (task.getAssignedReviewerId() != null) {
            reviewerService.incrementCompleted(task.getAssignedReviewerId());
            reviewerService.decrementPending(task.getAssignedReviewerId());
        }
        log.info("完成任务: taskId={}", taskId);
    }

    public List<DispatchTask> listByStatus(String status) {
        LambdaQueryWrapper<DispatchTask> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(DispatchTask::getStatus, status);
        }
        qw.orderByDesc(DispatchTask::getAssignedAt);
        return dispatchTaskMapper.selectList(qw);
    }

    /** 派单任务分页（原 listByStatus 保留兼容） */
    public PageResult<DispatchTask> pageByStatus(String status, long pageNum, long pageSize) {
        LambdaQueryWrapper<DispatchTask> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(DispatchTask::getStatus, status);
        }
        qw.orderByDesc(DispatchTask::getAssignedAt);
        Page<DispatchTask> p = dispatchTaskMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public List<DispatchTask> listMyTasks(Long reviewerId, String status) {
        LambdaQueryWrapper<DispatchTask> qw = new LambdaQueryWrapper<>();
        qw.eq(DispatchTask::getAssignedReviewerId, reviewerId);
        if (StringUtils.hasText(status)) {
            qw.eq(DispatchTask::getStatus, status);
        }
        qw.orderByDesc(DispatchTask::getAssignedAt);
        return dispatchTaskMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public void escalateTask(Long taskId, String reason) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派单任务不存在: " + taskId);
        }
        task.setStatus("ESCALATED");
        task.setUpgradedAt(LocalDateTime.now());
        task.setUpgradeReason(reason);
        dispatchTaskMapper.updateById(task);

        log.warn("任务升级: taskId={}, reason={}", taskId, reason);
    }

    public DispatchTask getById(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派单任务不存在: " + taskId);
        }
        return task;
    }

    /** 非数字单号友好校验（H-04/DA-06）：原 Long.valueOf 抛 NumberFormatException 变 500，现转友好业务异常 */
    private static Long parseBillId(String billId) {
        if (billId == null || billId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派单失败：单据ID不能为空");
        }
        try {
            return Long.valueOf(billId.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "派单失败：单据编号须为数字ID，收到非数字编号「" + billId + "」");
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void autoDispatchCheck() {
        LambdaQueryWrapper<DispatchTask> qw = new LambdaQueryWrapper<>();
        qw.eq(DispatchTask::getStatus, "PENDING");
        List<DispatchTask> pendingTasks = dispatchTaskMapper.selectList(qw);

        log.info("自动派单检查: 发现{}个待派单任务", pendingTasks.size());

        for (DispatchTask task : pendingTasks) {
            try {
                OpDispatchRule rule = findMatchingRule(
                        task.getBillType(), task.getExpenseType(),
                        task.getAmount(), task.getLegalEntityId());
                if (rule != null) {
                    // 检查目标分组是否开启了自动派单
                    if (!isGroupAutoDispatchEnabled(rule.getTargetGroupId())) {
                        log.info("分组未开启自动派单，跳过: taskCode={}, groupId={}",
                                task.getTaskCode(), rule.getTargetGroupId());
                        continue;
                    }
                    List<Reviewer> candidates = listCandidates(rule.getTargetGroupId());
                    if (!candidates.isEmpty()) {
                        Reviewer selected = selectReviewer(candidates, rule.getDispatchStrategy());
                        if (selected != null) {
                            task.setMatchedRuleId(rule.getId());
                            task.setAssignedGroupId(rule.getTargetGroupId());
                            task.setAssignedReviewerId(selected.getId());
                            task.setDispatchStrategy(rule.getDispatchStrategy());
                            task.setStatus("ASSIGNED");
                            task.setAssignedAt(LocalDateTime.now());
                            dispatchTaskMapper.updateById(task);
                            reviewerService.incrementPending(selected.getId());
                            log.info("自动派单成功: taskCode={}, reviewer={}",
                                    task.getTaskCode(), selected.getReviewerName());
                            continue;
                        }
                    }
                }
                log.warn("自动派单失败: taskCode={}, 无匹配规则或无可用审核员", task.getTaskCode());
            } catch (Exception e) {
                log.error("自动派单异常: taskCode={}", task.getTaskCode(), e);
            }
        }
    }

    private OpDispatchRule findMatchingRule(String billType, String expenseType,
                                            BigDecimal amount, Long legalEntityId) {
        LambdaQueryWrapper<OpDispatchRule> qw = new LambdaQueryWrapper<>();
        qw.eq(OpDispatchRule::getStatus, "NORMAL")
                .orderByDesc(OpDispatchRule::getPriority);
        List<OpDispatchRule> rules = opDispatchRuleMapper.selectList(qw);

        for (OpDispatchRule rule : rules) {
            if (matchesRule(rule, billType, expenseType, amount, legalEntityId)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matchesRule(OpDispatchRule rule, String billType, String expenseType,
                                BigDecimal amount, Long legalEntityId) {
        String dimension = rule.getDispatchDimension();
        String condition = rule.getMatchCondition();

        if (!StringUtils.hasText(dimension) || !StringUtils.hasText(condition)) {
            return true;
        }

        return switch (dimension) {
            case "TYPE" -> condition.equals(billType) || condition.equals(expenseType);
            case "AMOUNT" -> matchAmountCondition(condition, amount);
            case "LEGAL" -> {
                if (legalEntityId == null) yield false;
                yield condition.equals(String.valueOf(legalEntityId));
            }
            case "COMPLEXITY", "REGION" -> true;
            default -> true;
        };
    }

    private boolean matchAmountCondition(String condition, BigDecimal amount) {
        if (amount == null || !StringUtils.hasText(condition)) {
            return false;
        }
        try {
            String[] parts = condition.split(",");
            if (parts.length == 1) {
                BigDecimal threshold = new BigDecimal(parts[0].trim());
                return amount.compareTo(threshold) >= 0;
            }
            if (parts.length == 2) {
                BigDecimal min = new BigDecimal(parts[0].trim());
                BigDecimal max = new BigDecimal(parts[1].trim());
                return amount.compareTo(min) >= 0 && amount.compareTo(max) <= 0;
            }
        } catch (NumberFormatException e) {
            log.warn("金额条件解析失败: {}", condition);
        }
        return false;
    }

    private List<Reviewer> listCandidates(Long groupId) {
        LambdaQueryWrapper<Reviewer> qw = new LambdaQueryWrapper<>();
        qw.eq(Reviewer::getReviewerGroupId, groupId)
                .eq(Reviewer::getStatus, 1);
        return reviewerMapper.selectList(qw);
    }

    /**
     * 检查目标分组是否开启了自动派单。
     * ShGroup.isAutoDispatch: 1=自动派单, 0=人工抢单, null=默认不自动派单
     */
    private boolean isGroupAutoDispatchEnabled(Long groupId) {
        if (groupId == null) {
            return false;
        }
        ShGroup group = shGroupMapper.selectById(groupId);
        if (group == null) {
            return false;
        }
        Integer flag = group.getIsAutoDispatch();
        return flag != null && flag == 1;
    }

    private Reviewer selectReviewer(List<Reviewer> candidates, String strategy) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return switch (strategy) {
            case "ROUND_ROBIN" -> candidates.stream()
                    .min(Comparator.comparingInt(r -> r.getCurrentPending() != null ? r.getCurrentPending() : 0))
                    .orElse(candidates.get(0));

            case "LOAD_BALANCE" -> candidates.stream()
                    .min(Comparator.comparingDouble(this::calcLoadRatio))
                    .orElse(candidates.get(0));

            case "SKILL_MATCH" -> selectBySkill(candidates);

            // 信用分驱动派单——优先派给高信用审核员
            case "CREDIT_BASED" -> selectByCredit(candidates);

            default -> candidates.stream()
                    .min(Comparator.comparingInt(r -> r.getCurrentPending() != null ? r.getCurrentPending() : 0))
                    .orElse(candidates.get(0));
        };
    }

    private double calcLoadRatio(Reviewer reviewer) {
        int pending = reviewer.getCurrentPending() != null ? reviewer.getCurrentPending() : 0;
        int max = reviewer.getMaxDailyTasks() != null ? reviewer.getMaxDailyTasks() : 100;
        return (double) pending / max;
    }

    private Reviewer selectBySkill(List<Reviewer> candidates) {
        return candidates.stream()
                .min(Comparator.comparingInt(r -> r.getCurrentPending() != null ? r.getCurrentPending() : 0))
                .orElse(candidates.get(0));
    }

    /**
     * 信用分驱动派单——优先派给信用分最高的审核员。
     */
    private Reviewer selectByCredit(List<Reviewer> candidates) {
        return candidates.stream()
                .max(Comparator.comparingInt(r -> {
                    EmployeeCredit credit = employeeCreditService.findByUserId(r.getUserId());
                    return credit != null && credit.getCreditScore() != null ? credit.getCreditScore() : 500;
                }))
                .orElse(candidates.get(0));
    }

    private String generateTaskCode() {
        try { return numberRuleService.next("DISPATCH_TASK"); }
        catch (Exception e) { return "DT" + System.currentTimeMillis(); }
    }
}