// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 采购订单审批收尾（事件编排总线）。
 * <p>
 * PURCHASE_ORDER_REVIEW 审批通过 → po_order.status: OPEN → APPROVED。
 * <p>
 * <b>（甲档）：订单审批通过环节不再出证。</b>「审批不产生入库」——入库的账实动作在收货侧，
 * 采购循环的出证环节本来就有一条完整的：<br>
 * {@code PurchaseService.createReceipt:502 → VoucherService.generateEstimateOnReceipt}（借 1405／贷 2203 暂估）
 * → {@code InvoiceService:641 → VoucherService.reverseEstimateOnInvoiceMatch}（三单匹配红冲暂估）
 * → {@code InvoiceMatchBizAction:107 → VoucherService.generateInvoiceMatchVoucher}（借 1405／贷 2202 应付确认）。<br>
 * 原先这里无条件调 {@code generatePurchaseVoucher(order)}（借 1405／贷 2202，摘要却写「采购入库」，
 * 而此刻<b>尚未收货</b>），与收货暂估环节是两条各自成立的出证环节，缺一层排他 ⇒ 同一笔 80,000 在 12 分钟内
 * 出三张平衡凭证：库存商品借 160,000（实际到货 80,000）、应付账款贷 160,000 ＋ 暂估应付贷 80,000（实际负债 80,000）。
 * <p>
 * 只撤本环节的<b>调用</b>：{@code VoucherService.generatePurchaseVoucher} 作为历史凭证生成通道原样保留
 * （历史 5 张 PURCHASE 凭证的读环节、以及后续若要改走「在途/在单」规则（丙档）或「收货即红冲」（乙档）都还要它），
 * 不做删除、不作废、不冲销任何数据（铁律：不改数据）。
 */
@Slf4j
@Component("purchaseOrderBizAction")
@RequiredArgsConstructor
public class PurchaseOrderBizAction implements WfBizAction {

    private final PoOrderMapper poOrderMapper;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("PURCHASE_ORDER_REVIEW");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long orderId = event.getBizId();
        if (orderId == null) return;
        PoOrder order = poOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("采购订单审批收尾: 不存在的订单 {}", orderId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // 审计扩面：改状态前采集旧值
                String oldStatus = order.getStatus();
                PoOrder upd = new PoOrder();
                upd.setId(orderId);
                upd.setStatus("APPROVED");
                poOrderMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), orderId, order.getPoNo(), "po_order",
                        String.valueOf(orderId), auditBefore, auditAfter,
                        "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("采购订单 {} 审批通过: OPEN → APPROVED", order.getPoNo());
                // 甲档：订单审批通过不出证——入库出证环节在收货侧
                // PurchaseService.createReceipt → VoucherService.generateEstimateOnReceipt（借 1405／贷 2203 暂估），
                // 三单匹配再由 generateInvoiceMatchVoucher 出正式应付。此处出证＝库存与负债双计。
                log.info("采购订单 {} 审批通过腿按 D-DEC-333 不出证，入库凭证由收货暂估腿生成", order.getPoNo());
            } else {
                // 审计扩面：改状态前采集旧值
                String oldStatus = order.getStatus();
                PoOrder upd = new PoOrder();
                upd.setId(orderId);
                upd.setStatus("REJECTED");
                poOrderMapper.updateById(upd);
                Map<String, Object> auditBefore = new LinkedHashMap<>();
                auditBefore.put("status", oldStatus);
                Map<String, Object> auditAfter = new LinkedHashMap<>();
                auditAfter.put("status", upd.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), orderId, order.getPoNo(), "po_order",
                        String.valueOf(orderId), auditBefore, auditAfter,
                        "审批驳回" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                log.info("采购订单 {} 审批驳回: OPEN → REJECTED", order.getPoNo());
            }
        } catch (Exception ex) {
            log.error("采购订单 {} 审批收尾失败: {}", orderId, ex.getMessage(), ex);
        }
    }
}
