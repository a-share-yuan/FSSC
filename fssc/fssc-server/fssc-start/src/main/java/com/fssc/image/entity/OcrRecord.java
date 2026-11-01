// SPDX-License-Identifier: AGPL-3.0-or-later
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
 * OCR 识别结果写入数据库记录（ocr_record）。
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

    /**
     * 识别结果：SUCCESS 成功／FAILED 失败。
     * <p>本表不只承载"识别成功"的结构化票面：失败环节若只写 log 与异常池 ⇒
     * OCR 记录页对一次失败的识别恒 0 行，
     * 运营无从复核"到底识别过没有、为什么没识别出来"。现失败也落一行。
     * <p>需先执行 V1.4.96_ocr_record_status.sql（列默认 'SUCCESS'，历史行即成功行）。
     */
    private String recognizeStatus;

    /** 识别失败原因（业务话术；网关 JSON/环境变量名/traceId 等技术详情仍只进运维日志，②） */
    private String failReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
