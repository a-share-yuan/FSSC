// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.OcrRecord;
import org.apache.ibatis.annotations.Mapper;

/** OCR 识别结果落库 Mapper */
@Mapper
public interface OcrRecordMapper extends BaseMapper<OcrRecord> {
}
