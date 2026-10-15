package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 费用政策标准（expense_policy_standard，带 tenant_id）。政策下的具体标准值/限额。合并自 travel_standard（差旅标准）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("expense_policy_standard")
public class ExpensePolicyStandard extends TenantAuditBase {

    /** 政策ID */
    private Long policyId;

    /** 标准类型：AMOUNT-金额标准 QUOTA-限额 RATE-比例 */
    private String standardType;

    /** 标准键 */
    private String standardKey;

    /** 标准最小值 */
    private BigDecimal standardValueMin;

    /** 标准最大值 */
    private BigDecimal standardValueMax;

    /** 标准单位 */
    private String standardUnit;

    /** 级别类型：EMPLOYEE_LEVEL-员工级别 POSITION-职位 DEPARTMENT-部门 */
    private String levelType;

    /** 级别值 */
    private String levelValue;

    /** 超标动作：WARN-警告 BLOCK-阻止 EXTRAAUTH-附加审批 */
    private String overtimeAction;

    /** 超标审批级别（VARCHAR(32)） */
    private String overtimeAuthLevel;

    /** 备注 */
    private String remark;

    // ========== 合并自 travel_standard（差旅标准） ==========

    /** 员工级别 */
    private String employeeLevel;

    /** 职位 */
    private String position;

    /** 国内交通标准JSON */
    private String domesticTransport;

    /** 国际交通标准JSON */
    private String internationalTransport;

    /** 国内住宿标准 */
    private BigDecimal domesticAccommodation;

    /** 国际住宿标准 */
    private BigDecimal internationalAccommodation;

    /** 国内餐饮标准 */
    private BigDecimal domesticMeal;

    /** 国际餐饮标准 */
    private BigDecimal internationalMeal;

    /** 国内其他标准 */
    private BigDecimal domesticOther;

    /** 国际其他标准 */
    private BigDecimal internationalOther;

    /** 一线城市系数 */
    @TableField("city_tier_1_factor")
    private BigDecimal cityTier1Factor;

    /** 二线城市系数 */
    @TableField("city_tier_2_factor")
    private BigDecimal cityTier2Factor;

    /** 三线城市系数 */
    @TableField("city_tier_3_factor")
    private BigDecimal cityTier3Factor;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 状态：1-启用 0-停用 */
    private Integer stdStatus;
}