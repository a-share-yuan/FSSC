package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流程实例业务视图（wf_instance，带 tenant_id）。
 * 审计列不齐（无 create_by/update_by/del_flag），按真实列独立声明。
 */
@Data
@TableName("wf_instance")
public class WfInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;

    /** Flowable 流程实例 ID */
    private String procInstId;
    /** Flowable 流程定义 ID */
    private String procDefId;
    /** 流程定义 key */
    private String modelKey;
    private String bizType;
    private Long bizId;
    private String bizNo;
    /** 流程标题（列表展示） */
    private String title;
    /** 单据金额 */
    private BigDecimal amount;
    private Long startUserId;
    private Long startDeptId;
    /** 发起时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 耗时（毫秒） */
    private Long duration;
    /** 当前节点名称 */
    private String currentNode;
    /** 当前处理人姓名，多个逗号分隔 */
    private String currentAssignee;
    /** RUNNING/APPROVED/REJECTED/CANCELLED/TERMINATED */
    private String status;

    /** 当前在办 Flowable 任务 ID（wf_task 镜像回填，非表列） */
    @TableField(exist = false)
    private String currentTaskId;
    /** 当前可办理用户 ID，逗号分隔（assignee 或候选集，非表列） */
    @TableField(exist = false)
    private String currentAssigneeIds;
    /** 当前可办理人姓名，顿号分隔（非表列） */
    @TableField(exist = false)
    private String currentAssigneeNames;
    /** 当前节点已停留分钟数：最早那条 PENDING 镜像的 create_time 起算（非表列） */
    @TableField(exist = false)
    private Long stayMinutes;
    /** 是否已超出该节点 wf_node_config.timeout_hours；未配 SLA＝false（非表列） */
    @TableField(exist = false)
    private Boolean timeout;
    /** 超出节点 SLA 的小时数，未超时＝0（非表列） */
    @TableField(exist = false)
    private Long overdueHours;
    /**
     * 发起人姓名（nick_name，缺失回退登录号），由 {@code start_user_id} 反查（非表列）。
     * RA-D248b：列表「发起人」列一直在绑本字段而后端从不返回，故恒空。
     */
    @TableField(exist = false)
    private String initiatorName;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
