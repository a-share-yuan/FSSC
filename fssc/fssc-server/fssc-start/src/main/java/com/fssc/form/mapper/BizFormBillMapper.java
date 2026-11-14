package com.fssc.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.form.entity.BizFormBill;
import org.apache.ibatis.annotations.Mapper;

/** 可配置表单通用单据 Mapper */
@Mapper
public interface BizFormBillMapper extends BaseMapper<BizFormBill> {
}
