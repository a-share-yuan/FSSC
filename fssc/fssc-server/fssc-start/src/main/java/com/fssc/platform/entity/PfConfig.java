// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台全局配置（pf_config）。
 * 【平台表-无 tenant_id】与租户配置 sys_config 物理分表，防止租户改到平台全局参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_config")
public class PfConfig extends PlatformBaseEntity {

    /** 参数键（全局唯一） */
    private String configKey;
    private String configValue;
    private String configName;
    /** DEFAULT/LOGIN/NOTIFY/SECURITY */
    private String configGroup;
    /** STRING/NUMBER/BOOLEAN/JSON */
    private String valueType;
    private String remark;
}
