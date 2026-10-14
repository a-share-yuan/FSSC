// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程事件收尾动作配置（wf_event_action_config，表驱动）。
 * <p>
 * 每行映射一个 bizType → action_bean 的收尾配置，
 * {@link com.fssc.workflow.event.EventOrchestrationBus} 在启动时读取本表，
 * 按 bizType 分组、按 sort_order 排序、按 trigger_when 过滤后分发事件。
 * <p>
 * 设计目标：新流程接入仅需新增配置行（INSERT），零代码改动。
 */
@Data
@TableName("wf_event_action_config")
public class WfEventActionConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 业务类型，如 EXP_CLAIM / PURCHASE_ORDER_REVIEW */
    private String bizType;

    /** Spring Bean 名称（实现 WfBizAction 接口） */
    private String actionBean;

    /** 触发时机：APPROVED（仅审批通过）/ REJECTED（仅驳回）/ BOTH（两者均触发，默认） */
    private String triggerWhen;

    /** 同一 bizType 内的执行顺序（升序） */
    private Integer sortOrder;

    /** 是否启用：1=是 0=否 */
    private Integer enabled;

    /** 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
