// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpPayBatchDetail;
import org.apache.ibatis.annotations.Mapper;

/** sharing 域：OpPayBatchDetail（带 tenant_id） */
@Mapper
public interface OpPayBatchDetailMapper extends BaseMapper<OpPayBatchDetail> {
}
