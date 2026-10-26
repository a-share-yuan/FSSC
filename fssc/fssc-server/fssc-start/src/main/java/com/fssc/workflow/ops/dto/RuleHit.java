// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.dto;

/**
 * G3（2026-09-24）分配规则的顺序解析轨迹（一条 = 被评估过的一条规则）。
 *
 * <p>「多条件逐个解析直到命中」：解析器按 {@code op_dispatch_rule.priority}
 * <b>升序</b>逐条评估，命中即止；未命中的规则也要出现在轨迹里，
 * 否则"为什么没走那条规则"永远说不清。
 *
 * @param ruleId     规则 ID
 * @param ruleCode   规则编码
 * @param ruleName   规则名称
 * @param priority   优先级（数值小优先）
 * @param dimension  条件维度（TYPE/AMOUNT/LEGAL/COMPANY/EXPENSE_ITEM/CURRENCY/TASK_TYPE/ALL）
 * @param condition  条件原文
 * @param matched    是否命中
 * @param reason     判定说明
 */
public record RuleHit(
        Long ruleId,
        String ruleCode,
        String ruleName,
        Integer priority,
        String dimension,
        String condition,
        boolean matched,
        String reason) {
}
