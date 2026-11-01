// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 存货移动单（stock_move，带 tenant_id）。
 * 逐笔出入库/盘点/处置交易记录。
 */
@Data
@TableName("stock_move")
public class StockMove implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String moveNo;
    /** RECEIVE入库/ISSUE领用出库/SCRAP报废/DISPOSAL处置/INTERNAL_SALE内部销售/CHECK盘点/DEVAL跌价 */
    private String moveType;
    private String itemCode;
    private String itemName;
    private Long warehouseId;
    private Long companyId;
    private BigDecimal qty;
    private BigDecimal unitCost;
    private BigDecimal amount;
    /** 来源单据类型(PO_RECEIPT/EXPENSE/INVENTORY) */
    private String sourceDocType;
    private Long sourceDocId;
    /** 关联流程KEY */
    private String flowKey;
    private String procInstId;
    /** DRAFT/PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;
    private String remark;
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
