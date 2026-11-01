// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reviewer")
public class Reviewer extends TenantBase {

    private Long userId;
    private String reviewerCode;
    private String reviewerName;
    private Long reviewerGroupId;
    private String skillTags;
    private Integer maxDailyTasks;
    private Integer currentPending;
    private Integer completedToday;
    private BigDecimal avgReviewTimeMin;
    private BigDecimal accuracyRate;
    /** 1-在岗 0-离岗 */
    private Integer status;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
}