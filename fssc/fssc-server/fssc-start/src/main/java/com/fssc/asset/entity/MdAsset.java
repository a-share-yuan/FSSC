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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产卡片（md_asset，带 tenant_id，FA 投资到资产-固定资产）。
 * 折旧方法 depreciationMethod：LINEAR 直线 / ACCELERATED 加速。
 * 状态 status：INIT 在库 / USING 在用 / SCRAP 报废。
 */
@Data
@TableName("md_asset")
public class MdAsset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 资产编号 */
    private String assetNo;
    /** 资产名称 */
    private String assetName;
    /** 资产类别（md_asset_category） */
    private Long categoryId;
    /** 使用部门 */
    private Long deptId;
    /** 所属公司 */
    private Long companyId;
    /** 购置日期 */
    private LocalDate purchaseDate;
    /** 原值 */
    private BigDecimal originalAmount;
    /** 账面净值（原值 - 累计折旧） */
    private BigDecimal netAmount;
    /** 残值 */
    private BigDecimal residualValue;
    /** 折旧方法：LINEAR 直线 / ACCELERATED 加速 */
    private String depreciationMethod;
    /** 折旧月数（会计口径） */
    private Integer depreciationMonths;
    /** 税法折旧年限(月)：与会计年限分离，支撑税会差异(A105080)（#16） */
    private Integer taxLifeMonths;
    /** 月折旧额 */
    private BigDecimal monthlyDepr;
    /** 累计折旧 */
    private BigDecimal accumDepr;
    /** INIT 在库 / USING 在用 / SCRAP 报废 */
    private String status;
    /** 供应商 */
    private Long supplierId;
    /** 发票号 */
    private String invoiceNo;
    /** 最后折旧日期（月度批处理幂等用）：NULL 表示从未计提 */
    private LocalDate lastDeprDate;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}