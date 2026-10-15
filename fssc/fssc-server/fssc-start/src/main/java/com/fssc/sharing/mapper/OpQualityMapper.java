package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpQuality;
import org.apache.ibatis.annotations.Mapper;

/** 共享质检记录（带 tenant_id） */
@Mapper
public interface OpQualityMapper extends BaseMapper<OpQuality> {
}
