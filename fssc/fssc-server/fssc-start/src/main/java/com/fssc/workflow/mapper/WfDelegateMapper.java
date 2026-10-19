package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfDelegate;
import org.apache.ibatis.annotations.Mapper;

/** 流程委托代理（带 tenant_id） */
@Mapper
public interface WfDelegateMapper extends BaseMapper<WfDelegate> {
}
