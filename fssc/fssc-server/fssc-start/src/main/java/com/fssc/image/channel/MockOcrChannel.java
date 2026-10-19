// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 模拟 OCR 通道（默认实现）。由原 MockOcrProvider 收编而来：基于文件名（fileId）生成
 * 确定性发票数据——不含税 = id * 10，税率 13%（价税必然自洽，便于演示与断言），对外行为零变化。
 * <p>通道选择在运行时：{@link OcrChannelRouter} 优先读 sys_config ocr.channel（管理员界面可改，
 * 即时生效），为空则回退配置项 fssc.ocr.channel（默认 mock）。本类恒为 bean。
 */
@Slf4j
@Component
public class MockOcrChannel implements OcrChannel {

    @Override
    public OcrResult recognize(byte[] image, String fileName) {
        long id = parseId(fileName);
        BigDecimal net = BigDecimal.valueOf(id * 10).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = net.add(tax);

        OcrResult r = new OcrResult();
        r.setInvoiceNo("MOCK" + String.format("%08d", id));
        r.setInvoiceCode("04400190" + String.format("%04d", id % 10000));
        r.setInvoiceType("VAT");
        r.setSellerName("模拟供应商" + (id % 100));
        r.setSellerTaxNo("91310000MOCK" + id);
        r.setInvoiceDate("2026-08-10");
        r.setNetAmount(net);
        r.setTaxAmount(tax);
        r.setAmount(amount);
        r.setRawText("[MOCK] 模拟OCR识别结果 invoiceNo=" + r.getInvoiceNo() + " 价税合计=" + amount);
        return r;
    }

    private long parseId(String fileName) {
        try {
            return Long.parseLong(String.valueOf(fileName));
        } catch (NumberFormatException e) {
            return Math.abs(fileName.hashCode()) % 1000 + 1;
        }
    }
}
