package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdPayee;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdPayee（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdPayeeMapper extends BaseMapper<MdPayee> {
}
