package com.fssc.workflow.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import org.apache.ibatis.annotations.Mapper;

/** G3（2026-09-24）共享作业分配策略与双时限配置（带 tenant_id） */
@Mapper
public interface OpDispatchPolicyMapper extends BaseMapper<OpDispatchPolicy> {
}
