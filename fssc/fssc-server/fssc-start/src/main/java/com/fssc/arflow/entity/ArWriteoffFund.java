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
 * 核销资金池（蓝图核销管理 9.4「收款/票据信息」表格）：可匹配核销的资金来源——
 * 收款（模板导入/预登记）、票据（应收票据）、对冲虚拟收付。remain_amount 未核销余额。
 */
@Data
@TableName("ar_writeoff_fund")
public class ArWriteoffFund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 资金单号（收款单号/票据编号/对冲单号） */
    private String fundNo;
    /** RECEIPT收款/NOTE票据/OFFSET对冲虚拟收付 */
    private String fundType;
    /** 票据核销来源（ar_note.id） */
    private Long noteId;
    private Long customerId;
    private String customerName;
    private String ccy;
    private BigDecimal fundAmount;
    private BigDecimal remainAmount;
    private LocalDate fundDate;
    private String bankSerial;
    /** 债务抵消协议编号（对冲核销） */
    private String agreementNo;
    /** AVAILABLE可用/EXHAUSTED已用完/VOIDED已作废 */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String T_RECEIPT = "RECEIPT";
    public static final String T_NOTE = "NOTE";
    public static final String T_OFFSET = "OFFSET";

    public static final String ST_AVAILABLE = "AVAILABLE";
    public static final String ST_EXHAUSTED = "EXHAUSTED";
    public static final String ST_VOIDED = "VOIDED";
}
