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

/** 客户对账单（蓝图 SCC_AR_01/02/03）：导入→容差核对→财务调整→对账完成进入开票。 */
@Data
@TableName("ar_statement")
public class ArStatement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String billNo;
    private Long companyId;
    /** 客户编码（十位码，必填） */
    private String customerCode;
    private String customerName;
    /** DOMESTIC/SHIP_NORMAL/OTHER_BIZ/OTHER_INCOME/OVERSEAS */
    private String billType;
    private String ccy;
    private BigDecimal fxRate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** 折本位币金额（国外销售按国税局汇率折算） */
    private BigDecimal rmbAmount;
    private BigDecimal rmbTaxAmount;
    /** 提前开票 1是 */
    private Integer preInvoiceFlag;
    /** UNCHECKED/CHECK_OK/CHECK_FAILED */
    private String checkStatus;
    private String diffReason;
    /** IMPORTED/RECONCILED/ABNORMAL */
    private String status;
    /** 对账明细JSON */
    private String linesJson;
    private String importUser;
    private String remark;
    private LocalDateTime checkTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_IMPORTED = "IMPORTED";
    public static final String ST_RECONCILED = "RECONCILED";
    public static final String ST_ABNORMAL = "ABNORMAL";
}
