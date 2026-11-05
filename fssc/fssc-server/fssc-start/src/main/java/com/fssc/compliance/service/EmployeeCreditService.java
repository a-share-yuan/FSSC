// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.EmployeeCredit;
import com.fssc.compliance.entity.MdViolationReason;
import com.fssc.compliance.mapper.EmployeeCreditMapper;
import com.fssc.compliance.mapper.MdViolationReasonMapper;
import com.fssc.system.service.ConfigService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 员工信用服务（共享运营实质化）。
 * <p>
 * 信用分驱动三大能力：
 * 1. 免审额度：信用分越高，单据金额低于免审额度时自动通过
 * 2. 派单优先级：高信用员工提交的单据优先派给高级审核员
 * 3. 质检频率：低信用员工质检必检，高信用随机抽检
 * <p>
 * 信用等级：
 * A(850-1000) 免审5000 B(700-849) 免审2000
 * C(500-699) 免审500 D(0-499) 免审0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCreditService {

    private final EmployeeCreditMapper creditMapper;
    private final ConfigService configService;
    private final MdViolationReasonMapper violationReasonMapper;

    private static final int INITIAL_SCORE = 600;
    private static final int MAX_SCORE = 1000;
    private static final int MIN_SCORE = 0;

    /** 信用免审总开关（sys_config，缺省开）：置 0 后信用照常累计，但不再自动放行 */
    private static final String KEY_AUTO_APPROVE = "review.credit.auto_approve.enabled";
    /** 免审最低信用等级（sys_config，缺省 C）：低于该档即便有额度也转人工 */
    private static final String KEY_MIN_LEVEL = "review.credit.auto_approve.min_level";

    /**
     * 获取或初始化用户信用。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeCredit getOrCreate(Long userId, String userName) {
        EmployeeCredit credit = findByUserId(userId);
        if (credit != null) {
            return credit;
        }
        credit = new EmployeeCredit();
        credit.setUserId(userId);
        credit.setUserName(userName);
        credit.setCreditScore(INITIAL_SCORE);
        credit.setCreditLevel(calculateLevel(INITIAL_SCORE));
        credit.setAutoApproveLimit(calculateAutoApproveLimit(INITIAL_SCORE));
        credit.setTotalSubmitted(0);
        credit.setTotalApproved(0);
        credit.setTotalRejected(0);
        credit.setViolationCount(0);
        creditMapper.insert(credit);
        log.info("初始化员工信用: userId={}, score={}", userId, INITIAL_SCORE);
        return credit;
    }

    public EmployeeCredit findByUserId(Long userId) {
        if (userId == null) return null;
        return creditMapper.selectOne(
                new LambdaQueryWrapper<EmployeeCredit>()
                        .eq(EmployeeCredit::getUserId, userId)
                        .last("limit 1"));
    }

    /**
     * 检查是否可免审（金额低于信用等级对应的免审额度）。
     */
    /**
     * 是否允许"信用免审"（跳过人工审核环节）。
     *
     * <p>三级判定，任一不过即转人工：
     * <ol>
     *   <li>总开关 {@code review.credit.auto_approve.enabled}（缺省开）——关掉只累计不放行；</li>
     *   <li>信用等级不低于 {@code review.credit.auto_approve.min_level}（缺省 C）——不想放过 C 级就改 B，不用改代码；</li>
     *   <li>金额在本人免审额度内（额度由分数算：A≥850→5000 / B≥700→2000 / C≥500→500 / D→0）。</li>
     * </ol>
     *
     * <p><b>免审只跳过人工审核环节</b>，不跳过预算校验与合规校验（那两条在提交/预检阶段已经跑过）。
     */
    public boolean canAutoApprove(Long userId, BigDecimal amount) {
        if (!autoApproveEnabled()) {
            return false;
        }
        EmployeeCredit credit = findByUserId(userId);
        if (credit == null) {
            return false;
        }
        if (levelRank(credit.getCreditLevel()) < levelRank(minAutoApproveLevel())) {
            return false;
        }
        BigDecimal limit = credit.getAutoApproveLimit();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return amount != null && amount.compareTo(limit) <= 0;
    }

    /** 总开关缺省开：参数缺失/为空都按"开"处理，与铺底前的设计口径一致 */
    private boolean autoApproveEnabled() {
        String v = configService.getValue(KEY_AUTO_APPROVE);
        if (v == null || v.isBlank()) {
            return true;
        }
        String t = v.trim();
        return "1".equals(t) || "true".equalsIgnoreCase(t) || "Y".equalsIgnoreCase(t);
    }

    /** 最低免审等级，缺省 C；非法值一律回落 C，避免写错参数把免审全关或全开 */
    private String minAutoApproveLevel() {
        String v = configService.getValue(KEY_MIN_LEVEL);
        if (v == null || v.isBlank()) {
            return "C";
        }
        String t = v.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (t) {
            case "A", "B", "C", "D" -> t;
            default -> "C";
        };
    }

    /** A=4 B=3 C=2 D=1，未知=0（宁可判低，不可误放） */
    private int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "A" -> 4;
            case "B" -> 3;
            case "C" -> 2;
            case "D" -> 1;
            default -> 0;
        };
    }

    /**
     * 单据提交时调用：累计提交数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onSubmitted(Long userId) {
        EmployeeCredit credit = getOrCreate(userId, null);
        LambdaUpdateWrapper<EmployeeCredit> uw = new LambdaUpdateWrapper<>();
        uw.eq(EmployeeCredit::getId, credit.getId())
                .setSql("total_submitted = total_submitted + 1");
        creditMapper.update(null, uw);
    }

    /**
     * 单据自动通过时调用：累计通过数 + 信用加分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long userId) {
        EmployeeCredit credit = findByUserId(userId);
        if (credit == null) return;
        adjustScore(credit, 5, "单据自动通过");
        LambdaUpdateWrapper<EmployeeCredit> uw = new LambdaUpdateWrapper<>();
        uw.eq(EmployeeCredit::getId, credit.getId())
                .setSql("total_approved = total_approved + 1");
        creditMapper.update(null, uw);
    }

    /**
     * 单据被驳回时调用：累计驳回数 + 信用扣分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long userId, String reason) {
        EmployeeCredit credit = findByUserId(userId);
        if (credit == null) return;
        adjustScore(credit, -15, "单据被驳回: " + reason);
        LambdaUpdateWrapper<EmployeeCredit> uw = new LambdaUpdateWrapper<>();
        uw.eq(EmployeeCredit::getId, credit.getId())
                .setSql("total_rejected = total_rejected + 1");
        creditMapper.update(null, uw);
    }

    /**
     * 违规处罚：扣分 + 违规次数 +1。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onViolation(Long userId, int deductPoints, String reason) {
        EmployeeCredit credit = findByUserId(userId);
        if (credit == null) return;
        adjustScore(credit, -deductPoints, "违规处罚: " + reason);
        LambdaUpdateWrapper<EmployeeCredit> uw = new LambdaUpdateWrapper<>();
        uw.eq(EmployeeCredit::getId, credit.getId())
                .setSql("violation_count = violation_count + 1");
        creditMapper.update(null, uw);
        log.warn("员工违规: userId={}, 扣分={}, 原因={}", userId, deductPoints, reason);
    }

    /**
     * 按违规原因码扣分：分值取自 md_violation_reason，替代原先写死的一刀切（驳回 -15 / 质检不合格 -20）。
     *
     * 管理口径一变就得改代码发版的话，这条链永远跟不上制度调整。
     *
     * <p>原因码不存在或已停用时<b>直接报错</b>，不静默按 0 分处理——否则"扣了 0 分"会被当成扣过了。
     *
     * @param note 补充说明（质检退回原因等），拼进信用档案的调整原因里便于追溯
     * @return 实际扣掉的分值（正数）
     */
    @Transactional(rollbackFor = Exception.class)
    public int onViolationByReason(Long userId, String reasonCode, String note) {
        MdViolationReason reason = violationReasonMapper.selectOne(new LambdaQueryWrapper<MdViolationReason>()
                .eq(MdViolationReason::getReasonCode, reasonCode)
                .eq(MdViolationReason::getStatus, "NORMAL")
                .eq(MdViolationReason::getDelFlag, 0)
                .last("limit 1"));
        if (reason == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "违规原因不存在或已停用: " + reasonCode);
        }
        EmployeeCredit credit = getOrCreate(userId, null);
        int points = reason.getDeductPoints() == null ? 0 : reason.getDeductPoints();
        adjustScore(credit, -points,
                "违规：" + reason.getReasonName() + (StringUtils.hasText(note) ? "（" + note + "）" : ""));
        LambdaUpdateWrapper<EmployeeCredit> uw = new LambdaUpdateWrapper<>();
        uw.eq(EmployeeCredit::getId, credit.getId())
                .setSql("violation_count = violation_count + 1");
        creditMapper.update(null, uw);
        log.warn("员工违规（分类扣分）: userId={}, code={}, 扣分={}", userId, reasonCode, points);
        return points;
    }

    /** 违规原因字典（仅启用项，按 sort 升序；供质检退回下拉） */
    public java.util.List<MdViolationReason> listViolationReasons() {
        return violationReasonMapper.selectList(new LambdaQueryWrapper<MdViolationReason>()
                .eq(MdViolationReason::getStatus, "NORMAL")
                .eq(MdViolationReason::getDelFlag, 0)
                .orderByAsc(MdViolationReason::getSort));
    }

    /**
     * 质检通过加分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onQualityPass(Long userId) {
        adjustScore(findByUserId(userId), 3, "质检通过");
    }

    /**
     * 质检不合格扣分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onQualityFail(Long userId, String reason) {
        adjustScore(findByUserId(userId), -20, "质检不合格: " + reason);
    }

    /**
     * 手动调整信用分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustManually(Long userId, int delta, String reason) {
        EmployeeCredit credit = getOrCreate(userId, null);
        adjustScore(credit, delta, "手动调整: " + reason);
        log.info("手动调整信用: userId={}, delta={}, reason={}", userId, delta, reason);
    }

    private void adjustScore(EmployeeCredit credit, int delta, String reason) {
        if (credit == null) return;
        int newScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, credit.getCreditScore() + delta));
        String newLevel = calculateLevel(newScore);
        BigDecimal newLimit = calculateAutoApproveLimit(newScore);

        EmployeeCredit upd = new EmployeeCredit();
        upd.setId(credit.getId());
        upd.setCreditScore(newScore);
        upd.setCreditLevel(newLevel);
        upd.setAutoApproveLimit(newLimit);
        upd.setLastAdjustReason(reason);
        upd.setLastAdjustTime(LocalDateTime.now());
        creditMapper.updateById(upd);

        if (!newLevel.equals(credit.getCreditLevel())) {
            log.info("信用等级变更: userId={} {}->{} (score={})", credit.getUserId(), credit.getCreditLevel(), newLevel, newScore);
        }
    }

    public String calculateLevel(int score) {
        if (score >= 850) return "A";
        if (score >= 700) return "B";
        if (score >= 500) return "C";
        return "D";
    }

    public BigDecimal calculateAutoApproveLimit(int score) {
        if (score >= 850) return new BigDecimal("5000.00");
        if (score >= 700) return new BigDecimal("2000.00");
        if (score >= 500) return new BigDecimal("500.00");
        return BigDecimal.ZERO;
    }
}
