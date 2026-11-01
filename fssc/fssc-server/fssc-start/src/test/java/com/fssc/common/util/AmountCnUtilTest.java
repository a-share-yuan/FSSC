// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AmountCnUtil 单测：纯逻辑，无 Spring 上下文。
 * 断言按标准金融大写规范，覆盖整数/角分/零/负数/大额（万、亿、亿+万+个位复合）。
 */
class AmountCnUtilTest {

    @Test
    void 空值返回null() {
        assertNull(AmountCnUtil.toCn(null));
    }

    @Test
    void 零返回null() {
        assertNull(AmountCnUtil.toCn(BigDecimal.ZERO));
        assertNull(AmountCnUtil.toCn(new BigDecimal("0.00")));
    }

    @Test
    void 负数返回null() {
        assertNull(AmountCnUtil.toCn(new BigDecimal("-12.34")));
    }

    @Test
    void 整数带角分() {
        assertEquals("壹仟贰佰叁拾肆元伍角陆分", AmountCnUtil.toCn(new BigDecimal("1234.56")));
    }

    @Test
    void 整元补整() {
        assertEquals("壹佰元整", AmountCnUtil.toCn(new BigDecimal("100.00")));
        assertEquals("壹元整", AmountCnUtil.toCn(new BigDecimal("1")));
    }

    @Test
    void 有分无角补零() {
        assertEquals("壹元零伍分", AmountCnUtil.toCn(new BigDecimal("1.05")));
    }

    @Test
    void 万位大额() {
        assertEquals("壹万元整", AmountCnUtil.toCn(new BigDecimal("10000")));
    }

    @Test
    void 亿位大额() {
        assertEquals("壹亿元整", AmountCnUtil.toCn(new BigDecimal("100000000")));
    }

    @Test
    void 亿万千个复合() {
        assertEquals("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖元整",
                AmountCnUtil.toCn(new BigDecimal("123456789")));
    }
}
