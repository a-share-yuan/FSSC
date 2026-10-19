package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.SatisfactionSurvey;
import com.fssc.compliance.mapper.SatisfactionSurveyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 满意度服务（P1-5 共享运营实质化）。
 * <p>
 * 满意度评分 1-5 驱动：
 * - 评价 < 3 分触发预警
 * - 平均分反馈到审核员绩效看板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SatisfactionService {

    private final SatisfactionSurveyMapper surveyMapper;

    /**
     * 创建满意度评价。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SatisfactionSurvey record) {
        if (record.getBillId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据ID必填");
        }
        if (record.getScore() == null || record.getScore() < 1 || record.getScore() > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评分范围1-5");
        }
        surveyMapper.insert(record);
        if (record.getScore() <= 2) {
            log.warn("低满意度预警: billId={}, score={}, comment={}",
                    record.getBillId(), record.getScore(), record.getComment());
        }
        return record.getId();
    }

    /**
     * 分页查询。
     */
    public PageResult<SatisfactionSurvey> page(long pn, long ps, Long billId, String serviceCode) {
        LambdaQueryWrapper<SatisfactionSurvey> qw = new LambdaQueryWrapper<>();
        if (billId != null) qw.eq(SatisfactionSurvey::getBillId, billId);
        if (StringUtils.hasText(serviceCode)) qw.eq(SatisfactionSurvey::getServiceCode, serviceCode);
        qw.orderByDesc(SatisfactionSurvey::getCreateTime);
        Page<SatisfactionSurvey> p = surveyMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /**
     * 满意度汇总统计。
     */
    public Map<String, Object> summary() {
        long total = surveyMapper.selectCount(new LambdaQueryWrapper<>());
        long excellent = surveyMapper.selectCount(
                new LambdaQueryWrapper<SatisfactionSurvey>().ge(SatisfactionSurvey::getScore, 4));
        long poor = surveyMapper.selectCount(
                new LambdaQueryWrapper<SatisfactionSurvey>().le(SatisfactionSurvey::getScore, 2));

        double avgScore = 0;
        if (total > 0) {
            long sumScore = surveyMapper.selectList(new LambdaQueryWrapper<SatisfactionSurvey>())
                    .stream()
                    .mapToInt(s -> s.getScore() != null ? s.getScore() : 0)
                    .sum();
            avgScore = Math.round((double) sumScore / total * 100.0) / 100.0;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("excellentCount", excellent);
        result.put("poorCount", poor);
        result.put("avgScore", avgScore);
        result.put("satisfactionRate", total > 0
                ? Math.round((double) excellent * 100 / total * 100.0) / 100.0
                : 0);
        return result;
    }
}
