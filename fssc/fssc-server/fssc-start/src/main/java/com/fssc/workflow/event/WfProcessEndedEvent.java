// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.event;

import lombok.Data;

/**
 * 流程结束事件（Spring 事件）。
 * Flowable 引擎 PROCESS_COMPLETED → WfEngineFacade 发布本事件，
 * 业务域（如报销）监听后执行核销/释放等收尾动作。
 * 事件携带流程变量 bizType/bizId/approved。
 *
 * <p>新增 rejectType（驳回类型，仅驳回时非空）。
 * <ul>
 *   <li>THROW_BACK（漏单驳回）：电子流直接送回申请人，仅可重传影像/改简字段，流程即刻终止</li>
 *   <li>GRADE_BY_GRADE（逐级驳回，默认）：按审批链逐级驳回，字段可全改</li>
 * </ul>
 *
 * <p>ROOT-17：新增 {@link #endType} 显式区分"驳回"与"申请人撤回"。
 * 此前唯一的判据是消费方拿 {@code comment} 与中文字面量「申请人撤回」做 {@code equals}
 * （{@code ExpenseWfEventListener:119}），而 {@code comment} 是给人看的文案：
 * 改一个字、或将来让撤回带自定义原因，收尾分支就静默走成"驳回"——单据状态、
 * 驳回类型写入数据库、记录动作全部跟着错，且不会报错。属"文案承载业务语义"的同族病根。
 */
@Data
public class WfProcessEndedEvent {

    /** 业务类型，如 EXP_CLAIM */
    private String bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 审批结果：true 通过 / false 驳回 */
    private Boolean approved;
    /**
     * 租户ID（架构修复）：流程结束事件脱离发起请求的租户上下文，
     * 若不在事件上携带租户，业务收尾（状态翻转/凭证生成）将在无租户上下文下执行，
     * 导致 MP 租户拦截器跳过 WHERE 条件或 TenantContext.getTenantId() fail-closed 抛异常。
     * 由 WfEngineFacade 从 wf_instance.tenant_id 填充，EventOrchestrationBus 据其重建上下文。
     */
    private Long tenantId;
    /** 审批意见 */
    private String comment;
    /** 驳回类型：THROW_BACK 漏单驳回 / GRADE_BY_GRADE 逐级驳回；通过或未指定时为空 */
    private String rejectType;
    /** Flowable 流程实例ID（新增）：业务收尾可读流程变量，如跨法人调拨 targetCompanyId */
    private String procInstId;
    /**
     * 结束类型（ROOT-17）：仅"撤回"路径显式置 {@link #END_WITHDRAWN}；
     * 通过与驳回两路仍按 {@code approved} 判定，本列留空（不给同一事实造第二套真值）。
     */
    private String endType;

    /**
     * 办结本流程的<b>操作人</b>用户ID（P3-A2 前置件 ·，2026-10 未标级修复战役 K4 批）。
     *
     * <p>为什么必须有：业务收尾要落"复核人/审核人"这类追责列（如 {@code exp_bill.audit_user_id}），
     * 而本事件此前只携带 bizType/bizId/approved/comment——收尾方拿不到"是谁点的最后一节"，
     * 于是那些列建成建表以来恒 NULL（{@code audit_time}/{@code audit_user_id} 审完仍空）。
     *
     * <p><b>取值规则（硬约束，改动前请读全）</b>：只可来自 {@code WfEngineFacade.complete()} 的
     * {@code operUserId} 形参（人工办结链与超时自动通过链两处 {@code taskService.complete()}），
     * 由门面线程标记随 PROCESS_COMPLETED 同步派发带出。
     * <b>严禁改为反查 {@code wf_task_history} 末节 APPROVE 行</b>：门面在
     * {@code taskService.complete()} <b>内</b>同步触发本事件，而它自己的
     * {@code writeHistory(APPROVE)} 在 complete() <b>之后</b>才写——事件时刻末节办理行还没写入数据库，
     * 反查只会取到<b>上一节</b>的人，那是伪造追责证据（时序坑实证见
     * {@code WfQueryService} 的 段与 {@code ExpenseWfEventListener} 的复核人注释）。
     *
     * <p><b>为空是合法状态</b>：机器路径（超时自动通过、候选组未认领）本就没有确定的人，
     * 保持为空与 「取不到就留空，绝不写猜测」同一规则；消费方不得把空值当"系统"回填。
     */
    private Long endOperUserId;

    /** {@link #endType} 取值：申请人撤回 */
    public static final String END_WITHDRAWN = "WITHDRAWN";

    /** 本条结束事件是否由申请人撤回引起（非审批人驳回）。 */
    public boolean withdrawn() {
        return END_WITHDRAWN.equals(endType);
    }

    public WfProcessEndedEvent(String bizType, Long bizId, Boolean approved, String comment) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.approved = approved;
        this.comment = comment;
    }

    public WfProcessEndedEvent(String bizType, Long bizId, Boolean approved, String comment, String rejectType) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.approved = approved;
        this.comment = comment;
        this.rejectType = rejectType;
    }

    public WfProcessEndedEvent(String bizType, Long bizId, Boolean approved, String comment, String rejectType, String procInstId) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.approved = approved;
        this.comment = comment;
        this.rejectType = rejectType;
        this.procInstId = procInstId;
    }
}