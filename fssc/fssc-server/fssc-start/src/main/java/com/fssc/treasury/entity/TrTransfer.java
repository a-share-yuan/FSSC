// SPDX-License-Identifier: Apache-2.0
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
 * 资金调拨（tr_transfer）。
 * 银行账户间余额划转：创建草稿 -> 审批 APPROVED -> 执行 PAID（落银行账户余额）。
 */
@Data
@TableName("tr_transfer")
public class TrTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String transferNo;
    /** 调拨日期 */
    private LocalDate transferDate;
    /** 付款方银行账户 */
    private Long fromBankAccountId;
    /** 收款方银行账户 */
    private Long toBankAccountId;
    private Long companyId;
    private BigDecimal amount;
    private String purpose;
    /** DRAFT草稿/APPROVED已审批/PAID已执行 */
    private String status;
    /** 登记人（D-DEC-298/420 补列；此前本表无提交人列 ⇒ deny_self 与自审回避只能回落实例发起人） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---- D-DEC-439 乙：审批/动账/登账三件事的明示位（均为非表列，只出参、不进 SQL、不改状态机） ----

    /** 审批是否已完成（APPROVED 或已执行的 PAID） */
    @TableField(exist = false)
    private Boolean approved;
    /** execute() 是否已跑（status=PAID） */
    @TableField(exist = false)
    private Boolean executed;
    /** 账户余额是否已变动——execute() 是改 md_bank_account.balance 的唯一环节 */
    @TableField(exist = false)
    private Boolean balanceMoved;
    /** 是否已登记现金/银行日记账——本域无日记账登记环节，恒 false */
    @TableField(exist = false)
    private Boolean journalBooked;
    /** 三态码：NOT_APPROVED／APPROVED_NOT_EXECUTED／EXECUTED */
    @TableField(exist = false)
    private String execStateCode;
    /** 三态白话，如「已审批·未执行·账户余额未变」 */
    @TableField(exist = false)
    private String execStateText;
    /** 登账明示，如「未登账：现金/银行日记账未登记」 */
    @TableField(exist = false)
    private String journalText;
    /** 账证侧：已生成的调拨凭证号（无凭证为 null） */
    @TableField(exist = false)
    private String voucherNo;
    /** 账证侧：凭证状态 DRAFT／POSTED */
    @TableField(exist = false)
    private String voucherStatus;
    /** 下一步动作提示（乙案保持人工执行环节，审批通过不自动动账） */
    @TableField(exist = false)
    private String nextActionText;

    /**
     * D-H4-02：执行人真名（非表列）——由 {@code update_by} 批查 sys_user 解析，
     * 与执行时间 {@code update_time} 配对，供列表页外显"执行"两列。
     * <p>执行记录此前只能去 {@code sys_oper_log} 里翻（H4 真跑实测），而 execute() 是
     * {@code tr_transfer} 唯一的 PAID 写环节 ⇒ status=PAID 时 update_by/update_time 就是执行人/执行时刻。
     * 真名规则＝nickName 优先、空则回落 userName（工号），与应付域/退货域同一套。
     */
    @TableField(exist = false)
    private String executorName;
}