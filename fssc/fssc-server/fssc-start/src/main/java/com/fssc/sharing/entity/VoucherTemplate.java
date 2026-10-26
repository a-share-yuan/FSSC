// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 凭证模板（op_voucher_template，带 tenant_id）。
 * 按来源类型配置科目和摘要模板，用于自动生成凭证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_voucher_template")
public class VoucherTemplate extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 模板编码 */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 来源类型: EXPENSE费用/LOAN借款/PAY付款/REPAY还款/CONTRACT合同/ADVANCE预付款 */
    private String sourceType;
    /** 凭证字: JOURNAL记/RECEIPT收/PAYMENT付/TRANSFER转 */
    private String voucherWord;
    /** 摘要模板，支持{billTitle}等变量 */
    private String summaryTemplate;
    /** 借方科目编码 */
    private String debitSubjectCode;
    /** 借方科目名称 */
    private String debitSubjectName;
    /** 贷方科目编码 */
    private String creditSubjectCode;
    /** 贷方科目名称 */
    private String creditSubjectName;
    /** 辅助核算-部门: 0否1是 */
    private Integer auxDept;
    /** 辅助核算-项目: 0否1是 */
    private Integer auxProject;
    /** 辅助核算-供应商: 0否1是 */
    private Integer auxSupplier;
    /** 适用金额下限 */
    private BigDecimal amountMin;
    /** 适用金额上限 */
    private BigDecimal amountMax;
    /** 优先级，数字小优先 */
    private Integer priority;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}