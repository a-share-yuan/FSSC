package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.OpSlaBreach;
import org.apache.ibatis.annotations.Mapper;

/** SLA 违约记录 */
@Mapper
public interface OpSlaBreachMapper extends BaseMapper<OpSlaBreach> {
}
