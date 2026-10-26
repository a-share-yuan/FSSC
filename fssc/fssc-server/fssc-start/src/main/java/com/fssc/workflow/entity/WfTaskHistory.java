// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批历史（wf_task_history，带 tenant_id）。
 * 【审计表-不可删】无 del_flag、无 create_by/update_by，按真实列独立声明；Mapper 不提供 delete。
 */
@Data
@TableName("wf_task_history")
public class WfTaskHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;

    private String procInstId;
    private String taskId;
    private String nodeKey;
    private String nodeName;
    private String bizType;
    private Long bizId;
    /** SUBMIT提交/APPROVE同意/REJECT驳回/TRANSFER转办/ADDSIGN加签/CANCEL撤销/URGE催办/AUTO_PASS自动通过/TERMINATE终止 */
    private String action;
    private Long operUserId;
    /** 操作人姓名（冗余，防人员离职后查不到） */
    private String operUserName;
    private String operDeptName;
    /** 目标人（转办/加签时） */
    private Long targetUserId;
    private String comment;
    /** 意见附件 ID，逗号分隔 */
    private String attachIds;
    /** 本节点停留时长（毫秒） */
    private Long duration;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime operTime;

    /**
     * 展示层附加位（<b>非表列</b>，D-DEC-067/080）：本行是否「实例级定论行」
     * （action ∈ APPROVED/REJECTED/WITHDRAW/CANCEL，见 {@code WfQueryService.INSTANCE_LEVEL_ACTIONS}）。
     *
     * <p>定论行原先直接抄末位审批人的意见全文，与真实办理行只差一个 action、时间还早 1 秒，
     * 联查把它原样透出时看起来像"同一段签署内容留了两次"（12-18 #651/#652、12-21 #655/#656）。
     * 写侧判据已改为只记实例级结论；本字段让前端<b>无需再按 action 白名单自行推断</b>，
     * 可把定论行归到"整单结论"栏而不是"第 N 节办理"栏。由读数侧（联查）赋值，写入数据库不涉及。
     */
    @TableField(exist = false)
    private Boolean instanceLevel;
}
