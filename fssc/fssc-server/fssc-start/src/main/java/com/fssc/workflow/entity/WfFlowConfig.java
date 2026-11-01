// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程配置（wf_flow_config）：需求31 附录 审批流程配置建议。
 * cfg_type: THRESHOLD 金额阈值 / TIMEOUT 超时处理 / ACTION 审批动作 / INTEGRATION 外部集成
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_flow_config")
public class WfFlowConfig extends TenantAuditBase {
    private static final long serialVersionUID = 1L;

    /** 配置类型 THRESHOLD/TIMEOUT/ACTION/INTEGRATION */
    private String cfgType;
    /** 配置编码 */
    private String cfgCode;
    /** 配置名称 */
    private String cfgName;
    /** 配置内容 JSON */
    private String cfgJson;
    /** 排序 */
    private Integer sort;
    /** 状态 NORMAL/DISABLED */
    private String status;
    /** 备注 */
    private String remark;
}
