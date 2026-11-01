// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.ShJob;
import org.apache.ibatis.annotations.Mapper;

/** 共享作业岗位（需求 8.1.1：岗位设置，岗位需指定作业项） */
@Mapper
public interface ShJobMapper extends BaseMapper<ShJob> {
}
