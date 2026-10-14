// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.dto;

/**
 * G3（2026-09-24）分配决策结果——<b>必须可解释</b>。
 *
 * <p>方案 G3 的 DoD 是「任一共享作业节点可被"策略＋规则"命中并在日志里可追」，
 * 所以决策对象除了"选了谁"，还带上"用的哪条策略、候选长什么样、为什么是他"，
 * 由 {@code TaskAssignmentService} 原样落 {@code op_dispatch_log}。
 *
 * @param strategy          采用的分配策略（PICK/BALANCE_TIME/BALANCE_VOLUME/SMART）
 * @param policyId          命中的策略配置行 ID（op_dispatch_policy）
 * @param chosenUserId      选中的办理人（抢单模式下为 null，表示"进池待领取"）
 * @param chosenUserName    选中人姓名
 * @param candidateSnapshot 候选快照文本（用户ID:姓名:在手量:预计耗时分钟:超时数）
 * @param reason            决策说明：为什么选他 / 为什么不选人
 */
public record AssignmentDecision(
        String strategy,
        Long policyId,
        Long chosenUserId,
        String chosenUserName,
        String candidateSnapshot,
        String reason) {

    /** 是否真的选出了人（抢单/空池为 false） */
    public boolean assigned() {
        return chosenUserId != null;
    }
}
