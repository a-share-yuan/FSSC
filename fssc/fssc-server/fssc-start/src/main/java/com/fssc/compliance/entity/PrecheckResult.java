// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 预检结果记录（precheck_result）。每条规则的执行结果明细。 */
@Data
@TableName("precheck_result")
public class PrecheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;

    private String billType;

    private Long ruleId;

    private String ruleCode;

    /** PASS/FAIL/WARNING/SKIP */
    private String checkResult;

    private String checkMessage;

    /** ERROR/WARNING/INFO */
    private String level;

    private String inputData;

    private String outputData;

    private Integer executionTimeMs;

    private String errorDetail;

    private String precheckBatchId;

    // 以下字段来自批次视图的展示口径，precheck_result 表并未建列。
    // 标记为非持久化，MyBatis-Plus 生成 SELECT/INSERT 列面时才不会带上它们。

    /** 员工ID */
    @TableField(exist = false)
    private Long employeeId;

    /** 规则总数 */
    @TableField(exist = false)
    private Integer totalRules;

    /** 通过规则数 */
    @TableField(exist = false)
    private Integer passedRules;

    /** 失败规则数 */
    @TableField(exist = false)
    private Integer failedRules;

    /** 警告规则数 */
    @TableField(exist = false)
    private Integer warningRules;

    /** 总执行耗时 */
    @TableField(exist = false)
    private Integer totalExecutionTimeMs;

    /** 最终状态：PASS/FAIL/WARNING */
    @TableField(exist = false)
    private String finalStatus;

    /** 是否已提交：1-是 0-否 */
    @TableField(exist = false)
    private Integer submitted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}