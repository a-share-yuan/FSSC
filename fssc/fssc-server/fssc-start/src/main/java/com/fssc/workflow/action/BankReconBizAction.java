// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.bank.entity.BankReconcile;
import com.fssc.bank.mapper.BankReconcileMapper;
import com.fssc.bank.service.BankReconcileService;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 银行对账审批收尾（事件编排总线 + 凭证引擎接线）。
 * <p>
 * TR_BANK_RECON 审批通过 → bank_reconcile.matchStatus: MATCHED → CONFIRMED + 生成对账差异凭证
 * 锁定对账结果，差异金额入财务费用
 */
@Slf4j
@Component("bankReconBizAction")
@RequiredArgsConstructor
public class BankReconBizAction implements WfBizAction {

    private final BankReconcileMapper bankReconcileMapper;
    private final VoucherService voucherService;
    /** W2：定稿前取当月余额调节差额 */
    private final BankReconcileService bankReconcileService;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("TR_BANK_RECON");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long reconId = event.getBizId();
        if (reconId == null) return;
        BankReconcile recon = bankReconcileMapper.selectById(reconId);
        if (recon == null) {
            log.warn("银行对账收尾: 不存在的对账记录 {}", reconId);
            return;
        }
        try {
            // 审计扩面：改状态前采集旧值
            String oldMatchStatus = recon.getMatchStatus();
            BankReconcile upd = new BankReconcile();
            upd.setId(reconId);
            if (Boolean.TRUE.equals(event.getApproved())) {
                // W2：调节差额非零＝当月还有没对平的未达账项，不静默定稿。
    // 留在 MATCHED 并逐条记录交人工处理——Task_4 人工复核这道挡位按既定口径保留不动。
                BigDecimal diff = bankReconcileService.reconDiff(reconId);
                if (diff != null && diff.signum() != 0) {
                    Map<String, Object> unchanged = new LinkedHashMap<>();
                    unchanged.put("matchStatus", oldMatchStatus);
                    auditTrailService.recordUpdate(event.getBizType(), reconId, recon.getSerialNo(), "bank_reconcile",
                            String.valueOf(reconId), unchanged, unchanged,
                            "余额调节差额 " + diff.toPlainString() + " 非零，未自动定稿，请人工核对未达账项后重提"
                                    + (event.getComment() == null || event.getComment().isBlank()
                                            ? "" : "：" + event.getComment()));
                    log.warn("银行对账 {} 审批通过但调节差额 {} 非零，保持 {} 不定稿",
                            reconId, diff.toPlainString(), oldMatchStatus);
                    return;
                }
                upd.setMatchStatus("CONFIRMED");
                log.info("银行对账 {} 审批通过: MATCHED → CONFIRMED", reconId);
            } else {
                upd.setMatchStatus("REJECTED");
                log.info("银行对账 {} 审批驳回: → REJECTED", reconId);
            }
            bankReconcileMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("matchStatus", oldMatchStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("matchStatus", upd.getMatchStatus());
            auditTrailService.recordUpdate(event.getBizType(), reconId, recon.getSerialNo(), "bank_reconcile",
                    String.valueOf(reconId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            // 生成银行对账差异凭证
            if (Boolean.TRUE.equals(event.getApproved())) {
                try {
                    voucherService.generateBankReconVoucher(recon);
                } catch (Exception vex) {
                    log.error("银行对账 {} 凭证生成失败（不影响状态更新）: {}", reconId, vex.getMessage(), vex);
                }
                // 定稿即关闭未达账项（OPEN → CLEARED），补齐"审批结论"在业务表上的落点；
                // 勾对经办人（bank_reconcile.match_by）作为清结操作人——引擎线程无登录上下文。
                try {
                    bankReconcileService.closeReconItems(reconId, recon.getMatchBy());
                } catch (Exception cex) {
                    log.error("银行对账 {} 未达账项清结失败（不影响状态更新）: {}", reconId, cex.getMessage(), cex);
                }
            }
        } catch (Exception ex) {
            log.error("银行对账 {} 审批收尾失败: {}", reconId, ex.getMessage(), ex);
        }
    }
}
