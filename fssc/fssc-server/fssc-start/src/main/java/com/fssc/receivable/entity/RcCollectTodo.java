// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 催收待办（rc_collect_todo，带 tenant_id，逻辑删除 del_flag）。
 * 由逾期应收自动/手工生成，记录联系人信息与逾期天数，供催收处理。
 * 状态：DOING 催收中 / RESOLVED 已解决。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rc_collect_todo")
public class RcCollectTodo extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 关联应收单id */
    private Long receivableId;
    /** 往来单位（crm_customer.id） */
    private Long customerId;
    /** 应收单据号 */
    private String billNo;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactPhone;
    /** 到期日 */
    private LocalDate dueDate;
    /** 逾期天数 */
    private Integer overdueDays;
    /** 备注 */
    private String note;
    /** DOING 催收中 / RESOLVED 已解决 */
    private String status;
    /** 所属公司（多分子公司审批路由 V1.3.17） */
    private Long companyId;

    /**
     * D-DEC-410 乙：读侧复核提示位（非表列，只出参不进 SQL）。
     * 已办结且发起人与办结人同一时为提示文案，其余为 null。
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String multiRoleWarning;
}