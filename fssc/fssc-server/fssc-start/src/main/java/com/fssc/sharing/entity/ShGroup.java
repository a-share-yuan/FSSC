// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 共享业务分组（.2：分组管理，按业务类型/区域划分，负责人+成员数上下限+任务提取方式）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_group")
public class ShGroup extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 分组编码 */
    private String groupCode;

    /** 分组名称 */
    private String groupName;

    /** 业务类型 EXPENSE费用组/FUND资金组/AR应收组/AP应付组 */
    private String bizType;

    /** 负责人用户ID */
    private Long leaderId;

    /** 负责人姓名（非数据库字段，查询时关联填充） */
    @TableField(exist = false)
    private String leaderName;

    /** 成员任务数下限 */
    private Integer memberMin;

    /** 成员任务数上限 */
    private Integer memberMax;

    /** 任务提取方式 MANUAL手动/SYSTEM系统 */
    private String taskMode;

    /** 状态 NORMAL/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    // 合并自 reviewer_group（审核组）

    /** 组类型：BY_TYPE/BY_AMOUNT/BY_LEGAL/BY_REGION */
    private String groupType;

    /** 组配置JSON */
    private String groupConfig;

    /** 成员数 */
    private Integer memberCount;

    /** 是否自动派单：1-是 0-否 */
    private Integer isAutoDispatch;

    /** 优先级 */
    private Integer priority;
}
