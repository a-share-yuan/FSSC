// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ReviewLog;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.mapper.ReviewLogMapper;
import com.fssc.compliance.mapper.ReviewRuleMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能审单引擎：加载审单规则 → 逐条匹配 → 自动通过/驳回/人工审核。
 * 规则引擎与 PrecheckEngineService 共享脚本语法，但输出不同（审单动作 vs 合规校验结果）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartReviewService {

    private final ReviewRuleMapper reviewRuleMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final PrecheckEngineService precheckEngineService;
    private final EmployeeCreditService employeeCreditService;
    private final AiReviewService aiReviewService;

    /** D-DEC-175：create_by 列是 varchar 且不在 MP 自动填充六列之内，取当前登录人 id 显式落串；无上下文（作业/内部调用）留空不冒充。 */
    private static String currentOperatorId() {
        LoginUser u = UserContext.get();
        return u == null || u.getUserId() == null ? null : String.valueOf(u.getUserId());
    }

    /**
     * D-DEC-018（X-31，乙）：AI 未采纳的业务留痕。
     * <p>AI 通道现与端点不同型、调用恒失败，改前只剩一行 WARN 后静默走规则引擎 ⇒ 单据的审单记录里
     * 看不出"这一单是谁判的"。现把未采纳本身落成一条留痕：{@code action=SKIP} 与既有"未命中"行同族，
     * 不污染 AUTO_PASS/AUTO_REJECT/MANUAL_REVIEW 三个统计口径，也不给 AUTO_PASS 链路喂 AI 结论。
     */
    private static final String AI_FALLBACK_RULE_CODE = "AI_REVIEW_FALLBACK";
    private static final String AI_FALLBACK_RULE_NAME = "AI 审单未采纳";
    private static final String AI_FALLBACK_REMARK = "AI 未采纳·回退规则引擎（检查点）";

    // ==================== 规则 CRUD ====================

    public PageResult<ReviewRule> pageRules(long pageNum, long pageSize, String ruleCategory) {
        LambdaQueryWrapper<ReviewRule> qw = new LambdaQueryWrapper<>();
        if (ruleCategory != null && !ruleCategory.isBlank()) {
            qw.eq(ReviewRule::getRuleCategory, ruleCategory);
        }
        qw.orderByAsc(ReviewRule::getPriority).orderByAsc(ReviewRule::getId);
        Page<ReviewRule> p = reviewRuleMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public ReviewRule getRule(Long id) {
        ReviewRule rule = reviewRuleMapper.selectById(id);
        if (rule == null) throw new BizException(ErrorCode.PARAM_INVALID, "审单规则不存在: " + id);
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createRule(ReviewRule rule) {
        assertRuleCodeFree(rule.getRuleCode(), null);
        rule.setStatus("NORMAL");
        rule.setCreateTime(LocalDateTime.now());
        reviewRuleMapper.insert(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRule(ReviewRule rule) {
        ReviewRule existing = reviewRuleMapper.selectById(rule.getId());
        if (existing == null) throw new BizException(ErrorCode.PARAM_INVALID, "审单规则不存在: " + rule.getId());
        assertRuleCodeFree(rule.getRuleCode(), rule.getId());
        rule.setUpdateTime(LocalDateTime.now());
        reviewRuleMapper.updateById(rule);
    }

    /** 规则编码在本租户内的存活集唯一（与 PrecheckEngineService 同口径；本表走物理 DELETE，删除即让码位可用） */
    private void assertRuleCodeFree(String code, Long selfId) {
        if (code == null || code.isBlank()) {
            return;
        }
        Long n = reviewRuleMapper.selectCount(new LambdaQueryWrapper<ReviewRule>()
                .eq(ReviewRule::getRuleCode, code)
                .ne(selfId != null, ReviewRule::getId, selfId));
        if (n != null && n > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS, "规则编码已存在: " + code);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        if (reviewRuleMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审单规则不存在: " + id);
        }
        reviewRuleMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableRule(Long id) {
        ReviewRule rule = getRule(id);
        rule.setStatus("NORMAL");
        rule.setUpdateTime(LocalDateTime.now());
        reviewRuleMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableRule(Long id) {
        ReviewRule rule = getRule(id);
        rule.setStatus("DISABLED");
        rule.setUpdateTime(LocalDateTime.now());
        reviewRuleMapper.updateById(rule);
    }

    // ==================== 批量操作 ====================

    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(List<Long> ids) {
        ids.forEach(this::enableRule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> ids) {
        ids.forEach(this::disableRule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        ids.forEach(this::deleteRule);
    }

    // ==================== 核心审单引擎 ====================

    /**
     * 对单据执行智能审单。
     * <p>
     * 加载所有 NORMAL 状态规则，按优先级排序逐条匹配。
     * 匹配则执行对应动作，记录日志并返回最终结果。
     * 若所有规则均未匹配，则默认 MANUAL_REVIEW。
     *
     * @param billId   单据ID
     * @param billNo   单据编号
     * @param billData 单据数据（用于表达式求值）
     * @return 审单结果
     */
    public ReviewResult executeReview(Long billId, String billNo, Map<String, Object> billData) {
        // D-DEC-018：本单是否走了"AI 未采纳→回退规则引擎（检查点）"——留痕要在结论里说一次
        boolean aiNotAdopted = false;
        // P2-5: AI 审单前置（启用且配置有效时优先调用 AI；失败/未启用回退规则引擎）
        if (aiReviewService.isEnabled()) {
            Map<String, Object> riskContext = new LinkedHashMap<>();
            riskContext.put("billId", billId);
            riskContext.put("submitterId", billData.get("submitterId"));
            riskContext.put("amount", billData.get("amount"));

            AiReviewService.AiReviewResult aiResult = aiReviewService.review(billNo, billData, riskContext);
            if (aiResult != null) {
                log.info("AI 审单结果: billNo={}, action={}, risk={}, confidence={}",
                        billNo, aiResult.getAction(), aiResult.getRiskLevel(), aiResult.getConfidence());

                // 记录 AI 审单日志
                ReviewLog aiLog = new ReviewLog();
                aiLog.setBillId(billId);
                aiLog.setBillNo(billNo);
                aiLog.setRuleId(null);
                aiLog.setRuleCode("AI_REVIEW");
                aiLog.setRuleName("AI 智能审单");
                aiLog.setMatched(true);
                aiLog.setAction(aiResult.getAction());
                aiLog.setRiskLevel(aiResult.getRiskLevel() != null ? aiResult.getRiskLevel() : "LOW");
                aiLog.setRiskScore(aiResult.getRiskScore());
                aiLog.setRemark(aiResult.getRemark() != null ? aiResult.getRemark() : "AI 审单");
                // D-DEC-175：review_time 两支 insert 腿从未置值、create_by 非 TenantBase 字段 ⇒ 双双恒空，写点显式回写
                aiLog.setReviewTime(LocalDateTime.now());
                aiLog.setCreateBy(currentOperatorId());
                aiLog.setCreateTime(LocalDateTime.now());
                reviewLogMapper.insert(aiLog);

                // AUTO_PASS / AUTO_REJECT 直接采纳 AI 结果
                if ("AUTO_PASS".equals(aiResult.getAction()) || "AUTO_REJECT".equals(aiResult.getAction())) {
                    List<ReviewRuleResult> aiRuleResults = new ArrayList<>();
                    aiRuleResults.add(new ReviewRuleResult(null, "AI_REVIEW", "AI 智能审单",
                            true, aiResult.getAction(), aiResult.getRiskLevel(), aiResult.getRemark()));
                    // AUTO_PASS 时检查信用免审
                    if ("AUTO_PASS".equals(aiResult.getAction())) {
                        Long submitterId = billData.get("submitterId") != null
                                ? Long.valueOf(billData.get("submitterId").toString()) : null;
                        BigDecimal amount = billData.get("amount") != null
                                ? new BigDecimal(billData.get("amount").toString()) : null;
                        if (submitterId != null && amount != null
                                && employeeCreditService.canAutoApprove(submitterId, amount)) {
                            employeeCreditService.onApproved(submitterId);
                        }
                    } else if ("AUTO_REJECT".equals(aiResult.getAction())) {
                        Long submitterId = billData.get("submitterId") != null
                                ? Long.valueOf(billData.get("submitterId").toString()) : null;
                        if (submitterId != null) {
                            employeeCreditService.onRejected(submitterId, aiResult.getRemark());
                        }
                    }
                    return new ReviewResult(aiResult.getAction(),
                            aiResult.getRiskLevel() != null ? aiResult.getRiskLevel() : "LOW",
                            aiResult.getRiskScore(),
                            aiResult.getRemark() != null ? aiResult.getRemark() : "AI 审单通过",
                            aiRuleResults);
                }
                // MANUAL_REVIEW → 继续走规则引擎
                log.info("AI 审单建议人工审核，继续规则引擎: billNo={}", billNo);
            } else {
                // D-DEC-018（乙）：AI 未采纳不再只是日志里一行 warn——本单落一条业务留痕，结论由规则引擎（检查点）给
                aiNotAdopted = true;
                recordAiFallback(billId, billNo);
            }
        }

        // 规则引擎审单
        // P1 修复（2026-09-09 UI 实测发现）：加载规则时必须按单据类型过滤 related_expense_types，
        // 否则「金额超5万自动驳回」等费用风控规则会误杀借款/还款/付款等资金类单据
        // （600 万借款经 12 节点审批后仍被 amount>50000 规则 AUTO_REJECT，导致最终状态 REJECTED）。
        // 规则 related_expense_types 为 NULL 表示全类型适用（向后兼容保持原行为）。
        String billTypeOfBill = billData.get("billType") != null ? String.valueOf(billData.get("billType")) : null;
        List<ReviewRule> rules = reviewRuleMapper.selectList(
                new LambdaQueryWrapper<ReviewRule>()
                        .eq(ReviewRule::getStatus, "NORMAL")
                        .orderByAsc(ReviewRule::getPriority)
                        .orderByAsc(ReviewRule::getId));
        if (billTypeOfBill != null && !billTypeOfBill.isBlank()) {
            rules = rules.stream()
                    .filter(r -> r.getRelatedExpenseTypes() == null || r.getRelatedExpenseTypes().isBlank()
                            || containsExpenseType(r.getRelatedExpenseTypes(), billTypeOfBill))
                    .toList();
        }

        if (rules.isEmpty()) {
            return new ReviewResult("MANUAL_REVIEW", "LOW", 0,
                    "无审单规则，默认人工审核" + (aiNotAdopted ? "；" + AI_FALLBACK_REMARK : ""),
                    aiNotAdopted ? new ArrayList<>(List.of(aiFallbackRuleResult())) : Collections.emptyList());
        }

        List<ReviewRuleResult> ruleResults = new ArrayList<>();
        String finalAction = "MANUAL_REVIEW";
        String finalRisk = "LOW";
        int finalScore = 0;
        String finalRemark = "所有规则均未匹配，默认人工审核";

        for (ReviewRule rule : rules) {
            boolean matched = evaluateRule(rule, billData);
            ReviewRuleResult rr = new ReviewRuleResult(rule.getId(), rule.getRuleCode(), rule.getRuleName(),
                    matched, rule.getAction(), rule.getRiskLevel(), matched ? "匹配" : "未匹配");
            ruleResults.add(rr);

            // 记录日志
            ReviewLog logEntry = new ReviewLog();
            logEntry.setBillId(billId);
            logEntry.setBillNo(billNo);
            logEntry.setRuleId(rule.getId());
            logEntry.setRuleCode(rule.getRuleCode());
            logEntry.setRuleName(rule.getRuleName());
            logEntry.setMatched(matched);
            logEntry.setAction(matched ? rule.getAction() : "SKIP");
            logEntry.setRiskLevel(matched ? rule.getRiskLevel() : "LOW");
            logEntry.setRiskScore(matched ? riskScore(rule.getRiskLevel()) : 0);
            logEntry.setRemark(matched ? "命中规则: " + rule.getRuleName() : "未命中");
            // D-DEC-175：同 AI 腿——review_time/create_by 恒空根因，显式回写
            logEntry.setReviewTime(LocalDateTime.now());
            logEntry.setCreateBy(currentOperatorId());
            logEntry.setCreateTime(LocalDateTime.now());
            reviewLogMapper.insert(logEntry);

            if (matched) {
                log.info("审单规则命中: billNo={}, rule={}, action={}, risk={}",
                        billNo, rule.getRuleCode(), rule.getAction(), rule.getRiskLevel());

                // 高优先级规则优先
                finalAction = rule.getAction();
                finalRisk = rule.getRiskLevel();
                finalScore = riskScore(rule.getRiskLevel());
                finalRemark = "命中规则: " + rule.getRuleName();

                // AUTO_REJECT 立即终止（无需继续匹配）
                if ("AUTO_REJECT".equals(rule.getAction())) {
                    break;
                }
                // AUTO_PASS 也终止（已自动通过）
                if ("AUTO_PASS".equals(rule.getAction())) {
                    break;
                }
                // MANUAL_REVIEW 继续匹配看是否有更高风险的规则
            }
        }

        // P1-5: 信用分驱动免审——规则未命中 AUTO_PASS/AUTO_REJECT 时，检查信用免审额度
        if ("MANUAL_REVIEW".equals(finalAction)) {
            Long submitterId = billData.get("submitterId") != null
                    ? Long.valueOf(billData.get("submitterId").toString()) : null;
            BigDecimal amount = billData.get("amount") != null
                    ? new BigDecimal(billData.get("amount").toString()) : null;
            if (submitterId != null && amount != null
                    && employeeCreditService.canAutoApprove(submitterId, amount)) {
                finalAction = "AUTO_PASS";
                finalRemark = "信用免审通过（信用额度内自动通过）";
                ruleResults.add(new ReviewRuleResult(null, "CREDIT_AUTO_APPROVE",
                        "信用免审", true, "AUTO_PASS", "LOW", "信用分达标，金额在免审额度内"));
                employeeCreditService.onApproved(submitterId);
                log.info("信用免审通过: billNo={}, submitterId={}, amount={}", billNo, submitterId, amount);
            }
        } else if ("AUTO_REJECT".equals(finalAction)) {
            Long submitterId = billData.get("submitterId") != null
                    ? Long.valueOf(billData.get("submitterId").toString()) : null;
            if (submitterId != null) {
                employeeCreditService.onRejected(submitterId, finalRemark);
            }
        }

        // D-DEC-018（乙）：AI 未采纳这事实既进审单记录（recordAiFallback 那一行），也进本次结论，
        // 经办人不必去翻日志就知道这一单的判定来自规则引擎（检查点）。
        if (aiNotAdopted) {
            ruleResults.add(0, aiFallbackRuleResult());
            finalRemark = finalRemark + "；" + AI_FALLBACK_REMARK;
        }

        return new ReviewResult(finalAction, finalRisk, finalScore, finalRemark, ruleResults);
    }

    /** D-DEC-018：AI 未采纳的留痕行（op_review_log 既有列，零新列；action=SKIP 与"未命中"行同族） */
    private void recordAiFallback(Long billId, String billNo) {
        try {
            ReviewLog fb = new ReviewLog();
            fb.setBillId(billId);
            fb.setBillNo(billNo);
            fb.setRuleId(null);
            fb.setRuleCode(AI_FALLBACK_RULE_CODE);
            fb.setRuleName(AI_FALLBACK_RULE_NAME);
            fb.setMatched(false);
            fb.setAction("SKIP");
            fb.setRiskLevel("LOW");
            fb.setRiskScore(0);
            fb.setRemark(AI_FALLBACK_REMARK);
            fb.setReviewTime(LocalDateTime.now());
            fb.setCreateBy(currentOperatorId());
            fb.setCreateTime(LocalDateTime.now());
            reviewLogMapper.insert(fb);
        } catch (Exception ex) {
            // 留痕失败不得改变本单判定（AI 未采纳不是异常，规则引擎照常跑）
            log.warn("AI 未采纳留痕写入失败 billNo={}: {}", billNo, ex.getMessage());
        }
    }

    /** D-DEC-018：留痕行的返回视图（与 ReviewRuleResult 同构，前端审单明细直接可见） */
    private ReviewRuleResult aiFallbackRuleResult() {
        return new ReviewRuleResult(null, AI_FALLBACK_RULE_CODE, AI_FALLBACK_RULE_NAME,
                false, "SKIP", "LOW", AI_FALLBACK_REMARK);
    }

    /** 判断单据类型是否在规则适用类型列表中（英文逗号分隔，包含匹配） */
    private boolean containsExpenseType(String related, String billType) {
        if (related == null || related.isBlank() || billType == null || billType.isBlank()) {
            return true;
        }
        for (String t : related.split(",")) {
            String tt = t.trim();
            if (!tt.isEmpty() && (tt.equals(billType) || billType.startsWith(tt))) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateRule(ReviewRule rule, Map<String, Object> billData) {
        String expr = rule.getMatchExpression();
        if (expr == null || expr.isBlank()) {
            return false;
        }
        try {
            return precheckEngineService.evaluateScript(expr, billData);
        } catch (Exception e) {
            log.warn("审单规则表达式求值失败: ruleCode={}, expr={}", rule.getRuleCode(), expr, e);
            return false;
        }
    }

    private int riskScore(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 80;
            case "MEDIUM" -> 50;
            default -> 20;
        };
    }

    // ==================== 审单日志 ====================

    public PageResult<ReviewLog> pageLogs(long pageNum, long pageSize, Long billId, String action) {
        LambdaQueryWrapper<ReviewLog> qw = new LambdaQueryWrapper<>();
        if (billId != null) qw.eq(ReviewLog::getBillId, billId);
        if (action != null && !action.isBlank()) qw.eq(ReviewLog::getAction, action);
        qw.orderByDesc(ReviewLog::getId);
        Page<ReviewLog> p = reviewLogMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public List<ReviewLog> listLogsByBill(Long billId) {
        return reviewLogMapper.selectList(
                new LambdaQueryWrapper<ReviewLog>()
                        .eq(ReviewLog::getBillId, billId)
                        .orderByAsc(ReviewLog::getId));
    }

    // ==================== 审单统计 ====================

    /** 审单统计：按动作分组 */
    public Map<String, Object> reviewStats() {
        long total = reviewLogMapper.selectCount(new LambdaQueryWrapper<>());
        long autoPass = reviewLogMapper.selectCount(
                new LambdaQueryWrapper<ReviewLog>().eq(ReviewLog::getAction, "AUTO_PASS"));
        long autoReject = reviewLogMapper.selectCount(
                new LambdaQueryWrapper<ReviewLog>().eq(ReviewLog::getAction, "AUTO_REJECT"));
        long manualReview = reviewLogMapper.selectCount(
                new LambdaQueryWrapper<ReviewLog>().eq(ReviewLog::getAction, "MANUAL_REVIEW"));
        long highRisk = reviewLogMapper.selectCount(
                new LambdaQueryWrapper<ReviewLog>().eq(ReviewLog::getRiskLevel, "HIGH"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("autoPass", autoPass);
        result.put("autoReject", autoReject);
        result.put("manualReview", manualReview);
        result.put("autoRate", total > 0
                ? BigDecimal.valueOf((autoPass + autoReject) * 100.0 / total).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("highRiskCount", highRisk);
        return result;
    }

    // ==================== 内部类 ====================

    /** 审单结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ReviewResult {
        private String action;        // AUTO_PASS / AUTO_REJECT / MANUAL_REVIEW
        private String riskLevel;     // LOW / MEDIUM / HIGH
        private int riskScore;        // 0-100
        private String remark;
        private List<ReviewRuleResult> ruleResults;
    }

    /** 单条规则匹配结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ReviewRuleResult {
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private boolean matched;
        private String action;
        private String riskLevel;
        private String remark;
    }
}