// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商对账单逐笔明细（supplier_recon_detail）。
 * 对标金蝶K/3 Cloud应付管理的逐笔勾对 + SAP F.13自动清账。
 * 每条明细代表一笔发票(INVOICE)、付款(PAYMENT)或手工调整(ADJUSTMENT)。
 * 通过 matched_detail_id + match_status 实现逐笔勾对关系。
 */
@Data
@TableName("supplier_recon_detail")
public class SupplierReconDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 所属对账单ID */
    private Long reconId;
    /** 明细类型：INVOICE 发票 / PAYMENT 付款 / ADJUSTMENT 手工调整 */
    private String itemType;
    /** 原单ID（发票ID/付款ID） */
    private Long itemId;
    /** 原单编号（发票号/付款流水号） */
    private String itemNo;
    /** 原单日期 */
    private LocalDate itemDate;
    /** 金额 */
    private BigDecimal amount;
    /** 勾对对手明细ID（NULL=未勾对） */
    private Long matchedDetailId;
    /** 勾对状态：UNMATCHED 未勾对 / MATCHED 已勾对 / MANUAL 手工勾对 */
    private String matchStatus;
    /** 勾对方式：AUTO 自动 / MANUAL 手工 */
    private String matchType;
    /** 匹配规则：INVOICE_NO 按发票号 / AMOUNT 按金额 / PO_NO 按PO号 / MANUAL 手工 */
    private String matchRule;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
