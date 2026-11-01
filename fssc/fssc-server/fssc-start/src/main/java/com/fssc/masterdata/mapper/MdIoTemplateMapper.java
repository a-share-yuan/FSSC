// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdIoTemplate;
import org.apache.ibatis.annotations.Mapper;

/** MdIoTemplate Mapper（统一导入导出引擎）。 */
@Mapper
public interface MdIoTemplateMapper extends BaseMapper<MdIoTemplate> {
}
