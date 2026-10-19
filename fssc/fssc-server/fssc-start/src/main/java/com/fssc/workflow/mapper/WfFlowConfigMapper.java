package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfFlowConfig;
import org.apache.ibatis.annotations.Mapper;

/** 流程配置（wf_flow_config） */
@Mapper
public interface WfFlowConfigMapper extends BaseMapper<WfFlowConfig> {
}
