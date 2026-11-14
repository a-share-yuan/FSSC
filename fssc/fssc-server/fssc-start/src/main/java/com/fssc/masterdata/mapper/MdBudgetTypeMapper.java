package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetType;
import org.apache.ibatis.annotations.Mapper;

/** 预算类型（预算管理） */
@Mapper
public interface MdBudgetTypeMapper extends BaseMapper<MdBudgetType> {
}
