// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgException;
import org.apache.ibatis.annotations.Mapper;

/** 影像异常 */
@Mapper
public interface ImgExceptionMapper extends BaseMapper<ImgException> {
}
