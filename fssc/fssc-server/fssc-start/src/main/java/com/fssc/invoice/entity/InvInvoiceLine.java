// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票明细行（inv_invoice_line，带 tenant_id）。
 * 用于费用行与发票行的勾稽。无 del_flag/create_by，按真实列独立声明。
 */
@Data
@TableName("inv_invoice_line")
public class InvInvoiceLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long invoiceId;
    private Integer lineNo;
    /** 货物或应税劳务名称 */
    private String goodsName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    /** 自动匹配的费用项目ID */
    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
