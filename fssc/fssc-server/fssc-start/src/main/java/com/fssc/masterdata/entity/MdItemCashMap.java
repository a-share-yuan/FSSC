package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用类型与现金流量码映射（需求 2.33）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_item_cash_map")
public class MdItemCashMap extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 费用类型ID */
    private Long itemId;

    /** 费用类型名称 */
    private String itemName;

    /** 现金流量码 */
    private String cashCode;

    /** 现金流量名称 */
    private String cashName;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
