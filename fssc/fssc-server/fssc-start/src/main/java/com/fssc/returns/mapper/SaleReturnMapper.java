package com.fssc.returns.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.returns.entity.SaleReturn;
import org.apache.ibatis.annotations.Mapper;

/** 销售退货单 Mapper（无 XML，全部走 BaseMapper + Lambda 条件构造器）。 */
@Mapper
public interface SaleReturnMapper extends BaseMapper<SaleReturn> {
}
