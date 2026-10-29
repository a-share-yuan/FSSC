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

/** 单据付款记录（exp_bill_payment，带 tenant_id）。WAITING → PAID/FAILED。无 create_by，独立实体。 */
@Data
@TableName("exp_bill_payment")
public class ExpBillPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    /** 来源单据的审批流程实例ID，生成付款记录时随单写入 */
    private String procInstId;
    private Integer lineNo;
    /** EMPLOYEE员工/SUPPLIER供应商/OTHER */
    private String payeeType;
    private Long payeeId;
    private String payeeName;
    private String bankName;
    private String bankAccount;
    private String payMethod;
    private Long payAccountId;
    private String currencyCode;
    private BigDecimal amount;
    /** WAITING/PAID/FAILED */
    private String payStatus;
    private LocalDateTime payTime;
    private String payBatchNo;
    private String paySerialNo;
    private String failReason;
    /** 关联往来单位（crm_customer） */
    private Long customerId;
    /** 关联合同（cont_contract） */
    private Long contractId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
