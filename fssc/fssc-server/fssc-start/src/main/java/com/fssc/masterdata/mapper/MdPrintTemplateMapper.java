// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdPrintTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * MdPrintTemplate Mapper（V1.4.81 打印/套打引擎）。
 */
@Mapper
public interface MdPrintTemplateMapper extends BaseMapper<MdPrintTemplate> {
}
