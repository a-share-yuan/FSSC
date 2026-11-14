package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 智能审单规则（op_review_rule，带 tenant_id）。定义自动审核规则。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_review_rule")
public class ReviewRule extends TenantBase {

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则分类：INVOICE/BUDGET/STANDARD/BUSINESS/COMPLIANCE/FRAUD */
    private String ruleCategory;

    /** 匹配条件表达式（同预检脚本语法） */
    private String matchExpression;

    /** 执行动作：AUTO_PASS/AUTO_REJECT/MANUAL_REVIEW */
    private String action;

    /** 优先级，数字越小越先执行 */
    private Integer priority;

    /** 风险等级：LOW/MEDIUM/HIGH */
    private String riskLevel;

    /** 自动通过率（统计） */
    private BigDecimal passRate;

    /** 状态：NORMAL/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    // 合并自 precheck_rule（预检规则）

    /** 规则级别：ERROR/WARNING/INFO */
    private String ruleLevel;

    /** 执行顺序 */
    private Integer ruleOrder;

    /** 校验脚本 */
    private String scriptContent;

    /** 输入参数定义 */
    private String inputParams;

    /** 校验失败提示 */
    private String outputMessage;

    /** 适用费用类型 */
    private String relatedExpenseTypes;

    /** 适用组织类型 */
    private String relatedOrgTypes;

    /** 是否并行：1-是 0-否 */
    private Integer isParallel;

    /** 超时时间(毫秒) */
    private Integer timeoutMs;
}