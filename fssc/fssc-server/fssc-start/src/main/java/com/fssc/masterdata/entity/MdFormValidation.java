// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单字段校验规则（V1.4.85）。
 *
 * <p>对齐 EAS FSSC「检查点配置」的三要素：<b>校验字段 / 基准 / 执行顺序</b>，
 * 并额外给出 severity（不通过是否阻断）与 scope（保存时还是审批时才校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_form_validation")
public class MdFormValidation extends TenantAuditBase {

    private String formCode;

    private String fieldKey;

    /**
     * 规则类型：REQUIRED 必录 / NOT_NULL 非空 / MIN_VALUE 最小值 / MAX_VALUE 最大值 /
     * MIN_LENGTH 最小长度 / MAX_LENGTH 最大长度 / PATTERN 正则 / EMAIL / PHONE /
     * ID_CARD / UNIQUE 唯一 / CUSTOM_EXPRESSION 自定义表达式 / CROSS_FIELD 跨字段比对。
     */
    private String ruleType;

    /** 规则参数（正则表达式/最小值等） */
    private String ruleValue;

    /** 第二参数（跨字段比对时的另一字段） */
    private String ruleValue2;

    /** 校验失败文案（为空则按规则类型自动生成） */
    private String errorMessage;

    /** ERROR 阻断提交 / WARN 仅告警 */
    private String severity;

    /** 生效时机 SAVE 保存时 / APPROVE 审批时 / BOTH */
    private String scope;

    /** 执行顺序 */
    private Integer sortOrder;

    private String status;

    private String remark;
}
