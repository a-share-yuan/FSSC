// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.service.NumberRuleService;
import com.fssc.treasury.entity.TrBillSettle;
import com.fssc.treasury.entity.TrCreditBill;
import com.fssc.treasury.entity.TrFundPlan;
import com.fssc.treasury.entity.TrTransfer;
import com.fssc.treasury.mapper.TrBillSettleMapper;
import com.fssc.treasury.mapper.TrCreditBillMapper;
import com.fssc.treasury.mapper.TrFundPlanMapper;
import com.fssc.treasury.mapper.TrTransferMapper;
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
 * 资金域审批收尾（事件编排总线 + 凭证引擎接线）。
 * <p>
 * 覆盖 TR_TRANSFER / TR_UPLOAD / TR_RECEIPT / TR_BILL / TR_PAY_FAIL /
 * TR_BANK_MASTER / TR_SEAL_CHANGE / TR_PLAN_ADJUST 八类资金域流程。
 * 审批通过：翻转业务单据最终状态 + 生成资金凭证；审批驳回：记录日志，无对应驳回态则保持原态。
 * <p>
 * 实体与状态语义：
 * <ul>
 *   <li>TR_TRANSFER / TR_UPLOAD → tr_transfer（DRAFT→APPROVED，execute 才落 PAID）+ 调拨/上存凭证</li>
 *   <li>TR_RECEIPT → tr_credit_bill（PENDING→RECONCILED）+ 收款凭证</li>
 *   <li>TR_BILL → tr_bill_settle（登记确认，状态保持 STOCK）+ 应收票据凭证</li>
 *   <li>TR_PAY_FAIL → op_pay_batch（FAILED→APPROVED 回到可重试路径）</li>
 *   <li>TR_BANK_MASTER / TR_SEAL_CHANGE → md_bank_account（确保 NORMAL 生效）</li>
 *   <li>TR_PLAN_ADJUST → tr_fund_plan（SELECTED/DRAFT→APPROVED，复用计划实体）</li>
 * </ul>
 */
@Slf4j
@Component("treasuryBizAction")
@RequiredArgsConstructor
public class TreasuryBizAction implements WfBizAction {

