// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OcrFieldMapper 单测：纯函数，无 Spring 上下文。
 * 覆盖默认 mapping 解析（空/非法回退）、字段路径提取、缺字段容错、金额/日期归一。
 */
class OcrFieldMapperTest {

    @Test
    void 默认映射解析_空与非法JSON均回退() {
        Map<String, String> def = OcrFieldMapper.parseMapping(null);
        assertEquals("words_result.InvoiceNum.word", def.get("invoiceNo"));
        assertEquals("words_result.TotalTax.word", def.get("amount"));
        assertEquals(def, OcrFieldMapper.parseMapping(""));
        assertEquals(def, OcrFieldMapper.parseMapping("{ not-json"));
        assertEquals(def, OcrFieldMapper.parseMapping("{}"));
    }

    @Test
    void 自定义映射按字段覆盖_非法键忽略() {
        Map<String, String> m = OcrFieldMapper.parseMapping(
                "{\"invoiceNo\":\"data.no\",\"amount\":\"data.total\",\"unknownField\":\"x.y\",\"taxAmount\":123}");
        assertEquals(2, m.size());
        assertEquals("data.no", m.get("invoiceNo"));
        assertEquals("data.total", m.get("amount"));
        assertFalse(m.containsKey("unknownField"));
        assertFalse(m.containsKey("taxAmount")); // 非字符串路径忽略，其余字段也不回退默认（按需最小映射）
    }

    @Test
    void 默认映射提取百度式响应_全字段命中() {
        String resp = "{\"words_result\":{"
                + "\"InvoiceCode\":{\"word\":\"044001900111\"},"
                + "\"InvoiceNum\":{\"word\":\"12345678\"},"
                + "\"InvoiceType\":{\"word\":\"增值税专用发票\"},"
                + "\"InvoiceDate\":{\"word\":\"2026年08月10日\"},"
                + "\"TotalTax\":{\"word\":\"¥1,130.00\"},"
                + "\"TaxAmount\":{\"word\":\"130.00\"},"
                + "\"AmountInFiguers\":{\"word\":\"1000.00\"},"
                + "\"SellerName\":{\"word\":\"某某科技有限公司\"},"
                + "\"SellerPayerIdentificationNumber\":{\"word\":\"91440300MA5XXXXX\"},"
                + "\"PurchaserName\":{\"word\":\"买方公司\"},"
                + "\"PurchaserPayerIdentificationNumber\":{\"word\":\"91310000MA1XXXXX\"}}}";
        OcrResult r = OcrFieldMapper.map(null, resp);
        assertTrue(r.isOk());
        assertEquals("12345678", r.getInvoiceNo());
        assertEquals("044001900111", r.getInvoiceCode());
        assertEquals("2026-08-10", r.getInvoiceDate()); // 中文日期归一 yyyy-MM-dd
        assertEquals(new BigDecimal("1130.00"), r.getAmount()); // 金额容错 ¥/千分位
        assertEquals(new BigDecimal("130.00"), r.getTaxAmount());
        assertEquals(new BigDecimal("1000.00"), r.getNetAmount());
        assertEquals("某某科技有限公司", r.getSellerName());
        assertEquals("买方公司", r.getBuyerName());
        assertEquals("91310000MA1XXXXX", r.getBuyerTaxNo());
    }

    @Test
    void 缺字段容错_路径不存在字段留空不抛穿() {
        OcrResult r = OcrFieldMapper.map(null, "{\"words_result\":{\"InvoiceNum\":{\"word\":\"8888\"}}}");
        assertTrue(r.isOk());
        assertEquals("8888", r.getInvoiceNo());
        assertNull(r.getAmount());
        assertNull(r.getSellerName());
        assertNull(r.getInvoiceDate());
        // 全空对象：所有字段 null，仍视为拿到响应（是否入池由 #15 自洽校验裁断）
        assertTrue(OcrFieldMapper.map(null, "{}").isOk());
    }

    @Test
    void 自定义映射生效与坏响应返回失败() {
        OcrResult r = OcrFieldMapper.map("{\"invoiceNo\":\"data.header.no\",\"amount\":\"data.sum\"}",
                "{\"data\":{\"header\":{\"no\":\"A1\"},\"sum\":\"2.05\"}}");
        assertEquals("A1", r.getInvoiceNo());
        assertEquals(new BigDecimal("2.05"), r.getAmount());
        assertNull(r.getInvoiceCode()); // 未在映射内不取默认
        // 非 JSON / 空响应 → ok=false 不抛穿
        assertFalse(OcrFieldMapper.map(null, "not a json").isOk());
        assertFalse(OcrFieldMapper.map(null, "").isOk());
    }

    @Test
    void 日期与金额单点归一() {
        assertEquals("2026-08-10", OcrFieldMapper.normalizeDate("2026/8/10"));
        assertEquals("2026-08-10", OcrFieldMapper.normalizeDate("20260810"));
        assertEquals("2026-08-10", OcrFieldMapper.normalizeDate("2026年8月10日"));
        assertEquals("无效日期", OcrFieldMapper.normalizeDate("无效日期")); // 无法识别保留原文
        assertEquals(new BigDecimal("1234.56"), OcrFieldMapper.toDecimal(" ￥1,234.56 "));
        assertNull(OcrFieldMapper.toDecimal("abc"));
    }
}
