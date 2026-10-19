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

import java.math.BigDecimal;
import java.util.Set;

/**
 * 收款认领审批收尾（P0-1 事件编排总线 + P0-2 凭证引擎接线）。
 * <p>
 * RECEIVABLE_COLLECT 审批通过 → 自动核销应收余额 + 生成收款核销凭证：
 * 1. 查应收单（bizId 即应收单 ID）
 * 2. 累加已收金额，重算余额
 * 3. 余额 ≤ 0 → status DONE（结清）；否则 → PARTIAL（部分收款）
 * 4. 生成收款核销凭证（借银行存款 / 贷应收账款）
 * <p>
 */
@Slf4j
@Component("collectBizAction")
@RequiredArgsConstructor
public class CollectBizAction implements WfBizAction {

    private final RcReceivableMapper rcReceivableMapper;
    private final VoucherService voucherService;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("RECEIVABLE_COLLECT");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long recId = event.getBizId();
        if (recId == null) return;
        RcReceivable rec = rcReceivableMapper.selectById(recId);
        if (rec == null) {
            log.warn("收款认领收尾: 不存在的应收单 {}", recId);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("收款认领 {} 审批驳回，不核销", rec.getBillNo());
                return;
            }
            // D-DEC-409（甲）：核销金额原取<b>票面全额</b>（rec.getAmount()），与本方法注释、wf_event_action_config
            // id=81 的 description 和 VoucherService.generateCollectVoucher 的 javadoc 三处口径都写着的
            // 「取应收余额作为核销金额」相反。后果两处（现库实证风险，非理论）：
            //   ①部分收款后再认领 ⇒ received 翻倍、remain 转负（127,200 单再走一次即 −127,200）；
            //   ②对已 DONE（余额 0）的应收单再触发一次 ⇒ 凭空加款＋凭空出一张借银行存款的进账凭证。
            // 修法＝按声明的口径实现：本次核销额＝当前余额，余额夹到非负；余额已为 0 就什么都不做。
            // 不落的那半条＝「该腿是否必须先有 rc_receipt 到账载体才允许出证」＝账务前置条件，进人工裁决清单。
            BigDecimal receivableAmount = rec.getAmount() == null ? BigDecimal.ZERO : rec.getAmount();
            BigDecimal received = rec.getReceivedAmount() == null ? BigDecimal.ZERO : rec.getReceivedAmount();
            BigDecimal remain = rec.getRemainAmount() != null ? rec.getRemainAmount()
                    : receivableAmount.subtract(received);
            if (remain.signum() < 0) {
                remain = BigDecimal.ZERO;
            }
            if (remain.signum() == 0) {
                log.warn("收款认领 {} 审批通过但应收余额已为 0（已收 {}／票面 {}），不重复核销、不出证（D-DEC-409）",
                        rec.getBillNo(), received, receivableAmount);
                return;
            }
            BigDecimal newReceived = received.add(remain);
            BigDecimal newRemain = receivableAmount.subtract(newReceived);

            RcReceivable upd = new RcReceivable();
            upd.setId(recId);
            upd.setReceivedAmount(newReceived);
            upd.setRemainAmount(newRemain);
            if (newRemain.compareTo(BigDecimal.ZERO) <= 0) {
                upd.setStatus("DONE");
                log.info("收款认领 {} 审批通过: 全额核销，NONE → DONE", rec.getBillNo());
            } else {
                upd.setStatus("PARTIAL");
                log.info("收款认领 {} 审批通过: 部分核销，已收 {} 余额 {}", rec.getBillNo(), newReceived, newRemain);
            }
            rcReceivableMapper.updateById(upd);
            // P4-3 审计扩面：本次实际变化为 status/receivedAmount/remainAmount 三字段
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", rec.getStatus());
            auditBefore.put("receivedAmount", rec.getReceivedAmount());
            auditBefore.put("remainAmount", rec.getRemainAmount());
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditAfter.put("receivedAmount", upd.getReceivedAmount());
            auditAfter.put("remainAmount", upd.getRemainAmount());
            auditTrailService.recordUpdate(event.getBizType(), recId, rec.getBillNo(), "rc_receivable",
                    String.valueOf(recId), auditBefore, auditAfter,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            // P0-2: 生成收款核销凭证——金额＝本次核销额（余额），不是票面额（D-DEC-409 甲）
            try {
                voucherService.generateCollectVoucher(rec, remain);
            } catch (Exception vex) {
                log.error("收款认领 {} 凭证生成失败（不影响核销）: {}", rec.getBillNo(), vex.getMessage(), vex);
            }
        } catch (Exception ex) {
            log.error("收款认领 {} 审批收尾失败: {}", recId, ex.getMessage(), ex);
        }
    }
}
