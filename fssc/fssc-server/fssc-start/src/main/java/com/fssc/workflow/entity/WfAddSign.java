// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程加签（wf_add_sign，带 tenant_id）—— 前/后加签。
 *
 * <p><b>能力边界（务必先读）</b>：Flowable 8 <b>没有</b> Activiti 风格的
 * {@code ProcessInstanceModification}，无法在运行期动态插入 BPMN 节点
 * （已用 {@code javap} 逐类核对：{@code flowable-engine-8.0.0.jar} 内无
 * {@code ProcessInstanceModification*}，只有 {@code ChangeActivityStateBuilder} 与
 * {@code RuntimeService.addMultiInstanceExecution}）。
 * 因此本实现采用<b>「临时改派 + 回跳」</b>等价语义，而非"真·动态加节点"：
 *
 * <ul>
 *   <li><b>前加签 BEFORE</b>：把当前任务改派给加签人；加签人办完<b>不推进流程</b>，
 *       而是把任务交还原办理人（{@code exitAssignee}）再继续。</li>
 *   <li><b>后加签 AFTER</b>：当前办理人办完后，把<b>下一节点</b>新产生的任务改派给加签人；
 *       加签人办完同样不推进，交还下一节点本应得的人（{@code exitAssignee}，空=交还候选组）。</li>
 *   <li><b>可再加签</b>：加签人自己也能再加签 —— 记录天然嵌套（新记录的回跳目标是上一个加签人），
 *       回跳时按 id 倒序取最近一条 ACTIVE 记录即可，无需额外栈结构。</li>
 *   <li><b>一次多节点</b>：{@code signUsers} 支持逗号分隔多人，按 {@code seqIndex} <b>链式依次</b>签。</li>
 * </ul>
 *
 * <p><b>不生效即告知</b>：后加签依赖"推进后恰好产生 1 个新任务"。若为 0（流程已结束）
 * 或 &gt;1（并联/多实例节点），记录置 {@link #STATUS_FAILED} 并通知发起人，绝不静默。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_add_sign")
public class WfAddSign extends TenantAuditBase {

    /** 已登记待生效（后加签：等当前任务办结推进后才落任务） */
    public static final String STATUS_PENDING = "PENDING";
    /** 加签进行中（任务已改派给当前加签人） */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 加签链已走完、任务已交还 */
    public static final String STATUS_DONE = "DONE";
    /** 人工取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";
    /** 未能生效（无新任务 / 并联多任务，已告知发起人） */
    public static final String STATUS_FAILED = "FAILED";

    /** 前加签 */
    public static final String POSITION_BEFORE = "BEFORE";
    /** 后加签 */
    public static final String POSITION_AFTER = "AFTER";

    private String procInstId;

    /** 触发加签的任务ID（前加签=当前任务；后加签=已完成的上游任务） */
    private String srcTaskId;

    /** 实际被加签的任务ID（后加签在流程推进后才回填，故可空） */
    private String taskId;

    private String nodeKey;
    private String nodeName;

    /** {@link #POSITION_BEFORE} / {@link #POSITION_AFTER} */
    private String position;

    /** 加签人用户ID，逗号分隔（多人=链式依次签） */
    private String signUsers;

    /** 当前轮到的加签人下标（0-based） */
    private Integer seqIndex;

    /** 回跳目标：加签链走完后任务应交还的办理人；空=交还候选组/任务池 */
    private String exitAssignee;

    /** {@link #STATUS_PENDING} 等五种取值 */
    private String status;

    private String reason;

    /** 发起加签的人（原办理人或其候选人） */
    private Long createUserId;

    /** 加签链走完 / 取消时间 */
    private LocalDateTime doneTime;
}
