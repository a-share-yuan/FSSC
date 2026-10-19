// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_city（带 tenant_id，租户拦截器自动隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_city")
public class MdCity extends TenantAuditBase {

    private String cityCode;
    private String cityName;
    private String province;
    private String cityLevel;
    private Integer isAbroad;
    private Integer sort;
    private String status;
}
