// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入导出模板字段定义。
 *
 * <p>承载「中文列头 ↔ 库字段」的映射与声明式转换规则，
 * 让改列头、加入口校验、调整枚举取值都变成改数据而非改代码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_io_template_field")
public class MdIoTemplateField extends TenantAuditBase {

    /** 所属模板 */
    private Long templateId;

    /** 库字段名（实体属性名，驼峰） */
    private String fieldKey;

    /** Excel 列头文案（用户可见） */
    private String columnName;

    /** STRING/NUMBER/DATE/DATETIME/BOOLEAN/ENUM */
    private String columnType;

    /** 数据库列名（为空时由 fieldKey 驼峰转下划线） */
    private String dbColumn;

    /** 必录：1-缺值该行报错 */
    private Integer required;

    /** 字典类型（ENUM 时优先用它校验） */
    private String dictType;

    /** 枚举可选值（逗号分隔） */
    private String enumValues;

    /** 参照表（按 refCodeCol 反查主键写入） */
    private String refTable;

    private String refCodeCol;

    private String refNameCol;

    private String defaultValue;

    private Integer maxLength;

    private Integer decimalScale;

    private Integer sortOrder;

    private String remark;
}
