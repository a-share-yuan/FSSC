// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 套餐功能项（pf_package_feature）：套餐可用的功能开关 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_package_feature")
public class PfPackageFeature extends PlatformBaseEntity {

    private Long packageId;
    private String featureCode;
    /** 1 开启 0 关闭 */
    private Integer enabled;
}
