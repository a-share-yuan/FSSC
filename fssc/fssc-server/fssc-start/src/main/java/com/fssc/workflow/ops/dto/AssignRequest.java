// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.dto;

import java.math.BigDecimal;

/**
 * G3（2026-09-24）自动分配请求。
 *
 * <p>{@code taskRowId} 是 {@code wf_task.id}（业务待办行），单据类型/流程 key/节点 key/金额/单号
 * 都从该行推导，避免调用方拼一套与库里不一致的条件；其余字段是<b>规则匹配的补充维度</b>
 * （作业项/公司/费用项目/币种/法人主体），不传即"该维度不限制"。
 *
 * @param taskRowId    wf_task.id（必填）
 * @param taskType     共享作业项（AUDIT/BOOKKEEP/PAY…）
 * @param amount       单据金额（不传则取 wf_task.amount）
 * @param companyId    公司 ID
 * @param expenseItemId 费用项目 ID
 * @param currencyCode 币种
 * @param legalEntityId 法人主体 ID
 * @param priority     优先级 NORMAL/URGENT（不传按 NORMAL）
 * @param dryRun       是否试算（true 只出决策不写库、不落日志）
 */
public record AssignRequest(
        Long taskRowId,
        String taskType,
        BigDecimal amount,
        Long companyId,
        Long expenseItemId,
        String currencyCode,
        Long legalEntityId,
        String priority,
        Boolean dryRun) {

    public boolean isDryRun() {
        return Boolean.TRUE.equals(dryRun);
    }
}
