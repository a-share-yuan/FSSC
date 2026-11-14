package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdMatchTolerance;
import org.apache.ibatis.annotations.Mapper;

/** 三单匹配容差配置 Mapper */
@Mapper
public interface MdMatchToleranceMapper extends BaseMapper<MdMatchTolerance> {
}