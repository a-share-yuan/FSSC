// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 采购订单行项目（po_order_line，带 tenant_id）：金额与表头同一规则，均为不含税。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("po_order_line")
public class PoOrderLine extends TenantAuditBase {

    private Long poId;
    /** 行号（前端表格序号，从 1 起） */
    private Integer lineNo;
    /** 物料编码（md_item.item_code，可手录） */
    private String materialCode;
    private String materialName;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    /** 行金额（不含税，= 数量 × 单价） */
    private BigDecimal amount;
    private String remark;
}
