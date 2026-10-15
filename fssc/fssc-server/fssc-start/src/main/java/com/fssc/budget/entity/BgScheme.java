package com.fssc.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 预算方案（bg_scheme，带 tenant_id）。年度预算编制单元。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bg_scheme")
public class BgScheme extends TenantAuditBase {

    private Long companyId;
    private String schemeCode;
    private String schemeName;
    private Integer budgetYear;
    /** MONTH按月 / QUARTER按季 / YEAR按年 */
    private String periodType;
    /** 预算维度，逗号分隔：DEPT,ITEM,PROJECT */
    private String dimension;
    /** WARN预警 / HARD硬控 / SOFT软控 */
    private String controlType;
    /** 1 结余结转下期 */
    private Integer carryOver;
    /**
     * DRAFT草稿 / APPROVING审批中 / ISSUED已下达 / ACTIVE启用(存量口径，等效已下达) / CLOSED关闭
     * 新增状态机 DRAFT→APPROVING→ISSUED；既有 ACTIVE 方案保持有效，
     * 预算硬控/占用引用口径为 status IN ('ACTIVE','ISSUED')。
     */
    private String status;
    private String remark;
    /** 下达时间（流程审批通过或手动下达时回写；列见 sql/impl-cl-20260914.sql DDL） */
    private LocalDateTime issueTime;
    /** 下达人（流程审批通过=末节点审批人 / 手动下达=操作人） */
    private Long issueBy;
}
