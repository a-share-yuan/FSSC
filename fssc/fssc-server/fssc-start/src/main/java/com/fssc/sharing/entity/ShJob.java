// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 共享作业岗位（需求 8.1.1：岗位设置，岗位需指定作业项）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_job")
public class ShJob extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 岗位编码 */
    private String jobCode;

    /** 岗位名称 */
    private String jobName;

    /** 作业项（审核/记账/支付处理等，逗号分隔） */
    private String workItems;

    /** 状态 NORMAL/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

}
