package com.fssc.treasury.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.treasury.entity.TrTransfer;
import org.apache.ibatis.annotations.Mapper;

/** 资金调拨 Mapper */
@Mapper
public interface TrTransferMapper extends BaseMapper<TrTransfer> {
}