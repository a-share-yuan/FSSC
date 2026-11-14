package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillLine;
import org.apache.ibatis.annotations.Mapper;

/** 报销单明细（带 tenant_id） */
@Mapper
public interface ExpBillLineMapper extends BaseMapper<ExpBillLine> {
}
