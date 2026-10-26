// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 租户主表 */
@Mapper
public interface PfTenantMapper extends BaseMapper<PfTenant> {

    /** 按租户编码查询（登录时识别企业） */
    @Select("select * from pf_tenant where tenant_code = #{code} and del_flag = 0")
    PfTenant findByCode(@Param("code") String code);
}
