// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** EBS 财务调整数据推送（AR 接口2/7.2接口6）：调整后推送共享并自动重核关联对账单。 */
@Data
@TableName("ar_ebs_adjust")
public class ArEbsAdjust implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** EBS 调整单号（幂等键） */
    private String adjustNo;
    private String customerCode;
    /** 折扣/索赔/溢交 */
    private String adjustType;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String ebsVoucherNo;
    /** 关联对账单号（推送后自动重新核对） */
    private String statementNo;
    /** RECEIVED/APPLIED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public static final String ST_RECEIVED = "RECEIVED";
    public static final String ST_APPLIED = "APPLIED";
}
