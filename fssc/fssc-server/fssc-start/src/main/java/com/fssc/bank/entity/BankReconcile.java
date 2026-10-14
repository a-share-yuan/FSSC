// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 银企勾对流水（bank_reconcile，只追加；P2-1） */
@Data
@TableName("bank_reconcile")
public class BankReconcile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long statementId;
    private Long paymentId;
    private String paymentNo;
    private String serialNo;
    private BigDecimal amount;
    private String accountNo;
    /** MATCHED 自动勾对 / MANUAL 人工处理 */
    private String matchStatus;
    private Long matchBy;
    private LocalDateTime matchTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** P0-4 溯源：来源单据类型 BANK_STATEMENT银行流水 */
    private String sourceDocType;
    /** P0-4 溯源：来源单据ID（bank_statement.id） */
    private Long sourceDocId;
}
