// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.SupplierReconDetail;
import org.apache.ibatis.annotations.Mapper;

/** 供应商对账单逐笔明细 Mapper */
@Mapper
public interface SupplierReconDetailMapper extends BaseMapper<SupplierReconDetail> {
}
