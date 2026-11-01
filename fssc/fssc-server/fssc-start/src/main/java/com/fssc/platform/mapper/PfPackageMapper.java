// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfPackage;
import org.apache.ibatis.annotations.Mapper;

/** 套餐定义 */
@Mapper
public interface PfPackageMapper extends BaseMapper<PfPackage> {
}
