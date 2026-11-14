package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBank;
import org.apache.ibatis.annotations.Mapper;

/** 金融机构（银行主数据） */
@Mapper
public interface MdBankMapper extends BaseMapper<MdBank> {
}
