// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售退货单明细行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_return_line")
public class SaleReturnLine extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 头表 id（sale_return.id） */
    private Long returnId;
    private Integer lineNo;
    private String itemCode;
    private String itemName;
    private BigDecimal qty;
    private String uom;
    private BigDecimal price;
    private BigDecimal taxRate;
    private BigDecimal amount;
    /** 源单行 id（二期防超退严格校验用） */
    private Long sourceLineId;
    private String remark;
}
