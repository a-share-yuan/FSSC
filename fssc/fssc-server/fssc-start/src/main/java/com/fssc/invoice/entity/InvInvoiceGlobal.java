// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票全局查重索引（inv_invoice_global，【跨租户共享表】，不参与租户改写）。
 * <p>
 * 与 {@link InvInvoice}（带 tenant_id）不同：本表按"发票代码+号码"全局唯一，记录首次录入租户，
 * 供跨企业重复报销预警。{@code invoiceCode} 用【空串】而非 NULL 存储全电票发票代码，
 * 修复 {@code inv_invoice} 唯一键在全电票（code=NULL）下不生效的问题（NULL 不参与唯一性比较）。
 * 已在 FsscTenantLineHandler 忽略名单内，故不继承 TenantAuditBase、不带 tenant_id/del_flag。
 */
@Data
@TableName("inv_invoice_global")
public class InvInvoiceGlobal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票代码，全电票为空串 */
    private String invoiceCode;
    /** 发票号码 */
    private String invoiceNo;
    /** 首次录入该票的租户ID */
    private Long firstTenantId;
    /** 该票出现次数（跨企业重复预警依据） */
    private Integer tenantCount;
    /** 发票种类 SPECIAL/NORMAL/ELECTRONIC */
    private String invoiceKind;
    /** 最近一次录入的 inv_invoice.id */
    private Long lastInvoiceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
