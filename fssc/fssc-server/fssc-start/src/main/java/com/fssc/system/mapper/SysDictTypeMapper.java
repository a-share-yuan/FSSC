// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysDictType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 租户字典类型（带 tenant_id） */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `sys_dict_type` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
