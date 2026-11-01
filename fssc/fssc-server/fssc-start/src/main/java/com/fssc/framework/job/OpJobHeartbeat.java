// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务心跳（op_job_heartbeat，跨租户全局运维表，无 tenant_id）。
 * job_name 唯一（uk_job），记录最近一次执行时间/结果/耗时/连续失败次数/最近错误。
 * 【全局表】已登记到 {@code FsscTenantLineHandler} 忽略名单，供调度线程（无租户上下文）读写。
 */
@Data
@TableName("op_job_heartbeat")
public class OpJobHeartbeat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;
    private LocalDateTime lastRunAt;
    /** SUCCESS / FAIL / RUNNING */
    private String lastResult;
    private Long durationMs;
    private Integer failCount;
    private String lastError;
    private LocalDateTime updateTime;
}
