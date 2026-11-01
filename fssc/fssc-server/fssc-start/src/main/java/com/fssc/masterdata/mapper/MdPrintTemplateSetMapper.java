// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdPrintTemplateSet;
import org.apache.ibatis.annotations.Mapper;

/**
 * MdPrintTemplateSet Mapper（打印/套打引擎）。
 */
@Mapper
public interface MdPrintTemplateSetMapper extends BaseMapper<MdPrintTemplateSet> {
}
