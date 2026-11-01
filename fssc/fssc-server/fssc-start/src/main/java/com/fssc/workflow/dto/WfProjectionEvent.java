// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 流程镜像投影事件信封（G1 单一写入者 + ④层消息队列的共同投递物）。
 *
 * <p><b>为什么要有它（而不是直接把 Flowable 实体交给投影逻辑）</b>：
 * <ol>
 *   <li><b>消息队列要求投递物可序列化</b>：{@code Task}/{@code ProcessInstance} 是引擎活动对象，
 *       出了事件回调就失效，不能进 outbox 的 {@code payload}；</li>
 *   <li><b>"解析"与"写库"必须分层</b>：候选组要 {@code NodeOrgResolver.listOperableUserIds(task)}、
 *       到期时间要 {@code WfNodeConfigResolver}、变量要 {@code runtimeService.getVariables}——
 *       这些<b>只有在事件当场（Flowable 实体还在、流程变量还没被引擎清理）才解析得准</b>。
 *       故由监听器把结果全部解好装进本信封，{@code WfProjectionService} 只做纯写库，
 *       异步消费时也无需再触碰引擎。</li>
 * </ol>
 *
 * <p><b>字段含义</b>：与 {@code WfMirrorProjectionListener} 的字段级映射清单一一对应，
 * 来源优先级仍是"流程变量优先 → 镜像实例行回退 → NOT NULL 兜底"，只是<b>回退已在构造期完成</b>。
 *
 * <p><b>时间字段为什么用 epoch 毫秒而不是 {@code LocalDateTime}</b>：本信封必须能 JSON 往返
 * （outbox {@code payload} 列）。用 long 使序列化/反序列化不依赖 jackson 的 JavaTimeModule 配置，
 * 少一个"配置没生效就整条反序列化失败"的隐患；写入镜像表时在投影服务内还原为 {@code LocalDateTime}。
 */
@Data
public class WfProjectionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // 事件标识

    /** 事件类型：{@code PROCESS_STARTED / TASK_CREATED / TASK_ASSIGNED / TASK_COMPLETED / TASK_DELETED / PROCESS_COMPLETED} */
    private String eventType;

    // 归属

    /** 归属租户（事件当场解析：镜像实例行优先 → 当前线程租户）。null＝上下文不可恢复，投影方记缺陷后跳过。 */
    private Long tenantId;

    // 实例

    private String procInstId;
    private String procDefId;
    /** 流程定义 key（镜像实例行优先，取不到回退 {@code procDefId} 前缀） */
    private String modelKey;
    /** 业务归属（变量优先 → 镜像实例行回退） */
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private BigDecimal amount;
    /** 发起人用户ID（镜像实例行优先，取不到取当前登录用户） */
    private Long startUserId;
    /** 流程结束时的结果（{@code approved} 流程变量）：true＝APPROVED / false＝REJECTED；null＝非结束事件或变量已清理 */
    private Boolean approved;

    // 任务（任务级事件才有）

    private String taskId;
    private String nodeKey;
    private String nodeName;
    /** 任务创建时刻（epoch 毫秒，来源 {@code Task.getCreateTime()}） */
    private Long taskCreateTimeMillis;
    /** 办理人用户ID（{@code Task.getAssignee()} 非数字时为 null） */
    private Long assigneeId;
    /**
     * 办理人原样字符串（{@code Task.getAssignee()} 未做数字解析）。
     *
     * <p><b>为什么与 {@link #assigneeId} 并存</b>：原投影实现的 TASK_CREATED 分支把<b>原样字符串</b>
     * 写进 {@code wf_instance.current_assignee}（而非解析后的数字），TASK_ASSIGNED 分支写的是解析值。
     * 两处规则本就不一致，本次搬迁<b>不做"顺手统一"</b>——保持与原先逐字一致，
     * 差异留给后续单独一轮办结（避免在"零行为变化"的改造里夹带语义变更）。
     */
    private String assigneeRaw;
    /** 候选组任务的可办理人（已按 {@code wf_task.candidate_ids} 列宽 990 截断并包装为 {@code ,1,2,}） */
    private String candidateIds;
    /** 到期时刻（epoch 毫秒，来源 {@code wf_node_config.timeout_hours}；未配置超时则为 null） */
    private Long dueTimeMillis;

    // 记录

    /** 审批意见（{@code comment} 流程变量） */
    private String comment;
    /** 操作人用户ID（{@code Task.getAssignee()}） */
    private Long operUserId;
    /** 操作人姓名（当前登录态可取到时） */
    private String operUserName;
}