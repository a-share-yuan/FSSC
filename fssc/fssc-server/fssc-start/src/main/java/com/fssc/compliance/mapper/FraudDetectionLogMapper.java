package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.compliance.entity.FraudDetectionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FraudDetectionLogMapper extends BaseMapper<FraudDetectionLog> {
}
