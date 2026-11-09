// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 表单详情配置（配置各业务表单的字段/布局/规则）。
 * 扩展为单据类型管理：bill_type/bill_sub_type/category 标识单据类型，model_key 关联流程模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_form_config")
public class MdFormConfig extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 表单编码 */
    private String formCode;

    /** 单据大类 APPLY事前申请/CLAIM员工报销/PAY对公支付/LOAN借款/REPAY还款 */
    private String billType;

    /** 子类型 TRAVEL/PREPAY/SETTLE/AGENCY/AIR等 */
    private String billSubType;

    /** 入口分类 APPLY/CLAIM/PAY/LOAN/REPAY */
    private String category;

    /** 表单名称 */
    private String formName;

    /** 配置JSON（字段/布局/规则） */
    private String configJson;

    /**
     * 关联流程模型 key。
     *
     * <p>通用单据运行时以本列为起流唯一依据：提交时直接以此 key 启动流程实例，
     * 不再按 bill_type 做 switch 映射。为空时回退 {@link #formCode}。
     */
    private String modelKey;

    /**
     * 流程路由键，对应 {@code wf_process_config.biz_type}。
     * 为空时运行时回退 {@link #formCode}；同一张表单要按公司/金额分档走不同流程时，
     * 由 {@code wf_process_config} 的多行配置承担，本列只给一个默认键。
     */
    private String bizType;

    /**
     * 存储方式：{@code JSON} 由通用运行库 {@code biz_form_bill.form_data} 承载任意字段；
     * {@code EXPENSE_WIDE} 沿用 {@code exp_bill} 宽表与既有费用单据链路。
     */
    private String storeMode;

    /** 发起页路径，空则 {@code /form/{formCode}} */
    private String menuPath;

    /** 侧栏显示名，空则取 {@link #formName} */
    private String menuName;

    /** 挂在哪个菜单目录下（sys_menu.id） */
    private Long menuParentId;

    /** 流程标题取值字段 key；空则 formName + 申请人姓名 */
    private String titleExpr;

    /** 是否允许存草稿 */
    private Integer allowDraft;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
