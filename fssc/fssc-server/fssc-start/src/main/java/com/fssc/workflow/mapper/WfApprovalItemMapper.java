// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfApprovalItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 流程节点审批项（wf_approval_item，带 tenant_id） */
@Mapper
public interface WfApprovalItemMapper extends BaseMapper<WfApprovalItem> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键，与 WfNodeConfigMapper 同一规则） */
    @Delete("DELETE FROM `wf_approval_item` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}