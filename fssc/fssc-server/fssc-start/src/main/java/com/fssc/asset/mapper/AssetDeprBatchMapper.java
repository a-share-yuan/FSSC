// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.AssetDeprBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetDeprBatchMapper extends BaseMapper<AssetDeprBatch> {
}