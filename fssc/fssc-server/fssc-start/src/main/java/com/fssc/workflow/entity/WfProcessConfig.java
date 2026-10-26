// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 单据类型与流程绑定配置（wf_process_config，带 tenant_id）。
 * 禁止硬编码 processKey：按 biz_type + priority 从大到小取第一条命中的规则。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_config")
public class WfProcessConfig extends TenantAuditBase {

    /** EXP_APPLY/EXP_CLAIM/EXP_LOAN/EXP_REPAY/EXP_PAY/BG_ADJUST */
    private String bizType;
    /** 单据子类型（TRAVEL/SETTLE/PREPAY…）：空=通配任意子类型；非空=精确命中优先于通配（V1.3.19） */
    private String subType;
    private String configName;
    /** 绑定的流程定义 key */
    private String modelKey;
    /** 适用公司，空表示全租户 */
    private Long companyId;
    /** 适用部门，空表示不限 */
    private Long deptId;
    /** 金额下限（含），空表示不限 */
    private BigDecimal amountMin;
    /** 金额上限（不含），空表示不限 */
    private BigDecimal amountMax;
    /** 额外条件表达式（SpEL），如 #bill.urgent == true（ 死列：WfProcessConfigService.resolveModelKey 只按
     *  bizType/subType/amount/companyId 命中，从不解析本列——配置真值以代码为准，裁决件见 bpmn-repo/drafts/ai-rights/） */
    private String conditionExpr;
    /** 优先级，数值大者优先命中 */
    private Integer priority;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
