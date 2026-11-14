package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdExchangeRate;
import com.fssc.masterdata.mapper.MdExchangeRateMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 ExchangeRate 服务（带 tenant_id，租户拦截器自动隔离） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final MdExchangeRateMapper mapper;

    public PageResult<MdExchangeRate> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdExchangeRate> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdExchangeRate::getFromCurrency, keyword).or().like(MdExchangeRate::getToCurrency, keyword));
        }
        qw.orderByDesc(MdExchangeRate::getId);
        Page<MdExchangeRate> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdExchangeRate e) {
        e.setId(null);
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdExchangeRate e) {
        mapper.updateById(e);
    }

    // 海外报账①：汇率查询 / 月度导入

    /** 交叉/反算汇率保留小数位 */
    private static final int RATE_SCALE = 6;
    /** 本位币种 */
    private static final String HOME_CURRENCY = "CNY";

    /**
     * 取某日适用汇率（参考 汇率配置 查询语义：按 币种对 + 生效日 匹配，取「≤业务日 最近一条」）：
     * 优先 MIDDLE，同日多类型时中间价优先。
     * <p>区间列 expire_date 不再作为「查不到」的条件——原「区间包含」写法在月度汇率
     * 未续期时会让所有外币单据查不到价，调用方静默按 1:1 折算，
     * 造成本位币低估。现改为过期行沿用上一条价并告警，真正的缺价（该币种对从未维护过 ≤date 的行）
     * 返回 null 交由调用方 fail-closed 拦截。
     * <p>兜底顺序：直查 from→to → 反向取倒数（1/rate，6 位）→ 经 CNY 交叉（USD→EUR = USD_CNY / EUR_CNY，6 位）。
     *
     * @return 无任何可用价（含历史行）时返回 null
     */
    public BigDecimal lookup(String fromCurrency, String toCurrency, LocalDate date) {
        if (!StringUtils.hasText(fromCurrency) || !StringUtils.hasText(toCurrency) || date == null) {
            return null;
        }
        String from = normCurrency(fromCurrency);
        String to = normCurrency(toCurrency);
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        return composeRate(selectRate(from, to, date), selectRate(to, from, date),
                rateToHome(from, date), rateToHome(to, date));
    }

    /** 外币→CNY 中间价（同币种=1），交叉换算的原料 */
    private BigDecimal rateToHome(String currency, LocalDate date) {
        return HOME_CURRENCY.equals(currency) ? BigDecimal.ONE : selectRate(currency, HOME_CURRENCY, date);
    }

    /**
     * 纯逻辑组合（可脱离 Spring 单测）：直查命中即用；否则反向倒数；否则经 CNY 交叉；全缺返回 null。
     */
    static BigDecimal composeRate(BigDecimal direct, BigDecimal reverse, BigDecimal fromCny, BigDecimal toCny) {
        if (direct != null && direct.signum() > 0) {
            return direct;
        }
        if (reverse != null && reverse.signum() > 0) {
            return inverseRate(reverse);
        }
        if (fromCny != null && toCny != null && fromCny.signum() > 0 && toCny.signum() > 0) {
            return crossRate(fromCny, toCny);
        }
        return null;
    }

    /** 1/rate，6 位四舍五入 */
    static BigDecimal inverseRate(BigDecimal rate) {
        return rate == null || rate.signum() <= 0
                ? null : BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** A_CNY / B_CNY = A→B 交叉价，6 位四舍五入 */
    static BigDecimal crossRate(BigDecimal fromCny, BigDecimal toCny) {
        return fromCny == null || toCny == null || toCny.signum() <= 0
                ? null : fromCny.divide(toCny, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 表内取价：取 effect_date&lt;=date 的最新一条（同日多类型优先中间价）；expire_date 只用于告警，不再判「查不到」 */
    private BigDecimal selectRate(String from, String to, LocalDate date) {
        List<MdExchangeRate> rows = mapper.selectList(new LambdaQueryWrapper<MdExchangeRate>()
                .eq(MdExchangeRate::getFromCurrency, from)
                .eq(MdExchangeRate::getToCurrency, to)
                .le(MdExchangeRate::getEffectDate, date));
        MdExchangeRate hit = rows.stream()
                .filter(r -> r.getRate() != null && r.getRate().signum() > 0)
                .max(Comparator.comparing(MdExchangeRate::getEffectDate)
                        .thenComparingInt(r -> "MIDDLE".equals(r.getRateType()) ? 1 : 0))
                .orElse(null);
        if (hit == null) {
            return null;
        }
        if (hit.getExpireDate() != null && hit.getExpireDate().isBefore(date)) {
            log.warn("汇率 {}→{} 无覆盖 {} 的记录，沿用生效日 {}（失效日 {}）的过期价 {}，请尽快维护汇率表",
                    from, to, date, hit.getEffectDate(), hit.getExpireDate(), hit.getRate());
        }
        return hit.getRate();
    }

    /**
     * 月度批量导入（upsert）：键 = 租户 + from + to + effect_date（rateType 缺省 MIDDLE），source=IMPORT。
     *
     * @return 成功写入数据库行数
     */
    public int importMonthly(List<MdExchangeRate> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入数据为空");
        }
        int count = 0;
        for (MdExchangeRate row : rows) {
            if (!StringUtils.hasText(row.getFromCurrency()) || !StringUtils.hasText(row.getToCurrency())
                    || row.getRate() == null || row.getRate().signum() <= 0 || row.getEffectDate() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "汇率行不完整（from/to/rate/effect_date 必填）: " + row.getFromCurrency() + "→" + row.getToCurrency());
            }
            row.setFromCurrency(normCurrency(row.getFromCurrency()));
            row.setToCurrency(normCurrency(row.getToCurrency()));
            if (!StringUtils.hasText(row.getRateType())) {
                row.setRateType("MIDDLE");
            }
            if (!StringUtils.hasText(row.getSource())) {
                row.setSource("IMPORT");
            }
            MdExchangeRate exist = mapper.selectOne(new LambdaQueryWrapper<MdExchangeRate>()
                    .eq(MdExchangeRate::getFromCurrency, row.getFromCurrency())
                    .eq(MdExchangeRate::getToCurrency, row.getToCurrency())
                    .eq(MdExchangeRate::getRateType, row.getRateType())
                    .eq(MdExchangeRate::getEffectDate, row.getEffectDate())
                    .last("limit 1"));
            if (exist == null) {
                row.setId(null);
                mapper.insert(row);
            } else {
                exist.setRate(row.getRate());
                exist.setExpireDate(row.getExpireDate() != null ? row.getExpireDate() : exist.getExpireDate());
                exist.setSource(row.getSource());
                mapper.updateById(exist);
            }
            count++;
        }
        return count;
    }

    /** CSV 导入模板（列：from,to,rate,effect_date,expire_date；# 开头为注释行） */
    public static final String CSV_TEMPLATE = String.join("\n",
            "# 月度汇率导入模板：外币,本位币,汇率,生效日,失效日（失效日可空=长期有效；# 开头与表头行自动跳过）",
            "from,to,rate,effect_date,expire_date",
            "USD,CNY,7.130000,2026-08-01,2026-08-31",
            "EUR,CNY,7.850000,2026-08-01,2026-08-31");

    /**
     * 解析 CSV 文本并月度导入（标准库实现，零新依赖）。
     * 列：from,to,rate,effect_date,expire_date；容忍 BOM/表头/注释/空行，日期接受 yyyy-MM-dd 与 yyyy/M/d。
     *
     * @return 成功导入行数
     */
    public int importCsv(String csvText) {
        if (!StringUtils.hasText(csvText)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "CSV 内容为空");
        }
        List<MdExchangeRate> rows = new ArrayList<>();
        for (String raw : csvText.replace("\uFEFF", "").split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] col = line.split(",");
            if (col.length < 4 || "from".equalsIgnoreCase(col[0].trim())) {
                continue;
            }
            MdExchangeRate e = new MdExchangeRate();
            e.setFromCurrency(col[0].trim().toUpperCase(Locale.ROOT));
            e.setToCurrency(col[1].trim().toUpperCase(Locale.ROOT));
            e.setRateType("MIDDLE");
            e.setSource("IMPORT");
            try {
                e.setRate(new BigDecimal(col[2].trim()));
                e.setEffectDate(parseDate(col[3]));
                e.setExpireDate(col.length > 4 && StringUtils.hasText(col[4]) ? parseDate(col[4]) : null);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "CSV 行格式错误: " + line);
            }
            rows.add(e);
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "CSV 无有效数据行（列：from,to,rate,effect_date,expire_date）");
        }
        return importMonthly(rows);
    }

    private static LocalDate parseDate(String text) {
        return LocalDate.parse(text.trim().replace('/', '-'));
    }

    private static String normCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
