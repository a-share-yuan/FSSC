// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 应收确认审批收尾（P0-1 事件编排总线 + P0-2 凭证引擎接线）。
 * <p>
 * RECEIVABLE 审批通过 → rc_receivable.status: NONE → CONFIRMED + 生成应收确认凭证
 * （之前仅 NONE29/OVERDUE30，无 CONFIRMED 态，应收循环第二环节断裂）
 */
@Slf4j
@Component("receivableBizAction")
@RequiredArgsConstructor
public class ReceivableBizAction implements WfBizAction {

    private final RcReceivableMapper rcReceivableMapper;
    private final VoucherService voucherService;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("RECEIVABLE");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long recId = event.getBizId();
        if (recId == null) return;
        RcReceivable rec = rcReceivableMapper.selectById(recId);
        if (rec == null) {
            log.warn("应收确认收尾: 不存在的应收单 {}", recId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // P4-3 审计扩面：改状态前采集旧值
                String oldStatus = rec.getStatus();
                // 已核销最终状态 DONE 不得被审批回退成 CONFIRMED（收款环节先完成、审批后到的真实时序）
                String newStatus = "DONE".equals(oldStatus) ? "DONE" : "CONFIRMED";
                RcReceivable upd = new RcReceivable();
                upd.setId(recId);
                upd.setStatus(newStatus);
                rcReceivableMapper.updateById(upd);
                java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), recId, rec.getBillNo(), "rc_receivable",
                        String.valueOf(recId), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("应收单 {} 审批通过: {} → {}", rec.getBillNo(), oldStatus, newStatus);
                // P0-2: 生成应收确认凭证
                try {
                    voucherService.generateReceivableVoucher(rec);
                } catch (Exception vex) {
                    log.error("应收单 {} 凭证生成失败（不影响状态更新）: {}", rec.getBillNo(), vex.getMessage(), vex);
                }
            } else {
                // P4-3 审计扩面：改状态前采集旧值
                String oldStatus = rec.getStatus();
                RcReceivable upd = new RcReceivable();
                upd.setId(recId);
                upd.setStatus("REJECTED");
                rcReceivableMapper.updateById(upd);
                java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), recId, rec.getBillNo(), "rc_receivable",
                        String.valueOf(recId), auditBefore, auditAfter,
                        "审批驳回" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("应收单 {} 审批驳回: → REJECTED", rec.getBillNo());
            }
        } catch (Exception ex) {
            log.error("应收单 {} 审批收尾失败: {}", recId, ex.getMessage(), ex);
        }
    }
}
