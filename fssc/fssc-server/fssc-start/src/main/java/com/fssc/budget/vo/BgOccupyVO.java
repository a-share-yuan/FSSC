package com.fssc.budget.vo;

import com.fssc.budget.entity.BgOccupy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算占用流水 VO（带预算明细名称）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BgOccupyVO extends BgOccupy {

    /** 预算明细名称（来自 bg_item → md_expense_item.item_name） */
    private String budgetItemName;
}
