// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 借款冲抵（exp_loan_offset，带 tenant_id）。无 update_by/update_time，独立实体。 */
@Data
@TableName("exp_loan_offset")
public class ExpLoanOffset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    /** 借款单ID（LOAN 类型 exp_bill） */
    private Long loanBillId;
    private String loanBillNo;
    /** 冲抵金额 */
    private BigDecimal offsetAmount;
    /** CLAIM报销冲抵/REPAY还款冲抵 */
    private String offsetType;
    private LocalDate offsetDate;
    private String remark;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
