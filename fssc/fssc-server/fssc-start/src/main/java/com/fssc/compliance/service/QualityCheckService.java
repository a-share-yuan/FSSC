package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.EmployeeCredit;
import com.fssc.sharing.entity.OpQuality;
import com.fssc.sharing.mapper.OpQualityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 质检服务（P1-5 共享运营实质化）。
 * <p>
 * 质检类型：
 * - RANDOM: 随机抽检（高信用员工抽检率10%，低信用必检）
 * - FULL:   全检（信用D级员工或高风险单据）
 * - KEY:    关键节点检查
 * <p>
 * 质检结果驱动信用分联动：
 * PASS -> 信用分+3
 * BACK -> 信用分-20 + 退回整改
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityCheckService {

    private final OpQualityMapper qualityMapper;
    private final EmployeeCreditService creditService;

    /**
     * 创建质检记录并联动信用分。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpQuality record) {
        if (record.getBillId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据ID必填");
        }
        if (record.getCheckType() == null) {
            record.setCheckType("RANDOM");
        }
        if (record.getScore() == null) {
            record.setScore(80);
        }
        record.setCheckTime(LocalDateTime.now());
        qualityMapper.insert(record);
        log.info("质检记录创建: billId={}, type={}, score={}, result={}",
                record.getBillId(), record.getCheckType(), record.getScore(), record.getResult());

        // 联动信用分
        if (record.getAuditorId() != null) {
            if ("PASS".equals(record.getResult())) {
                creditService.onQualityPass(record.getAuditorId());
            } else if ("BACK".equals(record.getResult())) {
                creditService.onQualityFail(record.getAuditorId(), record.getDeductReason());
            }
        }
        return record.getId();
    }

    /**
     * 根据信用等级决定质检策略。
     */
    public String determineCheckType(String creditLevel) {
        if ("D".equals(creditLevel)) {
            return "FULL";
        }
        if ("C".equals(creditLevel)) {
            return "KEY";
        }
        // A/B 级随机抽检
        return "RANDOM";
    }

    /**
     * 判断是否需要质检（按信用等级和随机概率）。
     */
    public boolean shouldCheck(String creditLevel) {
        return switch (creditLevel) {
            case "D", "C" -> true;
            case "B" -> ThreadLocalRandom.current().nextDouble() < 0.15;
            case "A" -> ThreadLocalRandom.current().nextDouble() < 0.05;
            default -> true;
        };
    }

    /**
     * 分页查询质检记录。
     */
    public PageResult<OpQuality> page(long pn, long ps, Long billId, String checkType, String result) {
        LambdaQueryWrapper<OpQuality> qw = new LambdaQueryWrapper<>();
        if (billId != null) qw.eq(OpQuality::getBillId, billId);
        if (StringUtils.hasText(checkType)) qw.eq(OpQuality::getCheckType, checkType);
        if (StringUtils.hasText(result)) qw.eq(OpQuality::getResult, result);
        qw.orderByDesc(OpQuality::getCheckTime);
        Page<OpQuality> p = qualityMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /**
     * 质检统计。
     */
    public QualitySummary summary() {
        long total = qualityMapper.selectCount(new LambdaQueryWrapper<>());
        long pass = qualityMapper.selectCount(
                new LambdaQueryWrapper<OpQuality>().eq(OpQuality::getResult, "PASS"));
        long back = qualityMapper.selectCount(
                new LambdaQueryWrapper<OpQuality>().eq(OpQuality::getResult, "BACK"));
        long random = qualityMapper.selectCount(
                new LambdaQueryWrapper<OpQuality>().eq(OpQuality::getCheckType, "RANDOM"));
        long full = qualityMapper.selectCount(
                new LambdaQueryWrapper<OpQuality>().eq(OpQuality::getCheckType, "FULL"));
        double passRate = total > 0 ? (double) pass * 100 / total : 0;
        return new QualitySummary(total, pass, back, random, full,
                Math.round(passRate * 100.0) / 100.0);
    }

    public record QualitySummary(long total, long pass, long back, long randomCheck,
                                  long fullCheck, double passRate) {
    }
}
