package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfProcessConfig;
import org.apache.ibatis.annotations.Mapper;

/** 单据类型流程绑定配置（带 tenant_id） */
@Mapper
public interface WfProcessConfigMapper extends BaseMapper<WfProcessConfig> {
}
