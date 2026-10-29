// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程投影事件发件箱（wf_process_outbox，带 tenant_id）——流程配置
 * 「流程处理模式＝消息队列（推荐）／同步」参数的事务性发件箱实现。
 *
 * <p><b>它解决什么</b>：方案 §二 M7 指出，流程发起链路把「引擎推进 ＋ 业务表 ＋ 镜像表 ＋ 站内消息」
 * 全部压在一次请求的同步事务里（{@code WfEngineFacade} 整个类 {@code @Transactional} 计数 = 0，
 * 镜像写与引擎推进本来就不在同一事务）——"顺序同步执行"并不是原子性保证，只是"碰巧在同一个线程里排队"。
 * 本表把镜像投影动作从请求线程剥离：事件当场只落一行 outbox（与业务同事务），由调度器消费。
 *
 * <p><b>为什么是"发件箱"而不是"真 MQ"</b>（方案 §四 4.1 路线 A 的三条致命问题）：
 * <ol>
 *  <li>引外部中间件与"离线可跑"的 dev 约定冲突；</li>
 *  <li>投递物若仍是"一坨业务状态"，消费者照样要回写四张表，问题原样搬家；</li>
 *  <li>消息与业务事务不同源 ⇒ 双写问题不解决。</li>
 * </ol>
 * 本表与业务/DTO 同事务 INSERT，因此<b>不丢事件</b>；上线真 MQ 时只换消费者，投递物（{@code payload}）不变。
 *
 * <p><b>开关与零影响</b>：由 {@code sys_config.wf.process.mode} 控制，缺省 {@code SYNC}（fail-closed）。
 * SYNC 时本表<b>一行不写</b>、消费线程不启动——与原先逐字一致（方案 §三「零行为变化的验收门槛」）。
 *
 * <p><b>字段取舍（不粉饰）</b>：方案列的 {@code seq} 未建。{@code id} 为自增主键，同一事务内多次
 * enqueue 严格递增，已提供"按实例串行"所需的全部顺序信息；消费端按 {@code id ASC} 领取。
 * {@code status} 保留 {@code PENDING|CONSUMED|DEAD} 三态、未加 {@code PROCESSING} 中间态——
 * 消费者跑在单线程专用池上，不存在同表并发抢占（见 {@code WfOutboxConsumerJob}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_outbox")
public class WfProcessOutbox extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 引擎事件类型（与 {@code WfProjectionEvent.eventType} 同值） */
    private String eventType;
    /** 流程实例ID（分片键：同一实例的事件串行消费） */
    private String procInstId;
    /** Flowable 任务ID（流程级事件为空） */
    private String taskId;
    /** 业务单据类型 / ID / 单号（便于按单据排查，不必解 payload） */
    private String bizType;
    private Long bizId;
    private String bizNo;
    /** 投影事件信封 JSON（{@code WfProjectionEvent}）：监听器在事件当场把"只有 Flowable 实体才能解析"的
     *  字段（候选组、到期时间、变量回退）全部解好，消费者只做纯写库，不再触碰引擎。 */
    private String payload;
    /** 唯一键（唯一）：{@code eventType:taskId[:assignee]} / {@code eventType:procInstId}。重复投递被唯一键拒绝＝去重。 */
    private String idempotencyKey;
    /** {@code PENDING} 待消费 / {@code CONSUMED} 已投影 / {@code DEAD} 死信（重试超上限，已记缺陷待人工处置） */
    private String status;
    /** 已尝试消费次数 */
    private Integer attempts;
    /** 下次可消费时间（退避重试；null＝立即可消费） */
    private LocalDateTime nextRetryAt;
    /** 投影成功时刻 */
    private LocalDateTime consumedTime;
    /** 最后一次失败原因（截断到 last_error 列宽） */
    private String lastError;
}