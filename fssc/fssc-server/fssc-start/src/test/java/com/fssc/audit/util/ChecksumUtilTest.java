package com.fssc.audit.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChecksumUtil 单测：纯逻辑，无 Spring 上下文。
 * 覆盖：同字段同哈希、任一字段变则哈希变、金额归一化、常量时间比较 safeEquals 行为。
 */
class ChecksumUtilTest {

    @Test
    void 相同字段得到相同哈希() {
        String h1 = ChecksumUtil.sha256("EXP001", new BigDecimal("100.00"), "ACC1");
        String h2 = ChecksumUtil.sha256("EXP001", new BigDecimal("100.00"), "ACC1");
        assertEquals(h1, h2);
    }

    @Test
    void 任一字段变化则哈希变化() {
        String base = ChecksumUtil.sha256("EXP001", new BigDecimal("100.00"), "ACC1");
        assertNotEquals(base, ChecksumUtil.sha256("EXP002", new BigDecimal("100.00"), "ACC1"), "单号变");
        assertNotEquals(base, ChecksumUtil.sha256("EXP001", new BigDecimal("100.01"), "ACC1"), "金额变");
        assertNotEquals(base, ChecksumUtil.sha256("EXP001", new BigDecimal("100.00"), "ACC2"), "账户变");
    }

    @Test
    void 金额按去尾零归一化() {
        // 1.0 与 1.00 应归一化为同一哈希（stripTrailingZeros）
        assertEquals(ChecksumUtil.sha256(new BigDecimal("1.0")),
                ChecksumUtil.sha256(new BigDecimal("1.00")));
    }

    @Test
    void null字段归一化为空串仍稳定() {
        assertEquals(ChecksumUtil.sha256("A", null, "B"), ChecksumUtil.sha256("A", null, "B"));
        assertNotEquals(ChecksumUtil.sha256("A", null, "B"), ChecksumUtil.sha256("A", "X", "B"));
    }

    @Test
    void safeEquals_相同返回true() {
        String h = ChecksumUtil.sha256("EXP001", "ACC1");
        assertTrue(ChecksumUtil.safeEquals(h, h));
    }

    @Test
    void safeEquals_不同返回false() {
        assertFalse(ChecksumUtil.safeEquals(ChecksumUtil.sha256("A"), ChecksumUtil.sha256("B")));
    }

    @Test
    void safeEquals_空值处理() {
        assertTrue(ChecksumUtil.safeEquals(null, null), "双 null 视为相等");
        assertFalse(ChecksumUtil.safeEquals(null, "x"));
        assertFalse(ChecksumUtil.safeEquals("x", null));
    }
}
