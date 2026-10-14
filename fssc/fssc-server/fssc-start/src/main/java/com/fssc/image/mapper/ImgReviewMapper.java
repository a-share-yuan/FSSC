// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgReview;
import org.apache.ibatis.annotations.Mapper;

/** 影像调阅评价 */
@Mapper
public interface ImgReviewMapper extends BaseMapper<ImgReview> {
}
