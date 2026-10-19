package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.AssetDeprDetail;
import org.apache.ibatis.annotations.Mapper;

/** 资产折旧明细（带 tenant_id） */
@Mapper
public interface AssetDeprDetailMapper extends BaseMapper<AssetDeprDetail> {
}
