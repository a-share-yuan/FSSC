// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdSupplier;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdSupplier（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdSupplierMapper extends BaseMapper<MdSupplier> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `md_supplier` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
