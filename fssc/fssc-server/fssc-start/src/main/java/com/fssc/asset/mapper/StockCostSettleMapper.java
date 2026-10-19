package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.StockCostSettle;
import org.apache.ibatis.annotations.Mapper;

/** 存货成本结转批次（带 tenant_id） */
@Mapper
public interface StockCostSettleMapper extends BaseMapper<StockCostSettle> {
}
