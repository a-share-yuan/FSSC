// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OCR 识别结果落库记录（ocr_record）。
 * 每次 OCR 识别成功后将结构化票面字段持久化，不再仅存于 img_file.ocr_text JSON blob。
 */
@Data
@TableName("ocr_record")
public class OcrRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long batchId;
    private Long imgFileId;
    private Long fileId;

    private String invoiceCode;
    private String invoiceNo;
    private String invoiceType;
    private String invoiceDate;

    private BigDecimal netAmount;
    private BigDecimal taxAmount;
    private BigDecimal amount;

    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;

    private String rawText;
    private String channelName;
    /** 票面金额自洽 1=是 0=否 */
    private Integer consistent;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
