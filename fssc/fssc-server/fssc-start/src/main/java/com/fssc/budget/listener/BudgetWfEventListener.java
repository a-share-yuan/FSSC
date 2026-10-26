// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.budget.entity.BgScheme;
import com.fssc.budget.mapper.BgSchemeMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 预算方案审批收尾（WfBizAction）：BG_SCHEME 流程结束后同步方案状态。
 * 缺陷修复（E2E 2026-09-17）：预算域此前无 ended 收尾——审批通过后 bg_scheme 滞留 APPROVING，
 * 与 Flowable 已结束的事实不一致。预算管理规则：
 * 审批通过 → APPROVED（待下达，由预算管理员执行 schemeIssue 转 ISSUED）；
 * 审批驳回 → DRAFT（与 BudgetSchemeBizAction 同值去重，可改后重新提交；
 * 不置 REJECTED——schemeSubmit 仅接受 DRAFT，REJECTED 会成为无出口死态）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetWfEventListener implements WfBizAction {

    private final BgSchemeMapper bgSchemeMapper;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("BG_SCHEME");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long schemeId = event.getBizId();
        if (schemeId == null) {
            return;
        }
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            log.warn("流程结束事件指向不存在的预算方案: {}", schemeId);
            return;
        }
        BgScheme upd = new BgScheme();
        upd.setId(scheme.getId());
        // P4-3 审计扩面：改状态前采集旧值
        String oldStatus = scheme.getStatus();
        upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "DRAFT");
        bgSchemeMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", oldStatus);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", upd.getStatus());
        auditTrailService.recordUpdate(event.getBizType(), scheme.getId(), scheme.getSchemeCode(), "bg_scheme",
                String.valueOf(scheme.getId()), auditBefore, auditAfter,
                (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                        + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
        log.info("预算方案 {} 审批收尾: status={}", scheme.getSchemeCode(), upd.getStatus());
    }
}
