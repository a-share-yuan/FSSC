// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 三单匹配审批收尾（P0-1 事件编排总线）。
 * <p>
 * INVOICE_MATCH / INVOICE_MATCH_MANUAL 审批通过 → 确认匹配结果，po_order: APPROVED → INVOICED
 * + 应付确认凭证（借 库存商品(1405)/贷 应付账款(2202)）。
 * <p>
 * 注意：起流方（ThreeWayMatchService.review/manualReview）传入的 bizId 是<b>付款单 id</b>（exp_bill，PAY），
 * 采购订单需经 exp_bill.poId 解析（P1 修复：原实现直接把 bizId 当 po_order.id 查，永远查不到导致收尾空转）。
 */
@Slf4j
@Component("invoiceMatchBizAction")
@RequiredArgsConstructor
public class InvoiceMatchBizAction implements WfBizAction {

    private final PoOrderMapper poOrderMapper;
    private final com.fssc.expense.mapper.ExpBillMapper expBillMapper;
    private final com.fssc.sharing.service.VoucherService voucherService;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** D-DEC-077：人工匹配结论落库载体（写 exp_bill.ext_json.matchVerdict）。
     *  @Lazy 必需——ThreeWayMatchService → WfEngineFacade → 事件总线 → 本类，直接构造注入会成环 */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fssc.sharing.service.ThreeWayMatchService threeWayMatchService;
    /** D-DEC-071：四单齐套检查（含合同腿）所需 */
    private final com.fssc.contract.mapper.ContContractMapper contractMapper;

