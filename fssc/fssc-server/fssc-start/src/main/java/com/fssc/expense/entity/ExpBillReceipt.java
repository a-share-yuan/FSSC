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
import java.time.LocalDateTime;

/**
 * 单据收款事实（exp_bill_receipt，带 tenant_id）＝资金流入侧载体，镜像 {@link ExpBillPayment} 的反方向。
 * 还款 80,000 的现金流入原先零载体，追溯链「收款」环节无从读数。
 * <p>{@code createBy/updateBy} 为裸字段（无 fill 注解）⇒ 写入方必须显式 {@code setCreateBy(...)}，否则落 DB NULL。
 */
@Data
@TableName("exp_bill_receipt")
public class ExpBillReceipt implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    /** 来源单据的审批流程实例ID（列型 varchar(64)，与 exp_bill_payment 一致） */
    private String procInstId;
    private Integer lineNo;
    /** EMPLOYEE员工/CUSTOMER往来单位/OTHER */
    private String payerType;
    private Long payerId;
    private String payerName;
    private String bankName;
    private String bankAccount;
    /** BANK/现金/票据，取值域沿用单据侧收款方式规则 */
    private String receiveMethod;
    /** 公司收款账户（md_bank_account） */
    private Long receiveAccountId;
    private String currencyCode;
    private BigDecimal amount;
    /** RECEIVED/RECEIVING */
    private String receiptStatus;
    private LocalDateTime receiptTime;
    private String receiptSerialNo;
    private String remark;
    private Long createBy;
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
