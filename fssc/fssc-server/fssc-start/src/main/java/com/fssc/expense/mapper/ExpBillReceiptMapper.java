package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillReceipt;
import org.apache.ibatis.annotations.Mapper;

/** expense 域：ExpBillReceipt（资金流入侧收款事实，带 tenant_id） */
@Mapper
public interface ExpBillReceiptMapper extends BaseMapper<ExpBillReceipt> {
}
