package com.fssc.returns.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购退货单明细行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_return_line")
public class PurReturnLine extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 头表 id（pur_return.id） */
    private Long returnId;
    /** 行号 */
    private Integer lineNo;
    private String itemCode;
    private String itemName;
    /** 退货数量 */
    private BigDecimal qty;
    private String uom;
    /** 含税单价 */
    private BigDecimal price;
    /** 税率 % */
    private BigDecimal taxRate;
    /** 含税金额（正数） */
    private BigDecimal amount;
    /** 源入库单行 id（二期防超退严格校验用） */
    private Long sourceLineId;
    private String remark;
}
