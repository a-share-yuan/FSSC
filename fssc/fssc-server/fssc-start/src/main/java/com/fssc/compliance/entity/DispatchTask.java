// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dispatch_task")
public class DispatchTask extends TenantBase {

    private String taskCode;
    private Long billId;
    private String billType;
    private String expenseType;
    private BigDecimal amount;
    private Long legalEntityId;
    private BigDecimal complexityScore;
    private Long matchedRuleId;
    private Long assignedGroupId;
    private Long assignedReviewerId;
    private String dispatchStrategy;
    private String status;
    private LocalDateTime assignedAt;
    private LocalDateTime dueTime;
    private LocalDateTime upgradedAt;
    private String upgradeReason;

    // P1-5 共享运营实质化：SLA + 信用 + 质检 + 满意度
    private LocalDateTime slaDueTime;
    private String slaStatus;
    private Integer creditScore;
    private Integer qualityScore;
    private Integer satisfactionScore;
}