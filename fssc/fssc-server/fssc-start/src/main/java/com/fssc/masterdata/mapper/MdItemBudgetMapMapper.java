package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdItemBudgetMap;
import org.apache.ibatis.annotations.Mapper;

/** 费用类型与预算科目映射 */
@Mapper
public interface MdItemBudgetMapMapper extends BaseMapper<MdItemBudgetMap> {
}
