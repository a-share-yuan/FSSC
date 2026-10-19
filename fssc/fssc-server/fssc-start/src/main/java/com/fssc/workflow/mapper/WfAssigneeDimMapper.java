package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfAssigneeDim;
import org.apache.ibatis.annotations.Mapper;

/** 参与人方案取人维度（wf_assignee_dim，带 tenant_id）：tenant_id 由 MyBatis-Plus 租户拦截器注入 */
@Mapper
public interface WfAssigneeDimMapper extends BaseMapper<WfAssigneeDim> {
}
