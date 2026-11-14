package com.fssc.expense.service;

import com.fssc.masterdata.entity.MdExpenseStandard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 海外报账②③纯规则集：出国判定 / 境外标准档位匹配 / 天数补齐 / 补贴按天核算。
 *
 * <p>全部静态纯函数，不依赖 Spring 与数据库，供 ExpenseBillService（提交标准校验换源）、
 * ExpenseDetailService（差旅行保存）调用，可直接单测。
 */
public final class AbroadStandardRule {

    /** 国内识别名称表：目的地命中任一则视为国内，不触发境外标准（含官方全称与港澳台、常见英文写法） */
    private static final String[] DOMESTIC_KEYWORDS = {
            "中国", "中华人民共和国", "香港", "澳门", "台湾", "china", "prc", "macau", "hong kong", "taiwan"
    };

    private AbroadStandardRule() {
    }

    /**
     * 出国判定：任一差旅行目的地国家非空且不属于国内名称表 → true。
     * 与 exp_bill.ext_json.abroadFlag（WfEngineFacade.resolveAbroad 读）并存互补：
     * abroadFlag 驱动流程分流，本判定驱动标准/补贴查询换源。
     */
    public static boolean resolveAbroad(Iterable<String> destCountries) {
        if (destCountries == null) {
            return false;
        }
        for (String c : destCountries) {
            if (c != null && !c.isBlank() && !isDomestic(c)) {
                return true;
            }
        }
        return false;
    }

    /** 国内判定：命中国内名称表任一关键字（大小写不敏感，兼容"中华人民共和国"/港澳台/CHINA 等写法）。 */
    public static boolean isDomestic(String country) {
        if (country == null || country.isBlank()) {
            return true; // 空=留空即国内
        }
        String lower = country.toLowerCase();
        for (String kw : DOMESTIC_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 境外标准档位匹配：在 abroad=1 候选集中先取 country_tier 精确档，
     * 无匹配退回境外通用档（country_tier=NULL），再无返回 null（由调用方回退国内标准）。
     * 注：国家→档位（DEV/EMG/OTH）映射规则暂由调用方兜底传 DEV，
     * 留待 md_sys_config 配置化后替换。
     */
    public static MdExpenseStandard pickStandard(List<MdExpenseStandard> abroadCandidates, String countryTier) {
        if (abroadCandidates == null || abroadCandidates.isEmpty()) {
            return null;
        }
        if (countryTier != null && !countryTier.isBlank()) {
            for (MdExpenseStandard s : abroadCandidates) {
                if (countryTier.equalsIgnoreCase(s.getCountryTier())) {
                    return s;
                }
            }
        }
        for (MdExpenseStandard s : abroadCandidates) {
            if (s.getCountryTier() == null || s.getCountryTier().isBlank()) {
                return s;
            }
        }
        return null;
    }

    /**
     * 出差天数补齐：days 已有值（&gt;0）原样返回；否则按 起止日期含首末日 全算
     * end − begin + 1。半天/按比例折算（首尾按 0.5 天等）留待配置化扩展。
     */
    public static BigDecimal resolveDays(BigDecimal days, LocalDate beginDate, LocalDate endDate) {
        if (days != null && days.compareTo(BigDecimal.ZERO) > 0) {
            return days;
        }
        if (beginDate == null || endDate == null || endDate.isBefore(beginDate)) {
            return days;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(beginDate, endDate) + 1);
    }

    /**
     * 补贴按天核算：allowance = days × allowance_daily（本位币，2 位小数四舍五入）。
     * 任一为空或非正返回 null（调用方跳过、不报错——无境外补贴标准时保持人工填报规则）。
     */
    public static BigDecimal calcAllowance(BigDecimal days, BigDecimal allowanceDaily) {
        if (days == null || allowanceDaily == null
                || days.compareTo(BigDecimal.ZERO) <= 0 || allowanceDaily.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return days.multiply(allowanceDaily).setScale(2, RoundingMode.HALF_UP);
    }
}
