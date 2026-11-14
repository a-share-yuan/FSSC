package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdFormConfig;
import org.apache.ibatis.annotations.Mapper;

/** 表单详情配置（配置各业务表单的字段/布局/规则） */
@Mapper
public interface MdFormConfigMapper extends BaseMapper<MdFormConfig> {
}
