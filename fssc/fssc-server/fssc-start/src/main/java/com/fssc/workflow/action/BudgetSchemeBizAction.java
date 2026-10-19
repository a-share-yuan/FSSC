// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.budget.entity.BgScheme;
import com.fssc.budget.mapper.BgSchemeMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 预算方案审批收尾（ 2026-09-14，P0-1 事件编排总线策略实现）。
 * <p>
 * BG_SCHEME 审批流（wf_business_flow: flow_code=BG_APPROVE / biz_type=BG_SCHEME / model_key=BG_APPROVE，
 * 两级节点：预算管理员初审 → 财务总监终审）结束后：
 * <ul>
 *  <li>审批通过 → bg_scheme.status = APPROVED（待下达，由预算管理员页面执行 schemeIssue 转 ISSUED）</li>
 *  <li>驳回 / 撤回 → 回退 DRAFT（可修改后重新提交）</li>
 * </ul>
 * 向后兼容：既有 ACTIVE 方案不受影响；本 Action 仅处理 bizType=BG_SCHEME 的事件。
 * <p>
 * 事件分发由 {@link com.fssc.workflow.event.EventOrchestrationBus} 自动注册
 * （Bean 未在 wf_event_action_config 显式配置时按默认 BOTH + 999 排序接入）。
 */
@Slf4j
@Component("budgetSchemeBizAction")
@RequiredArgsConstructor
public class BudgetSchemeBizAction implements WfBizAction {

    private final BgSchemeMapper bgSchemeMapper;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("BG_SCHEME");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long schemeId = event.getBizId();
        if (schemeId == null || !"BG_SCHEME".equals(event.getBizType())) {
            return;
        }
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            log.warn("预算方案审批收尾: 不存在的方案 {}", schemeId);
            return;
        }
        BgScheme upd = new BgScheme();
        upd.setId(schemeId);
        if (Boolean.TRUE.equals(event.getApproved())) {
            // 审批通过 → APPROVED（待下达）：与 BudgetWfEventListener 同值幂等，
            // 下达由预算管理员在页面执行 schemeIssue（APPROVED → ISSUED，记录下达时间/人）。
            // P4-3 审计扩面：改状态前采集旧值
            String oldStatus = scheme.getStatus();
            upd.setStatus("APPROVED");
            bgSchemeMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), schemeId, scheme.getSchemeCode(), "bg_scheme",
                    String.valueOf(schemeId), auditBefore, auditAfter,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("预算方案 {} 审批通过（APPROVING → APPROVED，待下达）", scheme.getSchemeCode());
        } else {
            // 驳回/撤回 → 回退草稿
            if (!"APPROVING".equals(scheme.getStatus())) {
                log.info("预算方案 {} 状态已为 {}，跳过驳回回退", scheme.getSchemeCode(), scheme.getStatus());
                return;
            }
            // P4-3 审计扩面：改状态前采集旧值
            String oldStatus = scheme.getStatus();
            upd.setStatus("DRAFT");
            bgSchemeMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), schemeId, scheme.getSchemeCode(), "bg_scheme",
                    String.valueOf(schemeId), auditBefore, auditAfter,
                    "审批驳回" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("预算方案 {} 审批驳回/撤回（APPROVING → DRAFT）：{}",
                    scheme.getSchemeCode(), event.getComment());
        }
    }
}
