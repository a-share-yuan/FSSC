// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.framework.job.OpJobHeartbeat;
import org.apache.ibatis.annotations.Mapper;

/** 定时任务心跳（全局表，租户过滤已在拦截器忽略名单处理） */
@Mapper
public interface OpJobHeartbeatMapper extends BaseMapper<OpJobHeartbeat> {
}
