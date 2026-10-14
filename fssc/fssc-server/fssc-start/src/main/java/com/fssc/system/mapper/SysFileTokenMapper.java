// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysFileToken;
import org.apache.ibatis.annotations.Mapper;

/** 文件下载一次性令牌 */
@Mapper
public interface SysFileTokenMapper extends BaseMapper<SysFileToken> {
}
