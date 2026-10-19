package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvInvoice;
import org.apache.ibatis.annotations.Mapper;

/** 发票域：InvInvoice（带 tenant_id） */
@Mapper
public interface InvInvoiceMapper extends BaseMapper<InvInvoice> {
}
