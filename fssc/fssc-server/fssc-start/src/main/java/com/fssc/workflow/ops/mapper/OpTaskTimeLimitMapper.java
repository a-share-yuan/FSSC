// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.ops.entity.OpTaskTimeLimit;
import org.apache.ibatis.annotations.Mapper;

/** G3任务优先级与时限落账（带 tenant_id） */
@Mapper
public interface OpTaskTimeLimitMapper extends BaseMapper<OpTaskTimeLimit> {
}
