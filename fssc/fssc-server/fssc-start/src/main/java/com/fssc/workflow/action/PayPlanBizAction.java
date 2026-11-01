// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.payplan.entity.PayPlan;
import com.fssc.payplan.mapper.PayPlanMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 采购付款计划审批收尾（缺陷 D-12）。
 * <p>PAY_PLAN_BASE / PAY_PLAN_PLATE 上线时漏接 Flowable 收尾钩子：流程办结后
 * pay_plan.status 永久停留 APPROVING，付款批次核销（PlanWritebackService 按
 * status=APPROVED 匹配）永远跳过该计划，协同端看不到"已付款"。
 * <p>通过 → APPROVED（进入可核销池）；驳回 → REJECTED。
 * 范式对齐 ArClaimBizAction：只 update 需要改的列，异常不阻断流程收尾。
 */
@Slf4j
@Component("payPlanBizAction")
@RequiredArgsConstructor
public class PayPlanBizAction implements WfBizAction {

    private final PayPlanMapper payPlanMapper;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("PAY_PLAN_BASE", "PAY_PLAN_PLATE");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long planId = event.getBizId();
        if (planId == null) {
            return;
        }
        PayPlan plan = payPlanMapper.selectById(planId);
        if (plan == null) {
            log.warn("付款计划审批收尾: 不存在的单据 {}", planId);
            return;
        }
        try {
            // 审计扩面：改状态前采集旧值
            String oldStatus = plan.getStatus();
            PayPlan upd = new PayPlan();
            upd.setId(planId);
            if (Boolean.TRUE.equals(event.getApproved())) {
                upd.setStatus("APPROVED");
            } else {
                upd.setStatus("REJECTED");
                upd.setRemark((plan.getRemark() == null ? "" : plan.getRemark() + " | ")
                        + "审批驳回:" + event.getComment());
            }
            payPlanMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            // 本次实际变化：驳回时同时拼接了 remark，一并记录
            if (upd.getRemark() != null) {
                auditBefore.put("remark", plan.getRemark());
                auditAfter.put("remark", upd.getRemark());
            }
            auditTrailService.recordUpdate(event.getBizType(), planId, plan.getPlanNo(), "pay_plan",
                    String.valueOf(planId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("付款计划 {} 审批收尾: {}", plan.getPlanNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过→APPROVED" : "驳回→REJECTED");
        } catch (Exception ex) {
            log.error("付款计划 {} 审批收尾失败: {}", planId, ex.getMessage(), ex);
        }
    }
}
