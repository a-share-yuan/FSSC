// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.dto;

/**
 * G3分配策略候选人的负载画像。
 *
 * <p>三种策略都只看这三个数，规则只有一份（避免"均衡·按时"与"均衡·按量"各算一套）：
 * <ul>
 *   <li>{@code pendingCount} 在手待办数 —— 均衡·按量的判据；</li>
 *   <li>{@code expectedMinutes} 在手任务预计耗时合计 —— 均衡·按时的判据（同数量但节点轻重不同时不再等价）；</li>
 *   <li>{@code overdueCount} 其中已按 {@code due_time} 判超时的条数 —— 智能策略的"时限紧迫度"。</li>
 * </ul>
 *
 * @param userId 用户 ID
 * @param userName 用户姓名（冗余，便于日志直读）
 * @param pendingCount 在手待办数
 * @param expectedMinutes 在手任务预计耗时合计（分钟）
 * @param overdueCount 已超时条数（按 wf_task.due_time 真值判定）
 */
public record CandidateLoad(
        Long userId,
        String userName,
        int pendingCount,
        long expectedMinutes,
        int overdueCount) {

    /** 超时风险：已超时条数 / 在手条数（无在手任务时记 0，不给"空闲的人"凭空加分） */
    public double urgencyRatio() {
        return pendingCount <= 0 ? 0d : (double) overdueCount / pendingCount;
    }
}
