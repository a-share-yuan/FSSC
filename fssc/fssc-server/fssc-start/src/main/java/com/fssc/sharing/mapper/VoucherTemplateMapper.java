// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.VoucherTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 凭证模板 */
@Mapper
public interface VoucherTemplateMapper extends BaseMapper<VoucherTemplate> {
}