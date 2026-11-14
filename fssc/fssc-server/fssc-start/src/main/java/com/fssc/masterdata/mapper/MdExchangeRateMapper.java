package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdExchangeRate;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdExchangeRate（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdExchangeRateMapper extends BaseMapper<MdExchangeRate> {
}
