// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预制发票（蓝图开票链路核心）：客户规则拆分合并（附件一）→商务确认（参数开关）→
 * 推送金税（GOLDEN_TAX 接口配置）→回写→打印→邮寄；红字/换票/作废重开/提前开票核对。
 */
@Data
@TableName("ar_pre_invoice")
public class ArPreInvoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 预制发票号=对账单号+三位流水（蓝图 R-31） */
    private String preInvoiceNo;
    private Long companyId;
    private String reconBillNo;
    private String customerCode;
    private String customerName;
    /** 专票/普票 */
    private String invoiceType;
    private String ccy;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Integer lineCount;
    private String linesJson;
    /** PO/DELIVERY/LINE_GROUP/SINGLE_LINE */
    private String splitDim;
    /** TO_INVOICE/BIZ_CONFIRMING/PUSHED/INVOICED/VOIDED/RED_INVOICED */
    private String status;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private String redNoticeNo;
    /** 换票原票 */
    private String originNo;
    private Integer printedFlag;
    /** 1=ERP负数发票已推送待补红字通知单号（AR接口4） */
    private Integer redPending;
    /** 提前开票核对 NONE/PENDING/DONE */
    private String preCheckFlag;
    private String preCheckNote;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_TO_INVOICE = "TO_INVOICE";
    public static final String ST_BIZ_CONFIRMING = "BIZ_CONFIRMING";
    public static final String ST_PUSHED = "PUSHED";
    public static final String ST_INVOICED = "INVOICED";
    public static final String ST_VOIDED = "VOIDED";
    public static final String ST_RED_INVOICED = "RED_INVOICED";
    /** 已核销（AR蓝图状态机4.1：已开票→核销→已核销，核销工作台回写） */
    public static final String ST_WRITTEN_OFF = "WRITTEN_OFF";
}
