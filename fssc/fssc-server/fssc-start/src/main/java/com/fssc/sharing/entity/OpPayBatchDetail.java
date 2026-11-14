package com.fssc.sharing.entity;

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

/** 出纳付款批次明细（op_pay_batch_detail，带 tenant_id）。无 create_by，独立实体。 */
@Data
@TableName("op_pay_batch_detail")
public class OpPayBatchDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long batchId;
    /** 单据付款记录ID（exp_bill_payment） */
    private Long billPaymentId;
    private String payeeName;
    /** 供应商档案ID（md_supplier.id）；收款方为供应商且付款记录自带档案 id 时写入，否则 NULL */
    private Long supplierId;
    private String bankName;
    private String bankAccount;
    private BigDecimal amount;
    private String currencyCode;
    /** 用途标志（还款/报销/货款…） */
    private String useFlag;
    private String postscript;
    /** 银行流水号 */
    private String bankSerial;
    /** WAITING/SUCCESS/FAILED */
    private String status;
    private String failReason;
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
