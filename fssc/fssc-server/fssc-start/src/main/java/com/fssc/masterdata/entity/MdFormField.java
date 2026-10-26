// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单字段元数据（V1.4.85）——闭环差距①「单据/界面不是元数据驱动」。
 *
 * <p><b>改造前</b>：{@code md_form_config} 13 行在库，但 config_json 里只有
 * {@code {"sections":["概要",...]}} 这种分节名，没有任何字段定义，前端零消费。
 * 结果是「新增一种单据 = 改前端代码 + 发版」。
 *
 * <p><b>改造后</b>：字段的控件类型/布局/必录/锁定/默认值/选项全部落本表。
 * 前端按元数据渲染表单，服务端按元数据做校验，字段权限矩阵可对本表做 JOIN。
 * 新增字段 = 插一行数据。
 *
 * <p><b>字段属性对齐通行规则</b>：单据类型支持「布局、字段必录和锁定属性、
 * 业务流程以及打印的设置」—— 本表承载布局与必录/锁定，打印布局见
 * {@link MdPrintTemplateItem}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_form_field")
public class MdFormField extends TenantAuditBase {

    /** 所属表单 md_form_config.form_code */
    private String formCode;

    /** 字段标识（前端 key + 后端实体属性名） */
    private String fieldKey;

    /** 字段显示名 */
    private String fieldLabel;

    /** 数据库列名（为空则不写入数据库，仅展示/计算字段） */
    private String dbColumn;

    /** 所属分节 */
    private String section;

    /** 控件：INPUT/INPUT_NUMBER/TEXTAREA/SELECT/DATE/DATETIME/MONEY/PERCENT/SWITCH/UPLOAD/AMOUNT_LIST/USER_SELECT/DEPT_SELECT/REF */
    private String widgetType;

    /** 控件宽度 FULL/HALF/THIRD/QUARTER */
    private String controlWidth;

    /** 栅格跨列（24 栅格制） */
    private Integer span;

    private Integer sortOrder;

    private String placeholder;

    private String helpText;

    private String defaultValue;

    /** 选项配置 JSON（下拉选项树/参照表查询） */
    private String optionsJson;

    /** 必录属性：1-必录（字段必录属性） */
    private Integer required;

    /** 锁定属性：1-编辑时灰显不可改 */
    private Integer locked;

    /** 只读角色码（逗号分隔；命中角色即只读） */
    private String readonlyRoles;

    /** 可见角色码（逗号分隔；空=全部可见） */
    private String visibleRoles;

    private Integer uniqueFlag;

    /** 1-系统字段（禁删） */
    private Integer systemFlag;

    private String status;

    private String remark;
}
