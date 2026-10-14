// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.SupplierReconDiff;
import org.apache.ibatis.annotations.Mapper;

/** 供应商对账差异调节表 Mapper */
@Mapper
public interface SupplierReconDiffMapper extends BaseMapper<SupplierReconDiff> {
}
