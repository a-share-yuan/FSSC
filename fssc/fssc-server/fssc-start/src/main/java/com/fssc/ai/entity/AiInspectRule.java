// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常驻巡检规则（AI 平权 P5"可组织"的最小载体）。
 *
 * <p>一条规则＝目录里一个只读动作 ＋ 触发判据 ＋ 命中后的动作（只允许：进待确认队列）。
 * 规则本身可停用（enabled）、可追溯（last_*），触发产生的高风险写一律走确认票据 —— 与 §6 红线一致。
 * 自然语言→本行的转换器见 {@link com.fssc.ai.AiInspectRuleService}。
 */
@Data
@TableName("ai_inspect_rule")
public class AiInspectRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 规则名（自然语言原文或其摘要，人可读） */
    private String ruleName;

    /** 登记动作码（必须 kind=read ∧ aiAllowed，创建时校验） */
    private String actionCode;

    /** 动作参数模板 JSON（固定参数或 {{today}} 类占位） */
    private String argsTemplate;

    /** 结果判据：$.list/$.total 查询后的比较 */
    private String jsonPath;
    /** LT / LTE / GT / GTE / EQ / NOT_EMPTY / EMPTY */
    private String op;
    private String expected;

    /** 命中后的处置：QUEUE（进待确认队列，默认）——AUTO_EXECUTE 永不开启（需另立裁决） */
    private String onBreach;

    /** 调度频率（分钟）；job 每轮扫 next_run_at&lt;=now 的规则 */
    private Integer intervalMinutes;

    private Integer enabled;
    private LocalDateTime lastRunAt;
    /** OK / BREACHED / ERROR */
    private String lastResult;
    private String lastDetail;

    private String createdBy;
    private String instructionText;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
