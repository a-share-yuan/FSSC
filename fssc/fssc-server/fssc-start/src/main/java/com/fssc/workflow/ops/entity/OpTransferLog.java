// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * G3流程批量移交日志（op_transfer_log，带 tenant_id）。
 *
 * <p>流程移交与移交日志：一次批量移交落一条，记「谁把哪几条任务移交给谁、原因」。
 * 与 {@code wf_task_history} 的 TRANSFER 行互补——历史行记单条任务被转办，
 * 本表记一次批量移交这个<b>动作</b>，含失败清单（失败同样记录，不静默吞掉）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_transfer_log")
public class OpTransferLog extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 移交人（原办理人） */
    private Long fromUserId;
    private String fromUserName;
    /** 接收人 */
    private Long toUserId;
    private String toUserName;
    /** 被移交的 Flowable 任务 ID，逗号分隔 */
    private String taskIds;
    /** 请求移交条数 */
    private Integer taskCount;
    private Integer successCount;
    private Integer failCount;
    /** 失败清单（taskId:原因） */
    private String failedDetail;
    /** 移交原因 */
    private String reason;
    /** 操作人（流程管理员 / 移交人本人） */
    private Long operUserId;
    private String operUserName;
    private LocalDateTime operTime;
    private String remark;
}
