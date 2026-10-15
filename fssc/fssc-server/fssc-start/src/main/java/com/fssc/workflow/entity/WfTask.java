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
 * 待办任务业务视图（wf_task，带 tenant_id）。待办列表只查这张表（不与 ACT_RU_TASK 联查）。
 * 审计列不齐（无 create_by/update_by/del_flag），按真实列独立声明。
 */
@Data
@TableName("wf_task")
public class WfTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;

    /** Flowable 任务 ID */
    private String taskId;
    /** 流程实例 ID */
    private String procInstId;
    private String modelKey;
    private String nodeKey;
    private String nodeName;
    private String bizType;
    private Long bizId;
    private String bizNo;
    /** 任务标题 */
    private String title;
    /** 单据金额 */
    private BigDecimal amount;
    /** 处理人用户 ID（已签收） */
    private Long assigneeId;
    /** 候选人用户 ID，逗号包裹如 ,1,2,3, */
    private String candidateIds;
    private Long startUserId;
    /** 任务创建时间 */
    private LocalDateTime createTime;
    /** 到期时间 */
    private LocalDateTime dueTime;
    /** 被催办次数 */
    private Integer urgedCount;
    /** PENDING待办/DONE已办/CANCELLED已取消 */
    private String status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
