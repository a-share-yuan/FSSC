package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用类型与预算科目映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_item_budget_map")
public class MdItemBudgetMap extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 费用类型ID */
    private Long itemId;

    /** 费用类型名称 */
    private String itemName;

    /** 预算科目ID */
    private Long budgetItemId;

    /** 预算科目维度 */
    private String budgetDimKey;

    /** 状态 */
    private String status;

    /**
     * 控制档位：HARD/SOFT/WARN。
     * 为空＝沿用方案级 {@code bg_scheme.control_type}（存量 211 行全部为空，改造后行为不变）；
     * 有值＝「方案 + 费用项映射」级覆盖，粒度到费用项，不再整方案一个档位。
     */
    private String controlType;

    /** 备注 */
    private String remark;

}
