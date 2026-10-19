package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.UserContext;
import com.fssc.glex.entity.TaxRiskIndicator;
import com.fssc.glex.mapper.TaxRiskIndicatorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 税务风险指标服务（P1-3 税务一体化）。
 * <p>
 * 风险评分/等级/预警监控：进销项比对异常、税负率偏低、发票疑点等。
 * 风险等级：NORMAL正常 → ATTENTION关注 → WARNING预警 → HIGH高风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRiskService {

    private final TaxRiskIndicatorMapper riskMapper;

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
        // WS-6 税域收口：按数据范围收窄公司维度
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
        // WS-6 税域收口：按数据范围收窄公司维度
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
                return actual.compareTo(threshold.divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP)) < 0
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
        // WS-6 税域收口：单条读后写，按公司归属校验越权
        DataScopeKit.requireCompanyInScope(row.getCompanyId());
        TaxRiskIndicator upd = new TaxRiskIndicator();
        upd.setId(id);
        upd.setResolved(1);
        upd.setResolvedBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        upd.setResolvedTime(java.time.LocalDateTime.now());
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
        // WS-6 税域收口：按数据范围收窄公司维度
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
