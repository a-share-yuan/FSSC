// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.document.entity.ArchiveAttachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveAttachmentMapper extends BaseMapper<ArchiveAttachment> {
}