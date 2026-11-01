// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SodRoleConflict;
import org.apache.ibatis.annotations.Mapper;

/** SoD 不相容角色互斥规则（全局表，租户过滤已在拦截器忽略名单处理） */
@Mapper
public interface SodRoleConflictMapper extends BaseMapper<SodRoleConflict> {
}
