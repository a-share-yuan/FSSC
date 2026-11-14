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
 * 资金收款单（tr_credit_bill）。
 * 渠道流水认领生成应收收款，与银行流水自动对账后由 PENDING 流转到 RECONCILED（已核销）。
 */
@Data
@TableName("tr_credit_bill")
public class TrCreditBill implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String billNo;
    /** 收款日期 */
    private LocalDate receiveDate;
    private Long customerId;
    /** 支付方式 */
    private String payMethod;
    /** 付款方银行 */
    private String payerBank;
    /** 付款方账号 */
    private String payerAccount;
    private BigDecimal amount;
    private String currencyCode;
    /** 渠道 / 对账来源 */
    private String sourceChannel;
    /** 是否对账 0否/1是 */
    private Boolean reconFlag;
    private LocalDateTime reconTime;
    /** PENDING已认领/RECONCILED已核销 */
    private String status;
    /** Flowable 流程实例ID */
    private String procInstId;
    /** 归属公司（法人主体） */
    private Long companyId;
    /** 登记人（补列；本表没有提交人列的话，deny_self 与自审回避只能回落实例发起人） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}