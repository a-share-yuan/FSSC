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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应收票据登记（蓝图票据管理 9.5，流程18/19/20，状态机4.4）：
 * 电子票据（ECDS接收）/纸质票据（影像扫描+移交出纳保管）；审单入账→背书转让/汇票贴现/到期托收。
 * 贴现款与贴现利息单独记账（附录11.3.2）。
 */
@Data
@TableName("ar_note")
public class ArNote implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 票据编号 */
    private String noteNo;
    /** ELECTRONIC电子票据/PAPER纸质票据 */
    private String noteType;
    private String drawer;
    private String acceptor;
    private String customerCode;
    private String customerName;
    private String ccy;
    private BigDecimal amount;
    private LocalDate issueDate;
    private LocalDate dueDate;
    /** 影像扫描引用（纸质票据必填） */
    private String imageRef;
    /** 被背书人（背书转让） */
    private String transferee;
    /** 贴现利率%（年） */
    private BigDecimal discountRate;
    private LocalDate discountDate;
    /** 贴现天数（贴现日→到期日） */
    private Integer discountDays;
    private BigDecimal discountInterest;
    private BigDecimal discountAmount;
    private String voucherNo;
    private String discountVoucherNo;
    private String collectVoucherNo;
    /** REGISTERED/IN_ACCOUNT/CUSTODY/ENDORSED/DISCOUNTED/COLLECTING/COMPLETED/VOIDED */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String T_ELECTRONIC = "ELECTRONIC";
    public static final String T_PAPER = "PAPER";

    public static final String ST_REGISTERED = "REGISTERED";
    public static final String ST_IN_ACCOUNT = "IN_ACCOUNT";
    public static final String ST_CUSTODY = "CUSTODY";
    public static final String ST_ENDORSED = "ENDORSED";
    public static final String ST_DISCOUNTED = "DISCOUNTED";
    public static final String ST_COLLECTING = "COLLECTING";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_VOIDED = "VOIDED";
}
