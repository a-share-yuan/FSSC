// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

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
 * 单据状态变更轨迹（exp_bill_trace，带 tenant_id）。
 * 【审计表-不可删】无 del_flag/create_by，按真实列独立声明；Mapper 不提供 delete。
 *
 * @deprecated RA-TRC-03 乙：本表在全库没有写入位置（{@code BillTraceService.record} 零调用），
 * 联查 traces 段已改读 {@code wf_task_history}。表结构与既有行按「保留全部生成数据」规则不删除，仅停用。
 */
@Deprecated
@Data
@TableName("exp_bill_trace")
public class ExpBillTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private String fromStatus;
    private String toStatus;
    /** SAVE/SUBMIT/APPROVE/REJECT/AUDIT/AUDIT_BACK/PAY/ACCOUNT/CANCEL */
    private String action;
    private Long operUserId;
    private String operUserName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime operTime;
}
