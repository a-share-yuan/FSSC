// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.entity;

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
 * 资金计划（tr_fund_plan）。
 * 计划收/支 vs 实际收/支，命中后状态流转 DRAFT->SELECTED->COMPLETED。
 */
@Data
@TableName("tr_fund_plan")
public class TrFundPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 计划编号 */
    private String planNo;
    /** 计划日期 */
    private LocalDate planDate;
    /** 期间 yyyyMM */
    private String period;
    private Long companyId;
    /** 计划收入 */
    private BigDecimal incomeAmount;
    /** 计划支出 */
    private BigDecimal outcomeAmount;
    /** 实际收入 */
    private BigDecimal incomeActual;
    /** 实际支出 */
    private BigDecimal outcomeActual;
    /** 资金负责人 */
    private Long ownerId;
    /** DRAFT草稿/SELECTED已入选/APPROVED已审批/COMPLETED已完成/PAID已付款/CLOSED已关闭/ARCHIVED已归档 */
    private String status;
    /** 登记人（/420 补列。owner_id＝资金负责人是业务责任人，不是登记人，不可当提交人查询） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}