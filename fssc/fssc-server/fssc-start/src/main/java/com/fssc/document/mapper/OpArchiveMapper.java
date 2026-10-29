// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.document.entity.OpArchive;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 电子会计档案（带 tenant_id） */
@Mapper
public interface OpArchiveMapper extends BaseMapper<OpArchive> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `op_archive` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
