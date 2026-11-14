package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillShare;
import org.apache.ibatis.annotations.Mapper;

/** 费用分摊（带 tenant_id） */
@Mapper
public interface ExpBillShareMapper extends BaseMapper<ExpBillShare> {
}
