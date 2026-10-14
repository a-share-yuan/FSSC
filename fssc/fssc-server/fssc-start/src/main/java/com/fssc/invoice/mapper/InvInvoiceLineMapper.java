// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvInvoiceLine;
import org.apache.ibatis.annotations.Mapper;

/** 发票明细行（带 tenant_id） */
@Mapper
public interface InvInvoiceLineMapper extends BaseMapper<InvInvoiceLine> {
}
