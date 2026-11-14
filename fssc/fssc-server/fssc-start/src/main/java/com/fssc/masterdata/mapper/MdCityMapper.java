package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdCity;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdCity（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdCityMapper extends BaseMapper<MdCity> {
}
