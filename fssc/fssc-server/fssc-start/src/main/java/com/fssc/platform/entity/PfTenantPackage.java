// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户订购的套餐（pf_tenant_package）。
 * 注意：ref_tenant_id 是业务外键（哪个租户订购的），不是隔离字段 —— 命名约定见 01-platform.sql 头注释。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_tenant_package")
public class PfTenantPackage extends PlatformBaseEntity {

    /** 租户ID（业务外键，非隔离字段） */
    private Long refTenantId;
    private Long packageId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** ACTIVE生效中/EXPIRED已过期/TERMINATED已终止 */
    private String status;
    private String remark;
}
