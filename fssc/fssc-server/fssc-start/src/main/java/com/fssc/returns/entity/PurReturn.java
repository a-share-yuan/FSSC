// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购退货单（AP 退货退款冲减应付）。
 * 采购退料单模型：金额表内存正数（对账规则由 BCP 适配层转负），
 * 状态机 DRAFT→PENDING→APPROVED→VOIDED，退货方式 REFUND（进对账）/EXCHANGE（换货补货，不进对账）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_return")
public class PurReturn extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 采购退货单号（CT 前缀，sys_number_rule 取号） */
    private String billNo;
    private Long companyId;
    private String companyCode;
    private String companyName;
    /** 供应商代码（与 bcp_partner.partner_code 对齐） */
    private String vendorCode;
    private String vendorName;
    private String ccy;
    /** 业务日期（退货日期，对账 receiptDate） */
    private LocalDate billDate;
    /** 含税退货总额（恒正数，对账接口按负数返回） */
    private BigDecimal amount;
    /** 税额合计 */
    private BigDecimal taxAmount;
    /** REFUND 退货退款（进对账）/ EXCHANGE 退货补货（不进对账） */
    private String returnMode;
    /** 源单类型：PO_RECEIPT 采购入库单 */
    private String sourceDocType;
    /** 源单 id（po_receipt.id） */
    private Long sourceDocId;
    /** 源入库单号（对账明细追溯用） */
    private String sourceBillNo;
    /** 退货原因：质量不合格/破损/错发/多发/合同取消/其他 */
    private String returnReason;
    private Integer lineCount;
    /** DRAFT/PENDING/APPROVED/REJECTED/VOIDED */
    private String status;
    private String approver;
    private LocalDateTime approveTime;
    private String voidReason;
    private String remark;

    /** 明细行（仅用于接口传输，不入库） */
    @TableField(exist = false)
    private List<PurReturnLine> lines;

    // 状态常量
    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_VOIDED = "VOIDED";

    // 退货方式常量
    /** 退货退款：冲减应付，进对账 */
    public static final String MODE_REFUND = "REFUND";
    /** 退货补货：不冲减应付，不进对账 */
    public static final String MODE_EXCHANGE = "EXCHANGE";
}
