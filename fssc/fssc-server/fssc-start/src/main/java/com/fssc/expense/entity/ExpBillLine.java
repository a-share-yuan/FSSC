// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 报销单明细（exp_bill_line，带 tenant_id） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_bill_line")
public class ExpBillLine extends TenantAuditBase {

    private Long billId;
    private Integer lineNo;
    /** 费用项ID（md_expense_item） */
    private Long itemId;
    private String itemName;
    private LocalDate expenseDate;
    private Long deptId;
    private Long costCenterId;
    private Long projectId;
    private Long subjectId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    /** 原币金额 */
    private BigDecimal origAmount;
    /** 行录入口径：TAX_IN含税录入/TAX_EX不含税录入（#9；缺省继承表头 exp_bill.price_type） */
    private String priceType;
    /** 本位币金额 */
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal deductAmount;
    /** 凭证形态（V1.3.14⑤境外凭证合规）：FAPIAO境内发票/RECEIPT境外收据/ITINERARY行程单 */
    private String voucherForm;
    /** 境外凭证中文说明（19号公告：载明行程/地点/费用/人名，税前扣除要件；RECEIPT/ITINERARY 必填） */
    private String cnDesc;
    private BigDecimal approvedAmount;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    /** 人数（差旅/招待） */
    private Integer personCount;
    private String attendees;
    /** 标准金额（费用标准校验） */
    private BigDecimal stdAmount;
    /** 1 超标准 */
    private Integer overStd;
    private String overReason;
    /** 招待费类型（BUSINESS 商务宴请/WORK 工作宴请，D-DEC-373） */
    private String entertainType;
    /** 预算明细ID（bg_item，占用/核销） */
    private Long budgetItemId;
    private String remark;
    private String extJson;
}
