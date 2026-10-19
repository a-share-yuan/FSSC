// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 发票（inv_invoice，带 tenant_id）。OCR/录入的发票，价税分离。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_invoice")
public class InvInvoice extends TenantAuditBase {

    /** 发票代码；全电票无代码，恒为空串（NOT NULL DEFAULT ''，与 inv_invoice_global/uk_tenant_inv 口径一致） */
    private String invoiceCode;
    private String invoiceNo;
    /** VAT增值税专票 / VATN普通 / TRAIN火车票 / OTHER */
    private String invoiceType;
    /** 发票种类 SPECIAL专票/NORMAL普票/ELECTRONIC电子 */
    private String invoiceKind;
    /** 发票性质 BLUE蓝票/RED红票 */
    private String invoiceNature;
    private String invoiceTitle;
    private String taxNo;
    private String sellerName;
    private String sellerTaxNo;
    /** 购方抬头（企业名称）/ 购方税号（企业统一社会信用代码）：票账听从一致性核验 */
    private String buyerName;
    private String buyerTaxNo;
    /** 占用锁定：NONE 未占用 / LOCKED_IN_USE 已占用(入账锁定，防二次占用/红冲) */
    private String lockStatus;
    /** 异常标记：NORMAL 正常 / RED_FLUSH 红冲 / SELLER_BLACK 销方黑名单 / INVALID 已作废 */
    private String anomalyFlag;
    private LocalDate invoiceDate;
    /** 价税合计 */
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private String currencyCode;
    private String checkCode;
    private String machineNo;
    /** NORMAL/DISABLED */
    private String status;
    /** 是否已报销占用：0否/1是（#12 由 bindToBill 维护，与 used_count 同步） */
    private Integer isUsed;
    /** 被单据引用次数（#12 应恒等于 Σinv_bill_invoice 记录数，供对账一致性校验） */
    private Integer usedCount;
    /** NONE/PASS/REJECT */
    private String verifyStatus;
    private LocalDateTime verifyTime;
    private String verifyMsg;
    /** 可抵扣税额（进项专票=taxAmount，普票/转出=0） */
    private BigDecimal deductAmount;
    /** 认证状态：NOT_CERTIFIED未勾选/CERTIFIED已勾选/REVERSED进项转出 */
    private String certifyStatus;
    /** 勾选认证所属期 yyyyMM */
    private String certifyPeriod;
    /** 勾选认证时间 */
    private LocalDateTime certifyDate;
    /** 进项转出时间 */
    private LocalDateTime reverseDate;
    /** 发票影像文件ID（sys_file.id，供前端预览发票影像） */
    private Long imgFileId;
    /** 所属公司（多分子公司审批路由 V1.3.17） */
    private Long companyId;
    /** P0-4 溯源：来源单据类型 BILL单据/IMG_FILE影像/MANUAL手工 */
    private String sourceDocType;
    /** P0-4 溯源：来源单据ID */
    private Long sourceDocId;
    /** uxfix W1（2026-10-08）：方向 INPUT进项(收票)/OUTPUT销项(开票)。DDL 见 临时脚本/backup_uxfix_20261008/w1_ddl.tmp.sql */
    private String direction;
    /** 备注/留痕（作废原因、红冲原因，） */
    private String remark;
    /** 红字发票关联的原蓝票ID（inv_invoice.original_id，列见 sql/impl-cl-20260914.sql DDL） */
    private Long originalId;
}
