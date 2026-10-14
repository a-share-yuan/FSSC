// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.service;

import com.fssc.invoice.entity.InvBillInvoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InvoiceService.alreadyBound 单测（D-V4R-30 判据面）：
 * bindToBill 每次调用只会 INSERT 一行勾稽，所以"同一单据重复关联同一张发票"必须判重，
 * 否则票面会被同一张单吃穿（实测读数 72,000＋40,000＝112,000／50,000＋50,000＝100,000），
 * 后果是超付闸永久挡死该单付款，而反核销 inv:unbind 在授权面上未授予任何角色 ⇒ 系统内不可逆。
 */
@DisplayName("InvoiceService 关联判重（D-V4R-30）")
class InvoiceServiceBindDedupeTest {

    private static InvBillInvoice link(Long billId, Long lineId) {
        InvBillInvoice row = new InvBillInvoice();
        row.setBillId(billId);
        row.setLineId(lineId);
        return row;
    }

    @Test
    @DisplayName("整单关联（lineId 皆空）重复提交 ⇒ 判重")
    void 整单重复关联被识别() {
        List<InvBillInvoice> links = Collections.singletonList(link(2L, null));
        assertTrue(InvoiceService.alreadyBound(links, 2L, null));
    }

    @Test
    @DisplayName("同行号重复提交 ⇒ 判重")
    void 同行重复关联被识别() {
        List<InvBillInvoice> links = Collections.singletonList(link(2L, 7L));
        assertTrue(InvoiceService.alreadyBound(links, 2L, 7L));
    }

    @Test
    @DisplayName("同一单据的不同行号 ⇒ 不判重（分批关联是合法动作）")
    void 同单不同行不算重复() {
        List<InvBillInvoice> links = Collections.singletonList(link(2L, 7L));
        assertFalse(InvoiceService.alreadyBound(links, 2L, 8L));
    }

    @Test
    @DisplayName("别的单据占着这张票 ⇒ 不判重，交给面额累计闸去管")
    void 他单占用不算本单重复() {
        List<InvBillInvoice> links = Arrays.asList(link(9L, null), link(9L, 3L));
        assertFalse(InvoiceService.alreadyBound(links, 2L, null));
    }

    @Test
    @DisplayName("既有行带行号而本次整单（或反之）⇒ 不判重")
    void 行号空与非空不相撞() {
        assertFalse(InvoiceService.alreadyBound(Collections.singletonList(link(2L, 7L)), 2L, null));
        assertFalse(InvoiceService.alreadyBound(Collections.singletonList(link(2L, null)), 2L, 7L));
    }

    @Test
    @DisplayName("空集合／缺单据号 ⇒ 不判重（防御侧，不误伤）")
    void 空输入不判重() {
        assertFalse(InvoiceService.alreadyBound(null, 2L, null));
        assertFalse(InvoiceService.alreadyBound(Collections.emptyList(), 2L, null));
        assertFalse(InvoiceService.alreadyBound(Collections.singletonList(link(2L, null)), null, null));
    }
}
