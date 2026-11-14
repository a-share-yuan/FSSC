package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.OpSlaConfig;
import org.apache.ibatis.annotations.Mapper;

/** SLA 配置 */
@Mapper
public interface OpSlaConfigMapper extends BaseMapper<OpSlaConfig> {
}
