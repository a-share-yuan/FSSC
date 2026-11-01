// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdFormField;
import org.apache.ibatis.annotations.Mapper;

/** MdFormField Mapper（元数据驱动）。 */
@Mapper
public interface MdFormFieldMapper extends BaseMapper<MdFormField> {
}
