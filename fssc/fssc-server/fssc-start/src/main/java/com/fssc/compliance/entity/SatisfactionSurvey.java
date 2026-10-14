// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** 用户满意度调查（共享服务质量评价）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("satisfaction_survey")
public class SatisfactionSurvey extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联单据ID */
    private Long billId;

    /** 关联单据编号 */
    private String billNo;

    /** 关联服务编码（service_catalog.service_code） */
    private String serviceCode;

    /** 满意度评分 1-5 */
    private Integer score;

    /** 评价内容 */
    private String comment;

    /** 评价人 */
    private Long surveyUserId;

    /** 单据类型 */
    private String billType;
}
