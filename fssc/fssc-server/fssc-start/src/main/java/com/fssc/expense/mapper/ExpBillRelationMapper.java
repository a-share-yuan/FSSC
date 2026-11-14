package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillRelation;
import org.apache.ibatis.annotations.Mapper;

/** 单据细分：ExpBillRelation（带 tenant_id） */
@Mapper
public interface ExpBillRelationMapper extends BaseMapper<ExpBillRelation> {
}
