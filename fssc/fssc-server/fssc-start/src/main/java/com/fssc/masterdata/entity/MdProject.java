package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 项目（md_project，带 tenant_id，company_id 归属）。成本归集单元。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_project")
public class MdProject extends TenantAuditBase {

    private Long companyId;
    private String projectCode;
    private String projectName;
    /** 项目类型：客户项目/内部项目/研发项目等 */
    private String projectType;
    private Long deptId;
    private Long managerId;
    private LocalDate beginDate;
    private LocalDate endDate;
    private BigDecimal totalBudget;
    private BigDecimal usedAmount;
    /** NORMAL/DISABLED/FINISHED */
    private String status;
    private String remark;
}
