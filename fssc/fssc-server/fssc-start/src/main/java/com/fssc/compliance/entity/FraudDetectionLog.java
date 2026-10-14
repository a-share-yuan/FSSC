// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 反欺诈检测日志（fraud_detection_log）。反欺诈检测结果与审核记录。 */
@Data
@TableName("fraud_detection_log")
public class FraudDetectionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long employeeId;

    private Long billId;

    /** FREQUENCY/AMOUNT/GEOGRAPHY/VENDOR/TIME */
    private String detectionType;

    /** NORMAL/SUSPICIOUS/HIGH_RISK */
    private String detectionResult;

    private BigDecimal riskScore;

    private String details;

    /** ALLOW/WARN/BLOCK/REVIEW */
    private String actionTaken;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}