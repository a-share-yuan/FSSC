// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程处理详细日志（wf_process_log，带 tenant_id）——流程配置「详细日志开关」（参数 4.3）：
 * 「勾选后流程处理详细日志写 log 与流程日志（管理中心可查）」。
 *
 * <p><b>为什么要有它</b>（改造方案 §三④ / §四 C 档）：改造前我们<b>既无开关、也无流程日志视图</b>，
 * 且方案 §二 F2 指出镜像写失败被静默吞掉——结果是"流程为什么没往前走"在生产上无从查证，
 * 只能靠翻服务器 log 文件逐行找。本表把流程处理的关键事实（发起/派单/办结/取消/结束）
 * 落成可查询的流水，管理中心按实例/单据/时间一查即得。
 *
 * <p><b>与 wf_task_history 的分工（勿重复造）</b>：
 * <ul>
 *  <li>{@code wf_task_history} 是<b>审批记录</b>（审计规则，只记"谁审了什么"，面向时间线展示）；</li>
 *  <li>本表是<b>处理日志</b>（诊断规则，记引擎事件级明细：任务生成时候选人有谁、变量值是什么、
 *      节点配置解析到了什么），面向"为什么这单卡住了"的排查，可按 {@code log_type} 过滤、可按天清理。</li>
 * </ul>
 *
 * <p><b>开关与零影响</b>：由 {@code sys_config.wf.log.detailed.enabled} 控制，缺省 {@code 0}（关闭，
 * fail-closed，默认不勾选）。关闭时监听器<b>一个字段都不写</b>（只做一次配置读取即返回），
 * 库中零新增行，性能与行为与改造前逐字一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_log")
public class WfProcessLog extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID */
    private String procInstId;
    /** Flowable 任务ID（流程级事件为空） */
    private String taskId;
    /** 流程定义key */
    private String modelKey;
    /** 节点ID（BPMN userTask id） */
    private String nodeKey;
    private String nodeName;
    /** 业务单据类型 / ID / 单号（变量查询，缺失为空） */
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    /** 办理人（数字用户ID；候选组任务可能为空） */
    private Long assigneeId;
    /**
     * 日志类型（引擎事件归一）：
     * {@code PROCESS_STARTED} 流程发起 / {@code TASK_CREATED} 任务生成 / {@code TASK_ASSIGNED} 任务指派 /
     * {@code TASK_COMPLETED} 任务办结 / {@code TASK_CANCELLED} 任务取消 / {@code PROCESS_COMPLETED} 流程结束 /
     * {@code TIMER_FIRED} 定时器触发。
     */
    private String logType;
    /** 级别：INFO 正常 / WARN 异常但不致命 */
    private String level;
    /** 人类可读的一句话（管理中心列表直接显示，不必再拼字段） */
    private String content;
    /** 事件当时的明细快照（JSON：变量、候选人数、审批项结果等），供展开查看 */
    private String detail;
    /** 操作人用户ID（能从上下文取到时） */
    private Long operUserId;
}
