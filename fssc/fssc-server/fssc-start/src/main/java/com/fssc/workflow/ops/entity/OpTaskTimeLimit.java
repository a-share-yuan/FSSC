// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * G3（2026-09-24）任务优先级与时限落账（op_task_time_limit，带 tenant_id）。
 *
 * <p><b>为什么不给 wf_task 加列</b>：{@code wf_task} 在
 * {@code fssc/db/Dump20260911/fssc_wf_task.sql} 已有真实列定义，G3 硬约束要求"不要臆造列"，
 * 因此优先级与「命中的策略来源」放在本旁表；算出的到期时间仍然写回
 * {@code wf_task.due_time}（既有列），让它从"写了没人读的死列"变成真值（方案 §二 F6）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_task_time_limit")
public class OpTaskTimeLimit extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** Flowable 任务 ID（wf_task.task_id） */
    private String taskId;
    /** 流程实例 ID */
    private String procInstId;
    private String modelKey;
    private String nodeKey;
    /** 优先级：NORMAL普通 / URGENT加急 */
    private String priority;
    /** 命中的策略配置 ID（op_dispatch_policy） */
    private Long policyId;
    /** 本次采用的时限（小时）：按优先级取普通或加急 */
    private BigDecimal limitHours;
    /** 到期时间（与 wf_task.due_time 同值，便于按本表核对漂移） */
    private LocalDateTime dueTime;

    /** 优先级归一：只有 URGENT 是加急，其余一律普通（缺省方向取"不抬高优先级"，避免误升级时限） */
    public static String normalizePriority(String priority) {
        return priority != null && "URGENT".equalsIgnoreCase(priority.trim()) ? "URGENT" : "NORMAL";
    }
}
