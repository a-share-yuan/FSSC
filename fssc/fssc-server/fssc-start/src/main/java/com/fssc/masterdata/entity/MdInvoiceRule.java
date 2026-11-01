// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发票智能匹配规则（md_invoice_rule，带 tenant_id）。
 * 报销发起时按发票「货物或应税劳务、服务名称」命中费用类型自动预填。
 * keyword 支持用「/」分隔多个词；matchType=EXACT 精确 / CONTAINS 包含（按 sort 升序优先）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_invoice_rule")
public class MdInvoiceRule extends TenantAuditBase {

    /** 商品/服务名称关键词或词串（多个用 / 分隔） */
    private String keyword;
    /** 匹配方式：CONTAINS 包含（默认）/ EXACT 精确 */
    private String matchType;
    /** 命中后预填的费用类型 id（md_expense_item.id） */
    private Long expenseItemId;
    /** 费用类型名称（冗余，便于列表与前端展示） */
    private String itemName;
    /** 排序：越小越优先，同精度内生效 */
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}