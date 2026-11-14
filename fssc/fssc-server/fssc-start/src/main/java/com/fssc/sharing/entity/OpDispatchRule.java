package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 抢派单规则（.1）：定义工单分派到各小组任务池的规则条件。
 * 分派模式：SYSTEM 系统分派 / MANUAL 人工处理。
 * 规则条件：按 共享作业项 / 公司 / 费用类型 / 币种 组合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_dispatch_rule")
public class OpDispatchRule extends TenantAuditBase {
    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;

    // 合并自 dispatch_rule（派单规则）

    /** 派单维度 */
    private String dispatchDimension;

    /** 匹配条件 */
    private String matchCondition;
    /** 共享作业项（AUDIT/BOOKKEEP/PAY 等） */
    private String taskType;
    /** 适用公司 id（NULL=全部） */
    private Long companyId;
    /** 适用公司名称（冗余） */
    private String companyName;
    /** 适用费用类型 id（NULL=全部） */
    private Long expenseItemId;
    /** 适用费用类型名称（冗余） */
    private String expenseItemName;
    /** 适用币种（NULL=全部） */
    private String currencyCode;
    /** 目标业务分组 id（任务池归属） */
    private Long targetGroupId;
    /** 目标分组名称（冗余） */
    private String targetGroupName;

    /** 派单策略：ROUND_ROBIN/LEAST_LOADED/RANDOM */
    private String dispatchStrategy;
    /** 分派模式 SYSTEM/MANUAL */
    private String mode;
    /** 优先级（数值小优先） */
    private Integer priority;
    /** 超时 SLA 小时数 */
    private BigDecimal slaHours;
    /** 状态 NORMAL/DISABLED */
    private String status;
    /** 备注 */
    private String remark;

    /** 目标分组是否自动派单（关联 ShGroup.isAutoDispatch，仅查询展示用，非持久化字段） */
    @TableField(exist = false)
    private String targetGroupAutoDispatch;
}
