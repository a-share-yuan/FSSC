package com.fssc.bank.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银企未达账差异明细（bank_recon_item，带 tenant_id）。
 * 由未达账项调节表生成落库留痕，衔接 TR05 余额调节表。
 * item_type：BANK_IN 银行已收企业未收 / BANK_OUT 银行已付企业未付 /
 *            ENT_OUT 企业已付银行未付 / ENT_IN 企业已收银行未收(预留)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bank_recon_item")
public class BankReconItem extends TenantAuditBase {

    /** 银行账户ID */
    private Long accountId;
    /** 银行账号 */
    private String accountNo;
    /** 所属月份 yyyyMM */
    private String month;
    /** 未达账类型 BANK_IN/BANK_OUT/ENT_OUT/ENT_IN */
    private String itemType;
    /** 流水号/付款流水号 */
    private String serialNo;
    /** 对手/往来名称 */
    private String oppositeName;
    /** 业务日期 */
    private LocalDate bizDate;
    /** 金额 */
    private BigDecimal amount;
    /** OPEN未清/CLEARED已清结 */
    private String status;
    /** 摘要 */
    private String summary;
    /** 来源引用（statement_id / payment_id） */
    private String sourceRef;
}