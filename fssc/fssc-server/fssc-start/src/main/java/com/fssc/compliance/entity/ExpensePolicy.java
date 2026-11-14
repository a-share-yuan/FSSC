package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用政策（expense_policy）：合规预检与审批规则引擎的依据。
 * status：1 启用 / 0 停用（Integer，与 ExpensePolicyService/ApprovalRuleService 一致）。
 */
@Data
@TableName("expense_policy")
public class ExpensePolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String policyCode;
    private String policyName;
    /** 关联费用项（明细级政策） */
    private Long expenseItemId;
    private String expenseItemName;
    /** 费用类型编码（类型级政策，如 TRAVEL/MEAL） */
    private String expenseType;
    /** 适用组织（空=全组织） */
    private Long orgId;
    /** 单笔金额上限（空=不限） */
    private BigDecimal maxAmount;
    /** 是否必须附发票 1是 */
    private Integer needInvoice;
    /** 提交时限（费用发生后天数，空=不限） */
    private Integer submitDays;
    /** 超过此金额需更高级审批（空=不启用） */
    private BigDecimal approveAboveAmount;
    /** 优先级（数字越大越优先匹配） */
    private Integer priority;
    /** 生效开始日期（空=不限制） */
    private LocalDate effectiveStart;
    /** 生效结束日期（空=不限制） */
    private LocalDate effectiveEnd;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
