// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

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
    /** 双预算科目：科目编码 */
    private String ncAccount;
    /** 双预算科目：外部财务系统科目码 */
    private String ifsAccount;
    private String remark;

    /**
     * 预算类型：关联 {@code md_budget_type.id}。
     * 此前四张预算主数据「配了不用」——表里有人在维护、代码零消费；本列是接入的第一处。
     * 为空＝历史行，按常规预算处理（不强制回填，避免存量 108 行全部要人工补）。
     */
    private Long budgetTypeId;

    /**
     * 预算期间：关联 {@code md_budget_period.id}。
     * 与 {@link #budgetYear}/{@link #budgetPeriod} 并存：整数两列保留为「快速归集键」，
     * 本列提供期间主数据的起止日期，占用匹配时按日期覆盖优先于裸整数。
     */
    private Long budgetPeriodId;

    /**
     * 冻结标志：1=冻结，占用判定直接拒绝、释放/核销照常放行。
     * 冻结用于「本期不再新增支出但不撤销已批预算」的场景，与停用整张方案不同粒度。
     */
    private Integer freeze;

    /** 控制方式（HARD/SOFT/WARN）在方案表 bg_scheme.control_type，本表无列；
     *  pick 接口按 BudgetService.controlTypeOf 同一规则批量解析后带出，供前端校验分档（非表列）。 */
    @TableField(exist = false)
    private String controlType;

    /** 费用项映射级控制方式（md_item_budget_map.control_type），为空回落方案级；非表列 */
    @TableField(exist = false)
    private String itemControlType;

    /** 执行率（%），非表列——由 BudgetService.execRateOf 单点计算后填充：
     *  分子 = occupy_amount + used_amount（与"可用余额"列同轴），分母 = total_amount + adjust_amount，
     *  不封顶（可 >100 表示超支）；分母 <=0 时为 null（前端显示「—」）。 */
    @TableField(exist = false)
    private BigDecimal execRate;
}
