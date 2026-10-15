package com.fssc.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.asset.entity.FaCerRequest;
import org.apache.ibatis.annotations.Mapper;

/** 资本性支出申请（FA_CER）简化申请记录 */
@Mapper
public interface FaCerRequestMapper extends BaseMapper<FaCerRequest> {
}