    @Override
    public Set<String> bizTypes() {
        return Set.of("INVOICE_MATCH", "INVOICE_MATCH_MANUAL");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long billId = event.getBizId();
        if (billId == null) return;
        com.fssc.expense.entity.ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            log.warn("三单匹配收尾: 不存在的付款单 {}", billId);
            return;
        }
        Long orderId = bill.getPoId();
        PoOrder order = orderId == null ? null : poOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("三单匹配收尾: 付款单 {} 未关联采购订单，跳过 INVOICED 迁移与应付确认凭证", bill.getBillNo());
            return;
        }
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        // D-V4R-85（P0）办结前硬闸：付款单仍有三单匹配硬异常（超付/超容差 ⇒ frozen）时，**禁止**在本链办结放行。
        //   此前本钩子对任意 approved 的 INVOICE_MATCH(_MANUAL) 链都无条件把结论写成 MANUAL_MATCHED，
        //   等于「谁点通过谁放行」，付款硬闸形同虚设。实证（2026-10-16，V4 人工 N1-B 幕）：
        //   付款单 FK202610160003（120,000 超付、无发票覆盖、缺合同腿）先被付款闸正确拦下
        //   （code=400「三单匹配拦截：超付（关联发票 0＋容差 0.00 < 本次付款 120,000.00）」），
        //   随后仅把该单的 INVOICE_MATCH 复审链点通过两节，结论即被改写为 MANUAL_MATCHED，
        //   付款闸随即放行 → 异常单真的付了款（PB202610160004 PAID，回单 SER17921255826250）。
        //   注意：本方法整体被外层 catch(Exception) 兜底，异常会被静默吞掉 ⇒ 硬拦必须抛在 try 之外。
        if (approved) {
            assertNoHardMatch(bill);
        }
        try {
            if (approved) {
                // D-DEC-077：先落人工/自动匹配链的<b>终态结论</b>（幂等覆盖，异常只 warn）——
                // 此前本钩子只把 po_order 置 INVOICED，付款单侧的匹配结论没有任何落库载体，
                // ThreeWayMatchService.check() 每次现算现判，办结后仍回显 FAILED 并继续冻结付款。
                threeWayMatchService.recordMatchVerdict(billId, event.getBizType(), event.getProcInstId(),
                        com.fssc.sharing.service.ThreeWayMatchService.VERDICT_MANUAL_MATCHED,
                        event.getComment());

                // D-DEC-071：四单齐套检查把「合同腿」纳入同一判据——
                // 付款单 contract_id 与采购订单 contract_id 两腿皆无（或挂了不存在的合同）即缺腿。
                // 本轮只做<b>如实报告 + WARN 留痕，不硬拦</b>：
                // 「缺合同是否硬拦」仍属 P2-B 在册待裁（分级册§六），裁决落地时把下面的 log.warn
                // 换成抛 BizException 即可，硬拦开关不进本轮。
                String contractGap = contractGapOf(bill, order);
                if (contractGap != null) {
                    log.warn("[四单齐套] 付款单 {}（采购订单 {}）办结即自动生成应付确认凭证，但四单缺合同腿：{}。"
                                    + "合同/PO/收货/发票四腿中合同腿无任何一侧登记，本轮按在册待裁口径只留痕不拦截。",
                            bill.getBillNo(), order.getPoNo(), contractGap);
                }

                // P4-3 审计扩面：改状态前采集旧值
                String oldStatus = order.getStatus();
                PoOrder upd = new PoOrder();
                upd.setId(order.getId());
                upd.setStatus("INVOICED");
                // D-DEC-051 口径（主窗 10-01 更正）：引擎线程 UserContext 为空时，"如实留 NULL"的说法
                // 不成立——FsscMetaObjectHandler 对 null 补 0L，落库是 update_by=0（现库正证 exp_bill
                // 0值2行/NULL 0行）。D-DEC-093 消费腿：办结人改从事件取 endOperUserId，取不到才落 0；
                // 不用 order.create_by / bill.apply_user_id 顶替——那会把"建单人"冒充成"审批办结人"。
                // 办结人另可由 wf_task_history 末节点 + exp_bill.ext_json.matchVerdict.procInstId 交叉验证。
                upd.setUpdateBy(event.getEndOperUserId() != null ? event.getEndOperUserId()
                        : (com.fssc.framework.security.UserContext.get() == null
                                ? null : com.fssc.framework.security.UserContext.get().getUserId()));
                upd.setUpdateTime(java.time.LocalDateTime.now());
                poOrderMapper.updateById(upd);
                java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                // D-DEC-342（未标级修复战役 K4-C）：留痕的 biz 坐标必须与流程实例同轨。
                // 本链 wf_instance 的坐标是 INVOICE_MATCH(_MANUAL)／exp_bill.id／FK 单号，此前这里写的是
                // PO 侧（po_order.id／PO 单号）⇒ 台账 16:58 实测 audit_trail id=17 按付款单号或实例联查＝0 命中，
                // 而同一窗内 PAY_PLAN_BASE（id=18）／RECON_ACTIVE（id=19）都写的是单据坐标，属正证。
                // 被改的行仍是 po_order ⇒ obj_type/obj_id 如实记 PO 侧；PO 单号折进备注，
                // 读侧关键词检索（AuditTrailService.page/listByFilter 是 obj_type OR remark 的 LIKE）照旧命中。
                // remark 列宽 varchar(500)，本行比原文多带一段 PO 说明，超长时截尾而不是让整行留痕丢掉。
                String trailRemark = "审批通过（采购订单 " + order.getPoNo() + " 状态 → " + upd.getStatus() + "）"
                        + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment());
                auditTrailService.recordUpdate(event.getBizType(), billId, bill.getBillNo(), "po_order",
                        String.valueOf(order.getId()), auditBefore, auditAfter,
                        trailRemark.length() > 500 ? trailRemark.substring(0, 500) : trailRemark);
                log.info("三单匹配 {} 审批通过: po_order {} → INVOICED（付款单 {}）",
                        event.getBizType(), order.getPoNo(), bill.getBillNo());
                // P1-B 流程终态生成凭证：三单匹配通过 = 应付确认会计事件
                // 借 库存商品(1405)/贷 应付账款(2202)，失败不阻断状态迁移
                try {
                    Long vid = voucherService.generateInvoiceMatchVoucher(order);
                    log.info("采购订单 {} 三单匹配自动生成应付确认凭证: {}", order.getPoNo(), vid);
                } catch (Exception ex) {
                    log.error("采购订单 {} 应付确认凭证生成失败（已置 INVOICED，可后续补生成）: {}",
                            order.getPoNo(), ex.getMessage());
                }
            } else {
                log.info("三单匹配 {} 审批驳回，po_order 状态不变", order.getPoNo());
            }
        } catch (Exception ex) {
            log.error("三单匹配 {} 审批收尾失败: {}", billId, ex.getMessage(), ex);
        }
    }

    /**
     * D-V4R-85（P0）：三单匹配硬异常闸（办结前）。
     *
     * <p>判据＝{@link com.fssc.sharing.service.ThreeWayMatchService#check(Long)} 现算的 {@code frozen}：
     * 超付、发票超收货超容差等 ERROR 级差异会把付款单冻住，此时付款执行接口会拒绝。
     * 人工/复审链<b>不得</b>把这种单据放行——否则只要在链上点一次通过就能绕过付款硬闸。
     * 取数失败同样不放行（无法确认可否放行时按保守处理）。
     */
    private void assertNoHardMatch(com.fssc.expense.entity.ExpBill bill) {
        com.fssc.sharing.service.ThreeWayMatchService.MatchRow live;
        try {
            live = threeWayMatchService.check(bill.getId());
        } catch (Exception ex) {
            log.warn("三单匹配收尾：付款单 {} 实时判定取数失败，按不放行处理：{}",
                    bill.getBillNo(), ex.getMessage());
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "三单匹配实时判定取数失败，无法确认可否放行，请稍后重试或联系管理员：" + ex.getMessage());
        }
        if (live == null || !live.isFrozen()) {
            return;
        }
        int issueCount = live.getIssues() == null ? 0 : live.getIssues().size();
        throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                "三单匹配仍有硬异常，禁止在本链办结放行：付款单 " + bill.getBillNo()
                        + " 当前判定 " + live.getMatchStatus() + "（主异常码 " + live.getMatchCode()
                        + "，异常明细 " + issueCount + " 条"
                        + (live.getMsg() == null || live.getMsg().isBlank() ? "" : "，" + live.getMsg())
                        + "）。处理出口：先在匹配台补齐单据——发票池「关联单据」补发票覆盖、"
                        + "匹配台「关联单据」挂 PO·收货单、结算单挂采购合同；待自动判定回到 MATCHED"
                        + "（或差异预警等非冻结档）后再办结本链。确需「票未到先付」的场景，"
                        + "请改走付款计划/预付申请等正式通道，不得在本链点通过绕过付款硬闸。");
    }

    /**
     * D-DEC-071：四单齐套的合同腿判据——付款单 contract_id 与采购订单 contract_id 是同一事实的两个登记点，
     * 任一侧有值且合同档案真实存在即算齐；两腿皆空＝缺合同腿，挂了但查不到＝合同悬空。
     *
     * @return 缺失描述；合同腿在位时返回 null（调用方据此决定是否留痕）
     */
    private String contractGapOf(com.fssc.expense.entity.ExpBill bill, PoOrder order) {
        Long contractId = bill.getContractId() != null ? bill.getContractId() : order.getContractId();
        String where = bill.getContractId() != null ? "付款单" : "采购订单";
        if (contractId == null) {
            return "付款单与采购订单均未登记合同";
        }
        try {
            com.fssc.contract.entity.ContContract c = contractMapper.selectById(contractId);
            if (c == null) {
                return where + "登记的合同 id=" + contractId + " 在 cont_contract 查无";
            }
            return null;
        } catch (Exception ex) {
            // 合同腿自身取数失败：如实报"无法判定"，不把它说成"缺合同"，也不因此放行齐套结论
            return "合同腿取数异常，无法判定（" + ex.getMessage() + "）";
        }
    }
}
