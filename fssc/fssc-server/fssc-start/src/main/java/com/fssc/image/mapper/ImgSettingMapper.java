// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgSetting;
import org.apache.ibatis.annotations.Mapper;

/** 影像系统设置（需求 6.3） */
@Mapper
public interface ImgSettingMapper extends BaseMapper<ImgSetting> {
}
