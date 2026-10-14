// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;

/** 通知公告（带 tenant_id） */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {
}
