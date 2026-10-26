// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfPackageFeature;
import org.apache.ibatis.annotations.Mapper;

/** 套餐功能项 */
@Mapper
public interface PfPackageFeatureMapper extends BaseMapper<PfPackageFeature> {
}
