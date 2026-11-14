package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdBankAccount;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdBankAccount（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdBankAccountMapper extends BaseMapper<MdBankAccount> {
}
