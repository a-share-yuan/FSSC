// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fssc.glex.service.FxRevaluationService.FxDirection;
import com.fssc.glex.service.FxRevaluationService.RevalResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 月末汇兑重估纯函数单测：computeDiff（重估本位币/差异/2 位精度 HALF_UP）与 direction（损益方向）。
 * 纯静态方法，无 Spring 上下文、无 mock 依赖。
 */
class FxRevaluationServiceTest {

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + " 实际 " + actual);
    }

    @Test
    void 重估本位币与差异() {
        // 原币 100 USD，账面 713.00（入账 7.13），期末 7.20 → 重估 720.00，差异 +7.00
        RevalResult r = FxRevaluationService.computeDiff(
                new BigDecimal("713.00"), new BigDecimal("100"), new BigDecimal("7.20"));
        eq("720.00", r.getRevalLocal());
        eq("7.00", r.getDiff());
    }

    @Test
    void 精度保留2位HALF_UP() {
        // 1 × 8.235 = 8.235 → HALF_UP → 8.24
        RevalResult r = FxRevaluationService.computeDiff(
                BigDecimal.ZERO, new BigDecimal("1"), new BigDecimal("8.235"));
        eq("8.24", r.getRevalLocal());
    }

    @Test
    void AP涨_损失方向() {
        // 外币应付：期末涨 → 负债增加 → 损失
        assertEquals(FxDirection.LOSS, FxRevaluationService.direction("AP", new BigDecimal("7.00")));
    }

    @Test
    void AP跌_收益方向() {
        assertEquals(FxDirection.GAIN, FxRevaluationService.direction("AP", new BigDecimal("-13.00")));
    }

    @Test
    void AR涨_收益方向与AP反向() {
        // 外币应收（资产）：期末涨 → 资产增值 → 收益，与应付相反
        assertEquals(FxDirection.GAIN, FxRevaluationService.direction("AR", new BigDecimal("5.00")));
        assertEquals(FxDirection.LOSS, FxRevaluationService.direction("AR", new BigDecimal("-5.00")));
    }

    @Test
    void 借款LOAN按资产类同应收() {
        assertEquals(FxDirection.GAIN, FxRevaluationService.direction("LOAN", new BigDecimal("2.00")));
        assertEquals(FxDirection.LOSS, FxRevaluationService.direction("LOAN", new BigDecimal("-2.00")));
    }

    @Test
    void 差异为零跳过() {
        assertEquals(FxDirection.SKIP, FxRevaluationService.direction("AP", BigDecimal.ZERO));
        assertEquals(FxDirection.SKIP, FxRevaluationService.direction("AR", null));
    }
}
