package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpTravel;
import org.apache.ibatis.annotations.Mapper;

/** 单据细分：ExpTravel（带 tenant_id） */
@Mapper
public interface ExpTravelMapper extends BaseMapper<ExpTravel> {
}
