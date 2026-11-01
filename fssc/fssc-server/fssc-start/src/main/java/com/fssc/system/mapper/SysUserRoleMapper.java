// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 租户用户-角色（带 tenant_id） */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /** 用户拥有的角色ID（租户条件由拦截器注入） */
    @Select("select role_id from sys_user_role where user_id = #{userId}")
    List<Long> selectRoleIdsByUser(@Param("userId") Long userId);
}
