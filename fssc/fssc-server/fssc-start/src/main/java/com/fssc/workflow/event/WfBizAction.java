// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.event;

import java.util.Set;

/**
 * 流程结束收尾动作策略接口（事件编排总线）。
 * <p>
 * 每个业务域实现本接口，在 {@link #bizTypes()} 声明处理的业务类型集合，
 * 在 {@link #execute(WfProcessEndedEvent)} 中执行审批通过/驳回后的收尾逻辑。
 * <p>
 * 收尾动作原子化：生成凭证 / 写回状态 / 占用预算 / 派工 / 下推下游单据 / 推送消息。
 * <p>
 * 配置化（表驱动）：{@code wf_event_action_config} 表记录每个 bizType 对应的
 * action_bean、trigger_when（APPROVED/REJECTED/BOTH）、sort_order、enabled，
 * 新流程接入仅需新增配置行，零代码改动。
 *
 * @see EventOrchestrationBus
 */
public interface WfBizAction {

    /**
     * 该 Action 处理的业务类型集合（如 EXP_CLAIM、PURCHASE_ORDER_REVIEW）。
     * 一个 Action 可处理多个 bizType（如报销域统一处理 CLAIM/LOAN/REPAY/APPLY/PAY）。
     *
     * @return bizType 集合
     */
    Set<String> bizTypes();

    /**
     * 执行收尾动作。
     * <p>
     * 实现方自行区分 approved=true/false 分别处理审批通过与驳回。
     * 异常由 {@link EventOrchestrationBus} 统一捕获并记录日志，不中断其他动作。
     *
     * @param event 流程结束事件（含 bizType/bizId/approved/comment/rejectType/procInstId）
     */
    void execute(WfProcessEndedEvent event);
}
