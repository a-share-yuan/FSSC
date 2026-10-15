package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdSupplierContact;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：供应商联系人(N)(41号审查 P1-8,租户拦截器自动隔离) */
@Mapper
public interface MdSupplierContactMapper extends BaseMapper<MdSupplierContact> {
}
