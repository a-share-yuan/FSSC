// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合规规则配置（md_compliance_rule，带 tenant_id）。
 *
 * <p>作为智能审单/合规校验的统一规则来源，覆盖四类高频合规风险：</p>
 * <ul>
 *   <li>DUPLICATE_INVOICE 重复发票校验（同一发票号/代码在窗口期被重复报销）</li>
 *   <li>EXPENSE_MISMATCH 费用项目不一致（发票品名规则与报销费用类型不一致）</li>
 *   <li>OVER_STANDARD 超标校验（单笔/单日/单月超出费用标准或限额）</li>
 *   <li>BLACKLIST 黑名单使用校验（供应商/开票方在欠税、走逃、红冲、涉刑问题名单）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_compliance_rule")
public class MdComplianceRule extends TenantBase {

    /** 规则编码（唯一，如 COMP-DUP-INV-001） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类别：DUPLICATE_INVOICE / EXPENSE_MISMATCH / OVER_STANDARD / BLACKLIST */
    private String ruleType;

    /** 风险等级：LOW / MEDIUM / HIGH / CRITICAL */
    private String riskLevel;

    /** 处置动作：BLOCK 拦截 / WARN 警告 / REVIEW 转人工复核 / PASS 仅提示 */
    private String action;

    /** 命中判定表达式（与预检脚本同语法，如 `dupCount >= 1` / `ratio > 1.2`） */
    private String matchExpression;

    /** 阈值配置（JSON，如超标准比例、重复窗口天数） */
    private String thresholdConfig;

    /** 命中后的提示/阻断信息模板 */
    private String messageTemplate;

    /** 适用费用类型（逗号分隔；空表示全局） */
    private String relatedExpenseTypes;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 排序号，数字越小越先执行 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}