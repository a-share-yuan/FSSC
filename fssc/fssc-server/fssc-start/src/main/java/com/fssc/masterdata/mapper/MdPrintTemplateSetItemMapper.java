// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdPrintTemplateSetItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * MdPrintTemplateSetItem Mapper（打印/套打引擎）。
 */
@Mapper
public interface MdPrintTemplateSetItemMapper extends BaseMapper<MdPrintTemplateSetItem> {
}