    private final TrTransferMapper trTransferMapper;
    private final TrCreditBillMapper trCreditBillMapper;
    private final TrBillSettleMapper trBillSettleMapper;
    private final TrFundPlanMapper trFundPlanMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final MdBankAccountMapper mdBankAccountMapper;
    private final VoucherService voucherService;
    private final NumberRuleService numberRuleService;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("TR_TRANSFER", "TR_UPLOAD", "TR_RECEIPT", "TR_BILL",
                "TR_PAY_FAIL", "TR_BANK_MASTER", "TR_SEAL_CHANGE", "TR_PLAN_ADJUST");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        switch (event.getBizType()) {
            case "TR_TRANSFER":
            case "TR_UPLOAD":
                handleTransfer(event, approved);
                break;
            case "TR_RECEIPT":
                handleReceipt(event, approved);
                break;
            case "TR_BILL":
                handleBill(event, approved);
                break;
            case "TR_PAY_FAIL":
                handlePayFail(event, approved);
                break;
            case "TR_BANK_MASTER":
            case "TR_SEAL_CHANGE":
                handleBankMaster(event, approved);
                break;
            case "TR_PLAN_ADJUST":
                handlePlanAdjust(event, approved);
                break;
            default:
                log.warn("资金域收尾: 未识别的 bizType={}", event.getBizType());
        }
    }

    /** 资金调拨/上存：审批通过 DRAFT→APPROVED + 调拨/上存凭证；驳回保持 DRAFT */
    private void handleTransfer(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        TrTransfer t = trTransferMapper.selectById(id);
        if (t == null) {
            log.warn("资金调拨收尾: 不存在的调拨单 {}", id);
            return;
        }
        try {
            if (approved) {
                // 审计扩面：改状态前采集旧值
                String oldStatus = t.getStatus();
                TrTransfer upd = new TrTransfer();
                upd.setId(id);
                upd.setStatus("APPROVED");
                trTransferMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), id, t.getTransferNo(), "tr_transfer",
                        String.valueOf(id), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("资金调拨单 {} 审批通过: DRAFT → APPROVED", t.getTransferNo());
                try {
                    voucherService.generateTransferVoucher(t, event.getBizType());
                } catch (Exception vex) {
                    log.error("资金调拨单 {} 凭证生成失败（不影响状态更新）: {}", t.getTransferNo(), vex.getMessage(), vex);
                }
            } else {
                log.info("资金调拨单 {} 审批驳回，保持草稿态待重提", t.getTransferNo());
            }
        } catch (Exception ex) {
            log.error("资金调拨单 {} 审批收尾失败: {}", id, ex.getMessage(), ex);
        }
    }

    /** 资金收款：审批通过 PENDING→RECONCILED + 收款凭证；驳回保持 PENDING */
    private void handleReceipt(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        TrCreditBill b = trCreditBillMapper.selectById(id);
        if (b == null) {
            log.warn("资金收款收尾: 不存在的收款单 {}", id);
            return;
        }
        try {
            if (approved) {
                // 审计扩面：改状态前采集旧值
                String oldStatus = b.getStatus();
                TrCreditBill upd = new TrCreditBill();
                upd.setId(id);
                upd.setStatus("RECONCILED");
                // 甲案：状态翻档同处补写核销双列——此前薄更新只带 id＋status，
                // recon_flag/recon_time 恒 0/NULL，按这两列查询的报表与去重判断永远看不到这笔到账
                upd.setReconFlag(Boolean.TRUE);
                upd.setReconTime(LocalDateTime.now());
                trCreditBillMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                auditBefore.put("reconFlag", b.getReconFlag());
                auditBefore.put("reconTime", b.getReconTime());
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditAfter.put("reconFlag", upd.getReconFlag()); // D-DEC-421：字段级 before/after 补齐核销段
                auditAfter.put("reconTime", upd.getReconTime());
                auditTrailService.recordUpdate(event.getBizType(), id, b.getBillNo(), "tr_credit_bill",
                        String.valueOf(id), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("收款单 {} 审批通过: PENDING → RECONCILED", b.getBillNo());
                try {
                    voucherService.generateReceiptVoucher(b);
                } catch (Exception vex) {
                    log.error("收款单 {} 凭证生成失败（不影响状态更新）: {}", b.getBillNo(), vex.getMessage(), vex);
                }
            } else {
                log.info("收款单 {} 审批驳回，保持待核销态", b.getBillNo());
            }
        } catch (Exception ex) {
            log.error("收款单 {} 审批收尾失败: {}", id, ex.getMessage(), ex);
        }
    }

    /** 票据结算：审批通过 生成应收票据凭证（登记已确认，状态保持 STOCK）；驳回保持 STOCK */
    private void handleBill(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        TrBillSettle s = trBillSettleMapper.selectById(id);
        if (s == null) {
            log.warn("票据结算收尾: 不存在的票据 {}", id);
            return;
        }
        try {
            if (approved) {
                log.info("票据 {} 审批通过：登记入托确认（状态保持 {}）", s.getTicketNo(), s.getStatus());
                try {
                    voucherService.generateBillSettleVoucher(s);
                } catch (Exception vex) {
                    log.error("票据 {} 凭证生成失败（不影响状态更新）: {}", s.getTicketNo(), vex.getMessage(), vex);
                }
            } else {
                log.info("票据 {} 审批驳回，保持库内态", s.getTicketNo());
            }
        } catch (Exception ex) {
            log.error("票据 {} 审批收尾失败: {}", id, ex.getMessage(), ex);
        }
    }

    /** 支付失败处理：审批通过 FAILED→APPROVED（回到可重试路径）；驳回保持 FAILED */
    private void handlePayFail(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        OpPayBatch batch = opPayBatchMapper.selectById(id);
        if (batch == null) {
            log.warn("支付失败处理收尾: 不存在的付款批次 {}", id);
            return;
        }
        try {
            // 审计扩面：改状态前采集旧值
            String oldStatus = batch.getStatus();
            OpPayBatch upd = new OpPayBatch();
            upd.setId(id);
            // 失败处理核准后回到"已审批"而非"已确认"——execute 前置已收窄，CONFIRMED 不再可打款
            upd.setStatus(approved ? "APPROVED" : "FAILED");
            opPayBatchMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), id, batch.getBatchNo(), "op_pay_batch",
                    String.valueOf(id), auditBefore, auditAfter,
                    (approved ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("付款批次 {} 支付失败处理{}: → {}",
                    batch.getBatchNo(), approved ? "通过" : "驳回", approved ? "APPROVED" : "FAILED");
        } catch (Exception ex) {
            log.error("付款批次 {} 支付失败处理收尾失败: {}", id, ex.getMessage(), ex);
        }
    }

    /** 银行主数据/印鉴变更：审批通过确保账户 NORMAL（主数据生效）；驳回保持原态 */
    private void handleBankMaster(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        MdBankAccount acc = mdBankAccountMapper.selectById(id);
        if (acc == null) {
            log.warn("银行主数据收尾: 不存在的账户 {}", id);
            return;
        }
        try {
            if (approved) {
                // 审计扩面：改状态前采集旧值
                String oldStatus = acc.getStatus();
                MdBankAccount upd = new MdBankAccount();
                upd.setId(id);
                upd.setStatus("NORMAL");
                mdBankAccountMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), id, acc.getAccountCode(), "md_bank_account",
                        String.valueOf(id), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("银行账户 {} 审批通过：主数据生效（NORMAL）", acc.getAccountName());
            } else {
                log.info("银行账户 {} 审批驳回，保持原态", acc.getAccountName());
            }
        } catch (Exception ex) {
            log.error("银行账户 {} 审批收尾失败: {}", id, ex.getMessage(), ex);
        }
    }

    /** 资金计划调整：审批通过 SELECTED/DRAFT→APPROVED；驳回保持原态。plan_no 为空时默认处理回填（H-06/DF-03） */
    private void handlePlanAdjust(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) return;
        TrFundPlan plan = trFundPlanMapper.selectById(id);
        if (plan == null) {
            log.warn("资金计划调整收尾: 不存在的计划 {}", id);
            return;
        }
        try {
            if (approved) {
                // 审计扩面：改状态前采集旧值
                String oldStatus = plan.getStatus();
                TrFundPlan upd = new TrFundPlan();
                upd.setId(id);
                upd.setStatus("APPROVED");
                // 缺陷修复（H-06 / DF-03）：历史计划创建时未取号，审批结束默认处理回填 plan_no
                if (plan.getPlanNo() == null || plan.getPlanNo().isBlank()) {
                    upd.setPlanNo(nextPlanNo());
                    log.info("资金计划 {} 调整审批结束兜底回填 plan_no={}", id, upd.getPlanNo());
                }
                trFundPlanMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                // 本次实际变化：plan_no 默认处理回填时一并记录
                if (upd.getPlanNo() != null) {
                    auditBefore.put("planNo", plan.getPlanNo());
                    auditAfter.put("planNo", upd.getPlanNo());
                }
                String auditBizNo = plan.getPlanNo() == null || plan.getPlanNo().isBlank() ? upd.getPlanNo() : plan.getPlanNo();
                auditTrailService.recordUpdate(event.getBizType(), id, auditBizNo, "tr_fund_plan",
                        String.valueOf(id), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("资金计划 {} 调整审批通过: → APPROVED", plan.getPlanNo());
            } else {
                log.info("资金计划 {} 调整审批驳回，保持原态", plan.getPlanNo());
            }
        } catch (Exception ex) {
            log.error("资金计划 {} 调整审批收尾失败: {}", id, ex.getMessage(), ex);
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
