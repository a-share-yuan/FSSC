package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvInvoiceGlobal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发票域：InvInvoiceGlobal（跨租户全局查重索引表，无 tenant_id）。
 * 已在 FsscTenantLineHandler 忽略名单，跨租户读写须显式用 TenantContext.runIgnoreTenant 包裹。
 */
@Mapper
public interface InvInvoiceGlobalMapper extends BaseMapper<InvInvoiceGlobal> {
}
