// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.MdAssetChangeLog;
import org.apache.ibatis.annotations.Mapper;

/** asset 域：MdAssetChangeLog（变动台账，带 tenant_id） */
@Mapper
public interface MdAssetChangeLogMapper extends BaseMapper<MdAssetChangeLog> {
}
