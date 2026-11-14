package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdItemCashMap;
import org.apache.ibatis.annotations.Mapper;

/** 费用类型与现金流量码映射 */
@Mapper
public interface MdItemCashMapMapper extends BaseMapper<MdItemCashMap> {
}
