package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算类别（需求 3.3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_budget_category")
public class MdBudgetCategory extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 专项类型 RESEARCH科研/ASSET资产运营/EXPENSE费用专项 */
    private String specialType;
    /** EPS 专项费用/资产运营 */
    private String eps;
    /** 是否专项费用 Y/N */
    private String isSpecial;
    /** 类别编码 */
    private String categoryCode;
    /** 类别名称 */
    private String categoryName;
    /** 排序 */
    private Integer sort;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
