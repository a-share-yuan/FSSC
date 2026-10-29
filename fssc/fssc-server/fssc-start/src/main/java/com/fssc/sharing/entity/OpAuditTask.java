// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 共享运营派工单（op_audit_task，带 tenant_id）。审批通过自动入池。 */
@Data
@TableName("op_audit_task")
public class OpAuditTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private String billNo;
    private String billType;
    private String title;
    private BigDecimal amount;
    private Long deptId;
    private Long applyUserId;
    /** 优先级 1-10，默认 5 */
    private Integer priority;
    /** AUDIT审核 / PAY付款 / BOOKKEEP记账归档 */
    private String taskType;
    private Long assigneeId;
    /** WAITING待认领 / RECEIVED已认领 / FINISHED已完成 */
    private String poolStatus;
    /** 作业链序号（1审核/2付款/3记账） */
    private Integer chainSeq;
    /** 作业链总环节数 */
    private Integer chainTotal;
    /** 是否可认领 Y/N（前序未完成则 N，前序完成自动激活为 Y） */
    private String activeFlag;
    private LocalDateTime slaTime;
    private LocalDateTime receiveTime;
    private LocalDateTime finishTime;
    private String remark;

    /** 关联单据状态（仅出参，manage 分页时批量填充；UI「单据状态」列中文映射） */
    @TableField(exist = false)
    private String billStatus;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
