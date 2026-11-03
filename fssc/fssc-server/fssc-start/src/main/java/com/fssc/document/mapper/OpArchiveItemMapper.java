// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.document.entity.OpArchiveItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 案卷卷内件清单（带 tenant_id） */
@Mapper
public interface OpArchiveItemMapper extends BaseMapper<OpArchiveItem> {

    /** 物理删除（绕过 @TableLogic，供"移出卷"后释放件号位；卷本身不受影响） */
    @Delete("DELETE FROM `op_archive_item` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
