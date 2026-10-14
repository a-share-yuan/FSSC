// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 收款核销明细（rc_receipt_line，带 tenant_id，逻辑删除 del_flag）。
 * 一笔收款单可按多个应收单分别核销，每行记录【应收单 + 本次核销金额】。
 * 用于替代原 rc_receipt.receivable_ids 逗号串，实现逐应收单强勾稽（核销金额 ≤ 应收余额）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rc_receipt_line")
public class RcReceiptLine extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 收款核销单（rc_receipt.id） */
    private Long receiptId;
    /** 应收单（rc_receivable.id） */
    private Long receivableId;
    /** 应收单号（冗余，便于展示） */
    private String receivableNo;
    /** 本次核销金额 */
    private BigDecimal offsetAmount;
    /** WRITTEN_OFF 已核销 */
    private String status;
}