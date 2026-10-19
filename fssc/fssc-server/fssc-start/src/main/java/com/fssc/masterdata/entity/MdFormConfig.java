package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 表单详情配置（需求 2.27：配置各业务表单的字段/布局/规则）。
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

    /** 关联流程模型key（参考绑定） */
    private String modelKey;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
