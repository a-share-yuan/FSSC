// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.ops.entity.OpDispatchLog;
import org.apache.ibatis.annotations.Mapper;

/** G3（2026-09-24）自动分配日志（带 tenant_id，只增不改） */
@Mapper
public interface OpDispatchLogMapper extends BaseMapper<OpDispatchLog> {
}
