package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdLoanLimit;
import org.apache.ibatis.annotations.Mapper;

/** 借款额度（定义员工借款额度上限） */
@Mapper
public interface MdLoanLimitMapper extends BaseMapper<MdLoanLimit> {
}
