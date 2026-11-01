// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvBillInvoice;
import org.apache.ibatis.annotations.Mapper;

/** 发票域：InvBillInvoice（带 tenant_id） */
@Mapper
public interface InvBillInvoiceMapper extends BaseMapper<InvBillInvoice> {
}
