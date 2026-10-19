package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfNodeConfig;
import org.apache.ibatis.annotations.Mapper;

/** 流程节点审批人配置（带 tenant_id） */
@Mapper
public interface WfNodeConfigMapper extends BaseMapper<WfNodeConfig> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `wf_node_config` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
