// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OCR 识别结果入发票池的跨域传输对象（影像域 → 主域）。
 * <p>
 * 为什么要独立 DTO 而不是直接传 {@code InvInvoice} 实体：服务拆分后两个进程不再共享
 * 实体类，契约必须是纯数据。字段取自 OcrService#toInvoice 的实际赋值集合，保持一一对应。
 */
@Data
public class InvoicePoolDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID（调用方从 TenantContext 取后显式传入，服务端不读请求头） */
    private Long tenantId;
    /** 纳税主体（可空：OCR 场景未识别时交由主域默认主体逻辑处理） */
    private Long companyId;

    private String invoiceNo;
    /** 全电票无发票代码，主域会归一为空串以适配 uk_tenant_inv 查重 */
    private String invoiceCode;
    private String invoiceType;

    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;

    private LocalDate invoiceDate;

    /** 不含税金额 */
    private BigDecimal netAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 价税合计（必须等于 netAmount + taxAmount，主域强校验） */
    private BigDecimal amount;

    /**
     * 缺陷修复（H-0）：OCR 识别来源的影像页ID（img_file.id），
     * 主域入池时回写 inv_invoice.img_file_id，实现「发票池 ↔ 影像」互查。
     */
    private Long imgFileId;
}
