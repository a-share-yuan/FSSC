// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysPost;
import org.apache.ibatis.annotations.Mapper;

/** 岗位 */
@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `sys_post` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
