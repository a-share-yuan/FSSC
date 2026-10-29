// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行流水/回单（op_bank_statement，带 tenant_id）。
 * 银企直连下载，用于自动对账。无 del_flag，按真实列独立声明。
 * 唯一键 uk_tenant_serial：(tenant_id, bank_account_id, bank_serial)。
 */
@TableName("op_bank_statement")
public class OpBankStatement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long companyId;
    private Long bankAccountId;
    /** 交易时间 */
    private LocalDateTime transDate;
    /** 银行流水号 */
    private String bankSerial;
    private String oppositeName;
    private String oppositeAccount;
    /** DEBIT借/CREDIT贷 */
    private String transType;
    private BigDecimal amount;
    private BigDecimal balance;
    private String summary;
    /** UNRECON未对账/RECONED已对账/DIFF差额 */
    private String reconStatus;
    /** 勾稽到的单据ID */
    private Long reconBillId;
    /**
     * 勾稽单据所属表（recon_bill_type，recon_bill_id 的判别位）：TR_CREDIT_BILL＝tr_credit_bill（资金收款单）／
     * RC_RECEIPT＝rc_receipt（收款核销单），取值与表名逐字同大写。recon_bill_id 是裸 bigint，而两张表各自自增，
     * 同一个数字可以是两张表里的两张单，离开本列指针就没有载体。
     * NULL＝历史行（本列上线前勾的，不回填）或两类之外的单据（exp_bill／rc_receivable 等），
     * 读取侧遇 NULL 一律退回原有"按上下文推断"的规则，不得默认成任一具体表。
     */
    private String reconBillType;
    private LocalDateTime reconTime;

    /** 回单状态：NONE未生成/DOWNLOADED已下载/ARCHIVED已归档 */
    private String slipStatus;
    /** 回单影像文件ID（sys_file.id） */
    private Long slipFileId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 导入/直连写入数据库的操作人（bigint，与 md_* 同一规则）。
     * <p>部署守卫（P3-D 实证 10-01）：fssc 与 fssc_drill 两库 `op_bank_statement` 均<b>无 create_by/update_by 列</b>
     * 且无已批 ALTER 现有，而 {@code FsscMetaObjectHandler} 对存在 setter 的这两字段一律自动填充 ⇒
     * 列上线前任何 insert/update 都会 "Unknown column" 打红银行流水/收款认领/未达账项三页。
     * 注释态 DDL（DBA/用户放行后执行，执行后移除 exist=false）：
     * <pre>
     * ALTER TABLE op_bank_statement
     *   ADD COLUMN create_by BIGINT NULL COMMENT '导入/直连写入数据库操作人',
     *   ADD COLUMN update_by BIGINT NULL COMMENT '勾对/认领/归档改单操作人';
     * </pre>
     */
    @TableField(exist = false)
    private Long createBy;

    /** 勾对/认领/归档等改单动作的操作人（列未上线，见上守卫说明） */
    @TableField(exist = false)
    private Long updateBy;

    /**
     * 读面补充：该流水是否已被"收款认领核销"占用（由 bank_reconcile 记录 match_status=CLAIMED 派生，
     * 列表查询时回填）。勾对（recon_status=MATCHED）与认领是两件事，此字段只读不落表。
     */
    @TableField(exist = false)
    private Boolean claimed;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(Long bankAccountId) { this.bankAccountId = bankAccountId; }

    public LocalDateTime getTransDate() { return transDate; }
    public void setTransDate(LocalDateTime transDate) { this.transDate = transDate; }

    public String getBankSerial() { return bankSerial; }
    public void setBankSerial(String bankSerial) { this.bankSerial = bankSerial; }

    public String getOppositeName() { return oppositeName; }
    public void setOppositeName(String oppositeName) { this.oppositeName = oppositeName; }

    public String getOppositeAccount() { return oppositeAccount; }
    public void setOppositeAccount(String oppositeAccount) { this.oppositeAccount = oppositeAccount; }

    public String getTransType() { return transType; }
    public void setTransType(String transType) { this.transType = transType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getReconStatus() { return reconStatus; }
    public void setReconStatus(String reconStatus) { this.reconStatus = reconStatus; }

    public Long getReconBillId() { return reconBillId; }
    public void setReconBillId(Long reconBillId) { this.reconBillId = reconBillId; }

    public String getReconBillType() { return reconBillType; }
    public void setReconBillType(String reconBillType) { this.reconBillType = reconBillType; }

    public LocalDateTime getReconTime() { return reconTime; }
    public void setReconTime(LocalDateTime reconTime) { this.reconTime = reconTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }

    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }

    public String getSlipStatus() { return slipStatus; }
    public void setSlipStatus(String slipStatus) { this.slipStatus = slipStatus; }

    public Long getSlipFileId() { return slipFileId; }
    public void setSlipFileId(Long slipFileId) { this.slipFileId = slipFileId; }

    public Boolean getClaimed() { return claimed; }
    public void setClaimed(Boolean claimed) { this.claimed = claimed; }
}
