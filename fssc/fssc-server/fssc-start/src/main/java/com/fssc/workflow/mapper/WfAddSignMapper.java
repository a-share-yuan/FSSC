// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfAddSign;
import org.apache.ibatis.annotations.Mapper;

/** 流程加签（前/后加签，带 tenant_id） */
@Mapper
public interface WfAddSignMapper extends BaseMapper<WfAddSign> {
}
