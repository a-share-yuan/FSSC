// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.system.service.NumberRuleService;
import com.fssc.treasury.entity.TrFundPlan;
import com.fssc.treasury.mapper.TrFundPlanMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 资金计划审批收尾（事件编排总线）。
 * <p>
 * TR_PLAN 审批通过 → tr_fund_plan.status: DRAFT → APPROVED
 * <p>
 * 缺陷修复（H-06 / DF-03）：审批结束兜底回填 plan_no——历史单据创建时未取号导致
 * tr_fund_plan.plan_no=NULL、审批待办/列表单据号列恒空；已有编号则去重跳过。
 */
@Slf4j
@Component("treasuryPlanBizAction")
@RequiredArgsConstructor
public class TreasuryPlanBizAction implements WfBizAction {

    private final TrFundPlanMapper trFundPlanMapper;
    private final NumberRuleService numberRuleService;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("TR_PLAN");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long planId = event.getBizId();
        if (planId == null) return;
        TrFundPlan plan = trFundPlanMapper.selectById(planId);
        if (plan == null) {
            log.warn("资金计划收尾: 不存在的计划 {}", planId);
            return;
        }
        try {
            // 审计扩面：改状态前采集旧值
            String oldStatus = plan.getStatus();
            TrFundPlan upd = new TrFundPlan();
            upd.setId(planId);
            upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
            if (plan.getPlanNo() == null || plan.getPlanNo().isBlank()) {
                upd.setPlanNo(nextPlanNo());
                log.info("资金计划 {} 审批结束兜底回填 plan_no={}", planId, upd.getPlanNo());
            }
            trFundPlanMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            // 本次实际变化：plan_no 兜底回填时一并记录
            if (upd.getPlanNo() != null) {
                auditBefore.put("planNo", plan.getPlanNo());
                auditAfter.put("planNo", upd.getPlanNo());
            }
            String auditBizNo = plan.getPlanNo() == null || plan.getPlanNo().isBlank() ? upd.getPlanNo() : plan.getPlanNo();
            auditTrailService.recordUpdate(event.getBizType(), planId, auditBizNo, "tr_fund_plan",
                    String.valueOf(planId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("资金计划 {} 审批{}: DRAFT → {}", plan.getPlanNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过" : "驳回",
                    Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
        } catch (Exception ex) {
            log.error("资金计划 {} 审批收尾失败: {}", planId, ex.getMessage(), ex);
        }
    }

    /** 资金计划取号：规则 TR_PLAN（FP 前缀），失败降级时间戳号不阻断收尾 */
    private String nextPlanNo() {
        try {
            return numberRuleService.next("TR_PLAN");
        } catch (Exception ex) {
            log.warn("资金计划兜底取号失败，降级时间戳编号: {}", ex.getMessage());
            return "FP" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
    }
}
