// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * G3（2026-09-24）自动分配日志（op_dispatch_log，带 tenant_id）。
 *
 * <p>每次自动分配 / 规则命中 / 策略决策落一条，回答"这单为什么派给了他"：
 * 命中了哪条规则、采用了哪条策略、候选有哪些（快照）、为什么选他。
 *
 * <p><b>只增不改</b>：日志表不提供更新入口，写失败不影响主流程（分配本身已落库），
 * 但必须 {@code log.error} 留下可见物，不得静默（方案 §二 F2 的教训）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_dispatch_log")
public class OpDispatchLog extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private String procInstId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String modelKey;
    private String nodeKey;
    private String nodeName;
    /** 分配方式：RULE规则命中 / STRATEGY策略选人 / PICK抢单池 / MANUAL人工指定 */
    private String dispatchMode;
    private Long matchedRuleId;
    private String matchedRuleCode;
    private String matchedRuleName;
    private Long policyId;
    /** 采用的分配策略 */
    private String strategy;
    private Long targetGroupId;
    /** 候选快照（用户ID:姓名:在手量:预计耗时），决策依据留证 */
    private String candidateSnapshot;
    private Long chosenUserId;
    private String chosenUserName;
    /** 决策说明：为什么选他 / 为什么不选人 */
    private String decisionReason;
    /** 本次分配是否成功 */
    private Integer success;
}
