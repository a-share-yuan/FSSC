// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdPeriod;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 主数据：MdPeriod（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdPeriodMapper extends BaseMapper<MdPeriod> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `md_period` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
