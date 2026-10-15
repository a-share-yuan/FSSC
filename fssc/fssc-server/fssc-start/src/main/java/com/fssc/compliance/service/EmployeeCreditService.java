package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.compliance.entity.EmployeeCredit;
import com.fssc.compliance.mapper.EmployeeCreditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 员工信用服务（P1-5 共享运营实质化）。
 * <p>
 * 信用分驱动三大能力：
 * 1. 免审额度：信用分越高，单据金额低于免审额度时自动通过
 * 2. 派单优先级：高信用员工提交的单据优先派给高级审核员
 * 3. 质检频率：低信用员工质检必检，高信用随机抽检
 * <p>
 * 信用等级：
 * A(850-1000) 免审5000 B(700-849) 免审2000
 * C(500-699)  免审500  D(0-499)   免审0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCreditService {

    private final EmployeeCreditMapper creditMapper;

    private static final int INITIAL_SCORE = 600;
    private static final int MAX_SCORE = 1000;
    private static final int MIN_SCORE = 0;

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
    public boolean canAutoApprove(Long userId, BigDecimal amount) {
        EmployeeCredit credit = findByUserId(userId);
        if (credit == null) {
            return false;
        }
        if ("D".equals(credit.getCreditLevel())) {
            return false;
        }
        BigDecimal limit = credit.getAutoApproveLimit();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return amount != null && amount.compareTo(limit) <= 0;
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
        upd.setLastAdjustTime(java.time.LocalDateTime.now());
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
