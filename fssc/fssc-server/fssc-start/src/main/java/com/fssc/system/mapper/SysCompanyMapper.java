// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysCompany;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公司（纳税主体） */
@Mapper
public interface SysCompanyMapper extends BaseMapper<SysCompany> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `sys_company` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
