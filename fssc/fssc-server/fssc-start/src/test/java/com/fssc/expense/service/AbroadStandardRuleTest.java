package com.fssc.expense.service;

import com.fssc.masterdata.entity.MdExpenseStandard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbroadStandardRule 单测（V1.3.10 海外报账②③）：纯逻辑，无 Spring 上下文。
 * 覆盖 出国判定（dest_country 非空且≠中国）+ 境外标准档位匹配（tier 精确→通用→无）+ days×补贴/日。
 */
class AbroadStandardRuleTest {

    private static MdExpenseStandard std(String tier, String allowanceDaily) {
        MdExpenseStandard s = new MdExpenseStandard();
        s.setAbroad(1);
        s.setCountryTier(tier);
        s.setAllowanceDaily(allowanceDaily == null ? null : new BigDecimal(allowanceDaily));
        return s;
    }

    // ---------- 出国判定 ----------

    @Test
    void 空目的地或空集合为国内() {
        assertFalse(AbroadStandardRule.resolveAbroad(null));
        assertFalse(AbroadStandardRule.resolveAbroad(Collections.emptyList()));
        assertFalse(AbroadStandardRule.resolveAbroad(Arrays.asList("", "  ", null)));
    }

    @Test
    void 含中国字样为国内() {
        assertFalse(AbroadStandardRule.resolveAbroad(Arrays.asList("中国", "中国澳门", "中华人民共和国")));
    }

    @Test
    void 任一非中国国家即出国() {
        assertTrue(AbroadStandardRule.resolveAbroad(Arrays.asList("中国", "新加坡")));
        assertTrue(AbroadStandardRule.resolveAbroad(Collections.singletonList("德国")));
    }

    // ---------- 档位匹配取标准 ----------

    @Test
    void tier精确匹配优先() {
        MdExpenseStandard dev = std("DEV", "500");
        MdExpenseStandard emg = std("EMG", "350");
        MdExpenseStandard generic = std(null, null);
        List<MdExpenseStandard> list = Arrays.asList(generic, emg, dev);
        assertSame(dev, AbroadStandardRule.pickStandard(list, "DEV"));
        assertSame(emg, AbroadStandardRule.pickStandard(list, "EMG"));
        assertSame(dev, AbroadStandardRule.pickStandard(list, "dev")); // 大小写不敏感
    }

    @Test
    void 无tier匹配退通用档再退null() {
        MdExpenseStandard dev = std("DEV", "500");
        MdExpenseStandard generic = std(null, null);
        assertSame(generic, AbroadStandardRule.pickStandard(Arrays.asList(dev, generic), "OTH"));
        assertNull(AbroadStandardRule.pickStandard(Collections.singletonList(dev), "OTH"));
        assertNull(AbroadStandardRule.pickStandard(null, "DEV"));
        assertNull(AbroadStandardRule.pickStandard(Collections.emptyList(), "DEV"));
    }

    // ---------- 天数补齐（首末日全算） ----------

    @Test
    void days已有值不覆盖() {
        assertEquals(new BigDecimal("3.0"),
                AbroadStandardRule.resolveDays(new BigDecimal("3.0"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 8)));
    }

    @Test
    void days缺失按起止日期含首末日() {
        assertEquals(new BigDecimal("8"),
                AbroadStandardRule.resolveDays(null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 8)));
        assertEquals(BigDecimal.ONE,
                AbroadStandardRule.resolveDays(BigDecimal.ZERO, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)));
    }

    @Test
    void 日期缺失或倒挂保持原值() {
        assertNull(AbroadStandardRule.resolveDays(null, null, LocalDate.of(2026, 3, 8)));
        assertNull(AbroadStandardRule.resolveDays(null, LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 1)));
    }

    // ---------- days×daily 补贴 ----------

    @Test
    void 补贴等于天数乘每日标准() {
        assertEquals(new BigDecimal("4000.00"),
                AbroadStandardRule.calcAllowance(new BigDecimal("8"), new BigDecimal("500")));
        // 半天小数 + 四舍五入两位
        assertEquals(new BigDecimal("175.00"),
                AbroadStandardRule.calcAllowance(new BigDecimal("0.5"), new BigDecimal("350")));
        assertEquals(new BigDecimal("116.67"),
                AbroadStandardRule.calcAllowance(new BigDecimal("0.5"), new BigDecimal("233.3333")));
    }

    @Test
    void 空值非正返回null跳过不报错() {
        assertNull(AbroadStandardRule.calcAllowance(null, new BigDecimal("500")));
        assertNull(AbroadStandardRule.calcAllowance(new BigDecimal("8"), null));
        assertNull(AbroadStandardRule.calcAllowance(BigDecimal.ZERO, new BigDecimal("500")));
        assertNull(AbroadStandardRule.calcAllowance(new BigDecimal("8"), BigDecimal.ZERO));
    }
}
