package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfBusinessFlow;
import org.apache.ibatis.annotations.Mapper;

/** 业务流程（wf_business_flow） */
@Mapper
public interface WfBusinessFlowMapper extends BaseMapper<WfBusinessFlow> {
}
