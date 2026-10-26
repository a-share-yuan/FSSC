// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.arflow.entity.ArClaim;
import com.fssc.arflow.mapper.ArClaimMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AR服务申请单审批收尾（补齐评估缺口1：接入 Flowable 审批引擎）。
 * 通过 → PENDING_DISPATCH（待派工，可手工派工/转单后审单过账）；驳回 → REJECTED。
 * 范式对齐 ReceivableBizAction / PayBatchBizAction：只 update 需要改的列，异常不阻断流程收尾。
 */
@Slf4j
@Component("arClaimBizAction")
@RequiredArgsConstructor
public class ArClaimBizAction implements WfBizAction {

    private final ArClaimMapper arClaimMapper;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("AR_CLAIM");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long claimId = event.getBizId();
        if (claimId == null) {
            return;
        }
        ArClaim claim = arClaimMapper.selectById(claimId);
        if (claim == null) {
            log.warn("AR服务申请审批收尾: 不存在的单据 {}", claimId);
            return;
        }
        try {
            // P4-3 审计扩面：改状态前采集旧值
            String oldStatus = claim.getStatus();
            ArClaim upd = new ArClaim();
            upd.setId(claimId);
            if (Boolean.TRUE.equals(event.getApproved())) {
                upd.setStatus(ArClaim.ST_PENDING_DISPATCH);
            } else {
                upd.setStatus(ArClaim.ST_REJECTED);
                upd.setRemark((claim.getRemark() == null ? "" : claim.getRemark() + " | ")
                        + "审批驳回:" + event.getComment());
            }
            arClaimMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            // 本次实际变化：驳回时同时拼接了 remark，一并记录
            if (upd.getRemark() != null) {
                auditBefore.put("remark", claim.getRemark());
                auditAfter.put("remark", upd.getRemark());
            }
            auditTrailService.recordUpdate(event.getBizType(), claimId, claim.getBillNo(), "ar_claim",
                    String.valueOf(claimId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("AR服务申请 {} 审批收尾: {}", claim.getBillNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过→PENDING_DISPATCH" : "驳回→REJECTED");
        } catch (Exception ex) {
            log.error("AR服务申请 {} 审批收尾失败: {}", claimId, ex.getMessage(), ex);
        }
    }
}
