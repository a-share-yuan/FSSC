// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 存货收发存（md_inventory，带 tenant_id，FA 投资到资产-存货）。
 * priceMethod 计价方式：MA 移动平均。
 * 结存 remainingNum/remainingAmount 由出入库动态维护。
 */
@Data
@TableName("md_inventory")
public class MdInventory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 物料编码 */
    private String itemCode;
    /** 物料名称 */
    private String itemName;
    /** 规格 */
    private String spec;
    /** 计量单位 */
    private String unit;
    /** 仓库 */
    private Long warehouseId;
    /** 期初数量 */
    private BigDecimal openingNum;
    /** 期初金额 */
    private BigDecimal openingAmount;
    /** 入库数量 */
    private BigDecimal inNum;
    /** 入库金额 */
    private BigDecimal inAmount;
    /** 出库数量 */
    private BigDecimal outNum;
    /** 出库金额（发出成本） */
    private BigDecimal outAmount;
    /** 结存数量 */
    private BigDecimal remainingNum;
    /** 结存金额 */
    private BigDecimal remainingAmount;
    /** 计价方式：MA 移动平均 */
    private String priceMethod;
    /** 所属公司（多分子公司审批路由 V1.3.17） */
    private Long companyId;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}