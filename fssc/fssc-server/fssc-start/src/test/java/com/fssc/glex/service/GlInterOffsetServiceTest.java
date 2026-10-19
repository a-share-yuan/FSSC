package com.fssc.glex.service;

import com.fssc.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GlInterOffsetService.assertPairEqual 单测：内部抵消来账/往账成对相等铁律。
 * 纯静态方法，无 Spring 上下文、无 mock 依赖。
 */
class GlInterOffsetServiceTest {

    @Test
    void 金额相等通过() {
        assertDoesNotThrow(() ->
                GlInterOffsetService.assertPairEqual(new BigDecimal("100"), new BigDecimal("100")));
    }

    @Test
    void 金额按数值比较忽略小数位() {
        // 100 与 100.00 compareTo == 0，应通过
        assertDoesNotThrow(() ->
                GlInterOffsetService.assertPairEqual(new BigDecimal("100"), new BigDecimal("100.00")));
    }

    @Test
    void 金额不等抛异常() {
        assertThrows(BizException.class, () ->
                GlInterOffsetService.assertPairEqual(new BigDecimal("100"), new BigDecimal("99")));
    }

    @Test
    void 来账为空抛异常() {
        assertThrows(BizException.class, () ->
                GlInterOffsetService.assertPairEqual(null, new BigDecimal("100")));
    }

    @Test
    void 往账为空抛异常() {
        assertThrows(BizException.class, () ->
                GlInterOffsetService.assertPairEqual(new BigDecimal("100"), null));
    }
}
