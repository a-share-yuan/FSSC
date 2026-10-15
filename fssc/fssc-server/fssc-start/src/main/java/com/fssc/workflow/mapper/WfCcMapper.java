package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfCc;
import org.apache.ibatis.annotations.Mapper;

/** 流程抄送（带 tenant_id） */
@Mapper
public interface WfCcMapper extends BaseMapper<WfCc> {
}
