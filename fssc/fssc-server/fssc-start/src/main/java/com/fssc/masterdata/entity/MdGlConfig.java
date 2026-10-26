// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 记账配置（分录生成规则）（需求 2.20）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_gl_config")
public class MdGlConfig extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 费用类型ID */
    private Long itemId;
    /** 费用类型名称 */
    private String itemName;
    /** 借方科目ID */
    private Long debitSubjectId;
    /** 借方科目编码 */
    private String debitSubjectCode;
    /** 贷方科目ID */
    private Long creditSubjectId;
    /** 贷方科目编码 */
    private String creditSubjectCode;
    /** 会计科目名称（单行分录分支用，存外部财务系统规则原文） */
    private String subjectName;
    /** 会计科目编码（单行分录分支用） */
    private String subjectCode;
    /** 借贷方向：DEBIT/CREDIT */
    private String direction;
    /** 维度-成本中心/项目 */
    private String dimType;
    /** 维度-成本中心类型（管理类/销售类/制造类） */
    private String dimCcType;
    /** 维度-出差类别 */
    private String dimTravel;
    /** 维度-是否专项项目(0/1) */
    private String dimSpecial;
    /** 维度-表单状态(待付款/已付款) */
    private String dimStatus;
    /** 辅助核算规则 */
    private String auxRule;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
