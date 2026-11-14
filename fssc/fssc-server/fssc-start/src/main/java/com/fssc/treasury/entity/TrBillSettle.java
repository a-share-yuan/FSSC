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
 * 票据结算（tr_bill_settle）。
 * 银承/商承票据登记后，可背书转让、贴现、到期托收收妥，状态随之流转。
 */
@Data
@TableName("tr_bill_settle")
public class TrBillSettle implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String billNo;
    /** 票据号 */
    private String ticketNo;
    /** 票面种类：银承/商承 */
    private String ticketType;
    /** 出票行 */
    private String issuerBank;
    private BigDecimal amount;
    private LocalDate issueDate;
    private LocalDate dueDate;
    /** 到期日 */
    private LocalDate focusDate;
    /** 收款人 */
    private String beneficiary;
    /** 是否已背书 0否/1是 */
    private Boolean endorseFlag;
    /** STOCK在库/NEGOSIATED背书/DISCOUNTED贴现/COLLECTION托收/MATURED到期 */
    private String status;
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