// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_period（带 tenant_id，租户拦截器自动隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_period")
public class MdPeriod extends TenantAuditBase {

    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private LocalDate beginDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime closeTime;
    private Long closeBy;
}
