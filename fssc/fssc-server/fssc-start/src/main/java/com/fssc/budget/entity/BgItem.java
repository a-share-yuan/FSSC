package com.fssc.budget.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算明细（bg_item，带 tenant_id）。
 * 预算占用模型：可用额度 = total_amount + adjust_amount - occupy_amount - used_amount。
 * occupy/used 为【汇总值】，明细流水在 bg_occupy（只增不改）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bg_item")
public class BgItem extends TenantAuditBase {

    private Long schemeId;
    private Long companyId;
    private Integer budgetYear;
    /** 期间：0=全年，1-12=月份 */
    private Integer budgetPeriod;
    private Long deptId;
    /** 费用项 */
    private Long itemId;
    private Long projectId;
    /** 维度键，如 DEPT:1_ITEM:5（唯一性约束） */
    private String dimKey;
    /** 预算总额 */
    private BigDecimal totalAmount;
    /** 调整额（调整单审批后累计，可为负） */
    private BigDecimal adjustAmount;
    /** 已占用（未核销） */
    private BigDecimal occupyAmount;
    /** 已核销/已使用 */
    private BigDecimal usedAmount;
    private String currencyCode;
    /** 双预算科目：科目编码（同类系统/同类系统 体系） */
    private String ncAccount;
    /** 双预算科目：IFS 科目码（IFS 体系） */
    private String ifsAccount;
    private String remark;

    /** D-DEC-372：控制方式（HARD/SOFT/WARN）在方案表 bg_scheme.control_type，本表无列；
     *  pick 接口按 BudgetService.controlTypeOf 同口径批量解析后带出，供前端闸门分档（非表列）。 */
    @TableField(exist = false)
    private String controlType;

    /** R5-D-264：执行率（%），非表列——由 BudgetService.execRateOf 单点计算后填充：
     *  分子 = occupy_amount + used_amount（与"可用余额"列同轴），分母 = total_amount + adjust_amount，
     *  不封顶（可 >100 表示超支）；分母 <=0 时为 null（前端显示「—」）。 */
    @TableField(exist = false)
    private BigDecimal execRate;
}
