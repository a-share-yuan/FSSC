// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务流程（wf_business_flow）：单据审批流节点序列（来自需求文档"业务流程"表）。
 * node_json: ["填制单据","预算控制(检查并占用)","业务审批","财务处理",...]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_business_flow")
public class WfBusinessFlow extends TenantAuditBase {
    private static final long serialVersionUID = 1L;

    /** 流程编码 */
    private String flowCode;
    /** 流程名称 */
    private String flowName;
    /** 业务单据类型 MEETING/TRAVEL/TRAIN/CLAIM/CLAIM_TRAVEL/PREPAY/SETTLE/AGENCY/AIR/LOAN/REPAY/CONTRACT */
    private String bizType;
    /** 关联流程模型编码 */
    private String modelKey;
    /** 业务流程节点序列 JSON */
    private String nodeJson;
    /** 状态 NORMAL/DISABLED */
    private String status;
    /** 备注 */
    private String remark;
}
