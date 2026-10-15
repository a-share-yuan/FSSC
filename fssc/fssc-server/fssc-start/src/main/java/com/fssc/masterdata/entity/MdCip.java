package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 在建工程（基础数据与配置，设计原型 image_104）。
 * 记录在建工程项目主数据，编码唯一，隶属公司（companyId），并可归集成本、转固进固定资产。
 * status: NORMAL正常 / DISABLED停用 / TRANSFERRING转固审批中 / TRANSFERRED已转固。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_cip")
public class MdCip extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 在建工程编码（唯一） */
    private String cipCode;

    /** 在建工程名称 */
    private String cipName;

    /** 所属公司ID（sys_company.id） */
    private Long companyId;

    /** 所属公司名称（冗余展示，按 companyId 反查维护） */
    private String companyName;

    /** 工程类别：建筑安装/设备/待摊/资本化利息 */
    private String projectCategory;

    /** 项目负责人 */
    private String manager;

    /** 归属部门ID（sys_dept.id，可空） */
    private Long deptId;

    /** 归属部门名称（冗余展示） */
    private String deptName;

    /** 开工日期 */
    private LocalDate startDate;

    /** 预计完工日期 */
    private LocalDate expectedEndDate;

    /** 预算总额 */
    private BigDecimal budgetAmount;

    /** 累计归集金额（由成本归集明细实时累加回写，冗余便于列表展示） */
    private BigDecimal accruedAmount;

    /** 已付款金额（ 2026-09-14：CIP 付款审批通过自动凭证时累加回写；列见 sql/impl-cl-20260914.sql DDL） */
    private BigDecimal paidAmount;

    /** 已转固金额 */
    private BigDecimal transferredAmount;

    /** 转固日期 */
    private LocalDate transferDate;

    /** 转固生成的固定资产卡片ID（md_asset.id） */
    private Long assetId;

    /** 备注 */
    private String remark;

    /** 状态 NORMAL正常 / DISABLED停用 / TRANSFERRING转固审批中 / TRANSFERRED已转固 */
    private String status;
}
