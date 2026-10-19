package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdContractType;
import org.apache.ibatis.annotations.Mapper;

/** 合同类型（需求 5.1） */
@Mapper
public interface MdContractTypeMapper extends BaseMapper<MdContractType> {
}
