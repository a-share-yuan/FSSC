// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfModel;
import org.apache.ibatis.annotations.Mapper;

/** 流程模型（带 tenant_id） */
@Mapper
public interface WfModelMapper extends BaseMapper<WfModel> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `wf_model` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
