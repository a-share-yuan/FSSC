package com.fssc.masterdata.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ExchangeRateService 汇率推算纯逻辑单测（不依赖 Spring/Mapper）：
 * 覆盖 composeRate 兜底顺序（直查→反向倒数→经 CNY 交叉→无价放行）与
 * inverseRate/crossRate 的 6 位精度、除零/空值容错。
 */
class ExchangeRateServiceMathTest {

    // ---------------- 直查 ----------------

    @Test
    void 直查命中优先_忽略反向与交叉() {
        BigDecimal r = ExchangeRateService.composeRate(
                new BigDecimal("7.13"), new BigDecimal("0.15"), new BigDecimal("7.13"), new BigDecimal("7.85"));
        assertEquals(new BigDecimal("7.13"), r);
    }

    // ---------------- 反向 1/rate（6 位） ----------------

    @Test
    void 无反向表价时_倒数保留6位() {
        // 仅有 CNY→USD 7.13，查 USD→CNY = 1/7.13 = 0.140252（6 位 HALF_UP）
        BigDecimal r = ExchangeRateService.composeRate(null, new BigDecimal("7.13"), null, null);
        assertEquals(new BigDecimal("0.140252"), r);
        // JPY：1/0.048 = 20.833333
        assertEquals(new BigDecimal("20.833333"), ExchangeRateService.inverseRate(new BigDecimal("0.048")));
    }

    @Test
    void 反向优先于交叉() {
        BigDecimal r = ExchangeRateService.composeRate(null, new BigDecimal("7.85"),
                new BigDecimal("7.13"), new BigDecimal("7.85"));
        assertEquals(0, r.compareTo(ExchangeRateService.inverseRate(new BigDecimal("7.85"))));
    }

    // ---------------- 经 CNY 交叉（6 位） ----------------

    @Test
    void 交叉价等于两本位币价相除() {
        // USD→EUR = 7.13/7.85 = 0.908280
        BigDecimal r = ExchangeRateService.composeRate(null, null,
                new BigDecimal("7.13"), new BigDecimal("7.85"));
        assertEquals(new BigDecimal("0.908280"), r);
        // GBP→SGD = 9.08/5.55 = 1.636036
        assertEquals(new BigDecimal("1.636036"),
                ExchangeRateService.crossRate(new BigDecimal("9.08"), new BigDecimal("5.55")));
        // 经 CNY 结果恒为 6 位小数
        assertEquals(6, r.scale());
    }

    // ---------------- 无价 / 容错 ----------------

    @Test
    void 全缺价返回null_放行手填() {
        assertNull(ExchangeRateService.composeRate(null, null, null, new BigDecimal("7.85")));
        assertNull(ExchangeRateService.composeRate(null, null, BigDecimal.ZERO, new BigDecimal("7.85")));
    }

    @Test
    void 除零与空值安全() {
        assertNull(ExchangeRateService.inverseRate(null));
        assertNull(ExchangeRateService.inverseRate(BigDecimal.ZERO));
        assertNull(ExchangeRateService.crossRate(new BigDecimal("7.13"), BigDecimal.ZERO));
        assertNull(ExchangeRateService.crossRate(null, new BigDecimal("7.85")));
    }
}
