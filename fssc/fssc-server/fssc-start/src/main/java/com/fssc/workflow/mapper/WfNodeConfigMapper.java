// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfNodeConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 流程节点审批人配置（带 tenant_id） */
@Mapper
public interface WfNodeConfigMapper extends BaseMapper<WfNodeConfig> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `wf_node_config` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
