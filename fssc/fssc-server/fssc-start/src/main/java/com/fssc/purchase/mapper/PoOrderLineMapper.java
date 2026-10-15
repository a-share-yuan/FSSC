package com.fssc.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.purchase.entity.PoOrderLine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoOrderLineMapper extends BaseMapper<PoOrderLine> {
}
