package com.fssc.expense.service;

import com.fssc.expense.entity.ExpBillLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OverseasVoucherRule 单测（V1.3.14 境外凭证合规⑤，19号公告口径）：纯逻辑，无 Spring 上下文。
 * 覆盖 收据/行程单中文说明必填（RECEIPT 无说明报错、FAPIAO/国内不要求）+
 * forceNoDeduct 仅 abroad + 补贴超标额计算（含 2 位小数精度）。
 */
class OverseasVoucherRuleTest {

    private static ExpBillLine line(String voucherForm, String cnDesc) {
        ExpBillLine l = new ExpBillLine();
        l.setVoucherForm(voucherForm);
        l.setCnDesc(cnDesc);
        return l;
    }

    // ---------- requireCnDesc ----------

    @Test
    void 境外收据无中文说明返回错误() {
        assertNotNull(OverseasVoucherRule.requireCnDesc(true, line("RECEIPT", null)));
        assertNotNull(OverseasVoucherRule.requireCnDesc(true, line("RECEIPT", "  ")));
        assertNotNull(OverseasVoucherRule.requireCnDesc(true, line("ITINERARY", "")));
    }

    @Test
    void 境外收据填了中文说明通过() {
        assertNull(OverseasVoucherRule.requireCnDesc(true, line("RECEIPT", "东京出租车费，2026-03-01，新宿至丸之内，张三")));
        assertNull(OverseasVoucherRule.requireCnDesc(true, line("receipt", "x"))); // 大小写不敏感
    }

    @Test
    void FAPIAO与国内不要求中文说明() {
        assertNull(OverseasVoucherRule.requireCnDesc(true, line("FAPIAO", null)));
        assertNull(OverseasVoucherRule.requireCnDesc(true, line(null, null))); // 空形态=默认境内发票
        assertNull(OverseasVoucherRule.requireCnDesc(false, line("RECEIPT", null))); // 国内零变化
        assertNull(OverseasVoucherRule.requireCnDesc(false, line("ITINERARY", null)));
        assertNull(OverseasVoucherRule.requireCnDesc(true, null));
    }

    // ---------- forceNoDeduct ----------

    @Test
    void 仅境外行强制不可抵扣() {
        assertTrue(OverseasVoucherRule.forceNoDeduct(true, line("RECEIPT", "说明")));
        assertTrue(OverseasVoucherRule.forceNoDeduct(true, line("FAPIAO", null))); // abroad 即归零抵扣
        assertFalse(OverseasVoucherRule.forceNoDeduct(false, line("RECEIPT", "说明")));
        assertFalse(OverseasVoucherRule.forceNoDeduct(true, null));
    }

    // ---------- allowanceTaxable ----------

    @Test
    void 超标额等于实际减标准乘天数() {
        // 标准 500/日 × 8 天 = 4000，实际 4500 → 超标 500.00
        assertEquals(new BigDecimal("500.00"),
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4500"), new BigDecimal("500"), new BigDecimal("8")));
    }

    @Test
    void 不超标或临界返回零() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4000"), new BigDecimal("500"), new BigDecimal("8")))); // 恰等于标准
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("3000"), new BigDecimal("500"), new BigDecimal("8")))); // 低于标准
    }

    @Test
    void 精度两位四舍五入() {
        // 233.3333 × 0.5 = 116.666650 → 标准 116.67；实际 116.675 → 超标 0.005 → 0.01（HALF_UP）
        assertEquals(new BigDecimal("0.01"),
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("116.675"), new BigDecimal("233.3333"), new BigDecimal("0.5")));
        // 350 × 3.5 = 1225.00；实际 1300.5 → 超标 75.50
        assertEquals(new BigDecimal("75.50"),
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("1300.5"), new BigDecimal("350"), new BigDecimal("3.5")));
    }

    @Test
    void 空值或非正参数返回零不报错() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(null, new BigDecimal("500"), new BigDecimal("8"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4500"), null, new BigDecimal("8")))); // 无补贴标准→不判超标
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4500"), new BigDecimal("500"), null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4500"), BigDecimal.ZERO, new BigDecimal("8"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OverseasVoucherRule.allowanceTaxable(new BigDecimal("4500"), new BigDecimal("500"), BigDecimal.ZERO)));
    }

    // ---------- isReceiptForm ----------

    @Test
    void 收据与行程单为境外凭证形态() {
        assertTrue(OverseasVoucherRule.isReceiptForm("RECEIPT"));
        assertTrue(OverseasVoucherRule.isReceiptForm("itinerary"));
        assertFalse(OverseasVoucherRule.isReceiptForm("FAPIAO"));
        assertFalse(OverseasVoucherRule.isReceiptForm(null));
    }
}
