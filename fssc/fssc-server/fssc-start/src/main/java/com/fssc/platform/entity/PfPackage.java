// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 套餐定义（pf_package） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_package")
public class PfPackage extends PlatformBaseEntity {

    private String packageCode;
    private String packageName;
    /** TRIAL试用/STANDARD标准/PRO专业/ENTERPRISE旗舰/CUSTOM定制 */
    private String packageType;
    private BigDecimal priceMonth;
    private BigDecimal priceYear;
    /** 用户数上限，0 不限 */
    private Integer maxUser;
    private Integer maxCompany;
    private Long maxStorageMb;
    private Long maxApiDaily;
    private Long maxBillMonth;
    /** 1 启用 0 禁用 */
    private Integer status;
    private Integer sort;
    private String remark;
}
