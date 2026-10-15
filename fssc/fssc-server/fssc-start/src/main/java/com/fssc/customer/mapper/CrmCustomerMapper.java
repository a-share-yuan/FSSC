package com.fssc.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.customer.entity.CrmCustomer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrmCustomerMapper extends BaseMapper<CrmCustomer> {
}
