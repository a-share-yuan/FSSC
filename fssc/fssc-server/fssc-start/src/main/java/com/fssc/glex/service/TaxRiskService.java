// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.UserContext;
import com.fssc.glex.entity.TaxRiskIndicator;
import com.fssc.glex.mapper.TaxRiskIndicatorMapper;
import com.fssc.report.mapper.ReportMapper;
import com.fssc.system.service.ConfigService;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 税务风险指标服务（税务一体化）。
 * <p>
 * 风险评分/等级/预警监控：进销项比对异常、税负率偏低、发票疑点等。
 * 风险等级：NORMAL正常 → ATTENTION关注 → WARNING预警 → HIGH高风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRiskService {

    private final TaxRiskIndicatorMapper riskMapper;
    /** 税负率自动算的数据源＝税负分析报表同一条 SQL（进项/销项/不含税收入） */
    private final ReportMapper reportMapper;
    /** 税负率阈值走系统参数，缺省 3.0%（我方自定口径，非厂商依据） */
    private final ConfigService configService;

    /**
     * 税负率自动算：只把「税负率」这一条做成自动指标，其余指标维持人工录入。
     *
     * <p>口径：{@code 税负率 = (销项税额 − 进项税额) / 销项不含税收入 × 100}，取数即
     * {@code ReportMapper.taxBurdenAnalysis}（与「税负分析」报表同一条 SQL，两处不再各算一份）。
     * 阈值取系统参数 {@code tax.burden.threshold}，缺省 3.0；低于阈值即按 {@link #assessLevel} 判级。
     *
     * <p>同期间同公司已有手工/历史 TAX_BURDEN 指标时不覆盖其阈值，只刷新 actualValue/level，保留人工配置。
     *
     * @return 本期税负率指标；本期无销项收入（分母为 0）时返回 null，不伪造 0% 指标
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public TaxRiskIndicator computeTaxBurden(String period) {
        if (period == null || period.isBlank() || period.length() != 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "属期格式应为 yyyyMM");
        }
        String ym = period.substring(0, 4) + "-" + period.substring(4, 6);
        Long scopeCompany = DataScopeKit.scopedCompanyId();
        List<Map<String, Object>> rows = reportMapper.taxBurdenAnalysis(scopeCompany);
        Map<String, Object> hit = null;
        for (Map<String, Object> r : rows) {
            if (ym.equals(String.valueOf(r.get("period")))) {
                hit = r;
                break;
            }
        }
        if (hit == null) {
            log.info("税负率计算 {}：税负分析报表无本期进销项数据，跳过", period);
            return null;
        }
        BigDecimal base = toDecimal(hit.get("taxBase"));
        BigDecimal netTax = toDecimal(hit.get("netTax"));
        if (base == null || base.signum() <= 0) {
            log.info("税负率计算 {}：销项不含税收入为 0，税负率无意义，跳过", period);
            return null;
        }
        BigDecimal burden = netTax.multiply(BigDecimal.valueOf(100))
                .divide(base, 4, RoundingMode.HALF_UP);
        BigDecimal threshold = resolveBurdenThreshold();

        TaxRiskIndicator row = riskMapper.selectOne(new LambdaQueryWrapper<TaxRiskIndicator>()
                .eq(TaxRiskIndicator::getRiskType, "TAX_BURDEN")
                .eq(TaxRiskIndicator::getPeriod, period)
                .eq(scopeCompany != null, TaxRiskIndicator::getCompanyId, scopeCompany)
                .orderByDesc(TaxRiskIndicator::getId)
                .last("limit 1"));
        String level = assessLevel(threshold, burden, "TAX_BURDEN");
        boolean create = row == null;
        if (create) {
            row = new TaxRiskIndicator();
            row.setCompanyId(scopeCompany);
            row.setIndicatorCode("TAX_BURDEN");
            row.setIndicatorName("增值税税负率");
            row.setRiskType("TAX_BURDEN");
            row.setPeriod(period);
            row.setThresholdValue(threshold);
            row.setResolved(0);
        }
        row.setActualValue(burden);
        row.setRiskLevel(level);
        row.setRiskScore(calculateScore(level, burden.subtract(threshold).abs().multiply(BigDecimal.TEN)));
        row.setDescription("税负率 " + burden.setScale(2, RoundingMode.HALF_UP) + "%（阈值 "
                + threshold.setScale(2, RoundingMode.HALF_UP) + "%），应纳税额 "
                + netTax.setScale(2, RoundingMode.HALF_UP) + " / 不含税收入 "
                + base.setScale(2, RoundingMode.HALF_UP));
        row.setSuggestion("NORMAL".equals(level) ? "税负率正常，无需处理"
                : "税负率低于阈值，请核查进项抵扣与收入确认期间归属");
        if (create) {
            riskMapper.insert(row);
        } else {
            riskMapper.updateById(row);
        }
        log.info("税负率自动计算 {}：{}%（阈值 {}%，判定 {}）", period,
                burden.setScale(2, RoundingMode.HALF_UP), threshold, level);
        return row;
    }

    private BigDecimal resolveBurdenThreshold() {
        try {
            String v = configService.getValue("tax.burden.threshold");
            if (v != null && !v.isBlank()) {
                return new BigDecimal(v.trim());
            }
        } catch (Exception ex) {
            log.debug("读取税负率阈值参数失败，用缺省 3.0: {}", ex.getMessage());
        }
        return new BigDecimal("3.0");
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 分页查询风险指标。
     */
    public PageResult<TaxRiskIndicator> page(long pn, long ps, String riskLevel, Boolean unresolvedOnly) {
        LambdaQueryWrapper<TaxRiskIndicator> qw = new LambdaQueryWrapper<>();
        if (riskLevel != null && !riskLevel.isEmpty()) {
            qw.eq(TaxRiskIndicator::getRiskLevel, riskLevel);
        }
        if (Boolean.TRUE.equals(unresolvedOnly)) {
            qw.eq(TaxRiskIndicator::getResolved, 0);
        }
        // WS-6 税域办结：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxRiskIndicator::getCompanyId, null, null);
        qw.orderByDesc(TaxRiskIndicator::getRiskScore);
        Page<TaxRiskIndicator> p = riskMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /**
     * 查询当前未处理的高风险预警。
     */
    public List<TaxRiskIndicator> listHighRisk() {
        LambdaQueryWrapper<TaxRiskIndicator> qw = new LambdaQueryWrapper<TaxRiskIndicator>()
                .eq(TaxRiskIndicator::getResolved, 0)
                .in(TaxRiskIndicator::getRiskLevel, "WARNING", "HIGH")
                .orderByDesc(TaxRiskIndicator::getRiskScore);
        // WS-6 税域办结：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxRiskIndicator::getCompanyId, null, null);
        return riskMapper.selectList(qw);
    }

    /**
     * 评估风险等级（根据实际值与阈值比较）。
     */
    public String assessLevel(BigDecimal threshold, BigDecimal actual, String riskType) {
        if (threshold == null || actual == null) {
            return "NORMAL";
        }
        int cmp = actual.compareTo(threshold);
        // 税负率类：低于阈值=风险
        if ("TAX_BURDEN".equals(riskType)) {
            if (cmp < 0) {
                return actual.compareTo(threshold.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)) < 0
                        ? "HIGH" : "WARNING";
            }
            return "NORMAL";
        }
        // 进销项比例类：高于阈值=风险
        if ("INPUT_OUTPUT_RATIO".equals(riskType)) {
            if (cmp > 0) {
                return "ATTENTION";
            }
            return "NORMAL";
        }
        // 发票异常类：>0即高风险
        if ("INVOICE_ANOMALY".equals(riskType)) {
            return cmp > 0 ? "HIGH" : "NORMAL";
        }
        return "NORMAL";
    }

    /**
     * 计算风险评分（0-100）。
     */
    public int calculateScore(String riskLevel, BigDecimal severity) {
        int base = switch (riskLevel) {
            case "HIGH" -> 80;
            case "WARNING" -> 50;
            case "ATTENTION" -> 25;
            default -> 0;
        };
        if (severity != null) {
            base += severity.intValue();
            if (base > 100) base = 100;
        }
        return base;
    }

    /**
     * 标记风险已处理。
     */
    public void resolve(Long id, String remark) {
        TaxRiskIndicator row = riskMapper.selectById(id);
        if (row == null) return;
        // WS-6 税域办结：单条读后写，按公司归属校验越权
        DataScopeKit.requireCompanyInScope(row.getCompanyId());
        TaxRiskIndicator upd = new TaxRiskIndicator();
        upd.setId(id);
        upd.setResolved(1);
        upd.setResolvedBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        upd.setResolvedTime(LocalDateTime.now());
        upd.setResolvedRemark(remark);
        riskMapper.updateById(upd);
        log.info("税务风险 {} 已处理: {}", id, remark);
    }

    /**
     * 查询当期风险汇总统计。
     */
    public RiskSummary summary(String period) {
        LambdaQueryWrapper<TaxRiskIndicator> qw = new LambdaQueryWrapper<TaxRiskIndicator>()
                .eq(period != null, TaxRiskIndicator::getPeriod, period)
                .eq(TaxRiskIndicator::getResolved, 0);
        // WS-6 税域办结：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxRiskIndicator::getCompanyId, null, null);
        List<TaxRiskIndicator> all = riskMapper.selectList(qw);
        long high = all.stream().filter(r -> "HIGH".equals(r.getRiskLevel())).count();
        long warning = all.stream().filter(r -> "WARNING".equals(r.getRiskLevel())).count();
        long attention = all.stream().filter(r -> "ATTENTION".equals(r.getRiskLevel())).count();
        long normal = all.stream().filter(r -> "NORMAL".equals(r.getRiskLevel())).count();
        return new RiskSummary((int) high, (int) warning, (int) attention, (int) normal, all.size());
    }

    private String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 风险汇总统计。
     */
    public record RiskSummary(int high, int warning, int attention, int normal, int total) {
        public int unresolvedCount() {
            return high + warning + attention;
        }
    }
}
