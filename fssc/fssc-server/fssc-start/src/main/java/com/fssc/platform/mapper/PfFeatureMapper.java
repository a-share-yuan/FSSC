// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfFeature;
import org.apache.ibatis.annotations.Mapper;

/** 功能点定义 */
@Mapper
public interface PfFeatureMapper extends BaseMapper<PfFeature> {
}
