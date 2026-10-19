package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 薪酬发放（gl_salary_run，带 tenant_id）。按部门/期间计算应发、扣除与个税，DRAFT → PAID。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_salary_run")
public class GlSalaryRun extends TenantAuditBase {

    /** 记账公司（组织合法性守卫 V1.3.18） */
    private Long companyId;
    /** 发放批次号 */
    private String runNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** 发放部门 sys_dept.id */
    private Long deptId;
    /** 发放人数 */
    private Integer empCount;
    /** 应付总额（含社保） */
    private BigDecimal totalAmount;
    /** 应发工资 */
    private BigDecimal salaryAmount;
    /** 扣除合计（旷工/罚款等） */
    private BigDecimal deductionAmount;
    /** 社会保险合计 */
    private BigDecimal socialSecurityAmount;
    /** 个人所得税 */
    private BigDecimal taxAmount;
    /** DRAFT/PAID */
    private String payStatus;
}