package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程抄送（wf_cc，带 tenant_id）。
 * 无 del_flag/update_by，按真实列独立声明。
 */
@Data
@TableName("wf_cc")
public class WfCc implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String procInstId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private Long ccUserId;
    /** 来源节点 */
    private String fromNode;
    /** 是否已读 */
    private Integer isRead;
    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
