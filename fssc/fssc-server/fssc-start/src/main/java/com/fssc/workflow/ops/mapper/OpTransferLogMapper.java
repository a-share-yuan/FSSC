// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.ops.entity.OpTransferLog;
import org.apache.ibatis.annotations.Mapper;

/** G3流程批量移交日志（带 tenant_id，只增不改） */
@Mapper
public interface OpTransferLogMapper extends BaseMapper<OpTransferLog> {
}
