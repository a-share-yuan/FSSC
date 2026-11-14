package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBill;
import org.apache.ibatis.annotations.Mapper;

/** 报销单据主表（带 tenant_id） */
@Mapper
public interface ExpBillMapper extends BaseMapper<ExpBill> {
}
