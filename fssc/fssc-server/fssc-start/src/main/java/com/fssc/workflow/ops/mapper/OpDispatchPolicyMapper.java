// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import org.apache.ibatis.annotations.Mapper;

/** G3共享作业分配策略与双时限配置（带 tenant_id） */
@Mapper
public interface OpDispatchPolicyMapper extends BaseMapper<OpDispatchPolicy> {
}
