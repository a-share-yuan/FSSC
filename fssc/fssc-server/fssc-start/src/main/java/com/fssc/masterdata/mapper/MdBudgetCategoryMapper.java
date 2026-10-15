package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBudgetCategory;
import org.apache.ibatis.annotations.Mapper;

/** 预算类别（需求 3.3） */
@Mapper
public interface MdBudgetCategoryMapper extends BaseMapper<MdBudgetCategory> {
}
