package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdInvoiceRule;
import org.apache.ibatis.annotations.Mapper;

/** 主数据：MdInvoiceRule（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdInvoiceRuleMapper extends BaseMapper<MdInvoiceRule> {
}