// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 工具调用日志（ai_tool_call_log，双日志之一）。不可删除，无 del_flag，Mapper 只提供 insert。
 * 与 sys_oper_log 通过 {@code trace_id} 串联：任一调用可回放"指令→动作→结果→操作人"。
 */
@Data
@TableName("ai_tool_call_log")
public class AiToolCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 全链路追踪 ID，与 X-Trace-Id / sys_oper_log.trace_id 同值 */
    private String traceId;

    /** 用户原始指令文本（AI 侧透传，审计回放的第一现场） */
    private String instructionText;

    /** 调用的模型标识（客户端自报，仅记录不作信任依据） */
    private String model;

    /** 目录动作码，如 expense.ExpenseClaimController.submit */
    private String actionCode;

    private String actionUrl;
    private String actionMethod;
    /** read/write/approve/... 目录分类 */
    private String actionKind;
    private String riskLevel;

    /** 实际请求参数（脱敏后 JSON） */
    private String args;

    private Integer httpStatus;
    /** 业务响应码（Result.code），与 httpStatus 一起区分"传输成功但业务拒绝" */
    private Integer bizCode;
    /** 业务响应摘要（截断） */
    private String bizResult;

    /** 成败：1 成功 0 失败 */
    private Integer status;
    private String errorMsg;

    /** 操作人（真人透传 token 的属主）——AI 不拥有身份，继承下指令的人 */
    private Long operatorUserId;
    private String operatorUsername;

    /** 高风险写：绑定的确认票据；一次性，票据变了参数即作废 */
    private Long confirmId;

    /** 唯一键（写类动作必填，重放不产生第二张单） */
    private String idempotencyKey;

    private String clientIp;
    private Long costMs;
    private LocalDateTime callTime;
}
