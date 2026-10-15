package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdIncomeExpense;
import org.apache.ibatis.annotations.Mapper;

/** 收支设置（资金计划）（需求 2.21） */
@Mapper
public interface MdIncomeExpenseMapper extends BaseMapper<MdIncomeExpense> {
}
