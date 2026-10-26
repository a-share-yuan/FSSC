// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpBarcode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpBarcodeMapper extends BaseMapper<OpBarcode> {
}
