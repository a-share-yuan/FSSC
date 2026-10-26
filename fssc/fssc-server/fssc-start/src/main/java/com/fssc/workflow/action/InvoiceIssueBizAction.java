// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 开票审批收尾（P0-1 事件编排总线）。
 * <p>
 * INVOICE_ISSUE 审批通过 → inv_invoice.status: DRAFT → ISSUED
 */
@Slf4j
@Component("invoiceIssueBizAction")
@RequiredArgsConstructor
public class InvoiceIssueBizAction implements WfBizAction {

    private final InvInvoiceMapper invInvoiceMapper;
    private final RcReceivableMapper receivableMapper;
    private final VoucherService voucherService;
    private final com.fssc.customer.mapper.CrmCustomerMapper crmCustomerMapper;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("INVOICE_ISSUE");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long invoiceId = event.getBizId();
        if (invoiceId == null) return;
        InvInvoice invoice = invInvoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("开票收尾: 不存在的发票 {}", invoiceId);
            return;
        }
        try {
            // P4-3 审计扩面：改状态前采集旧值
            String oldStatus = invoice.getStatus();
            InvInvoice upd = new InvInvoice();
            upd.setId(invoiceId);
            upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "ISSUED" : "REJECTED");
            invInvoiceMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), invoiceId, invoice.getInvoiceNo(), "inv_invoice",
                    String.valueOf(invoiceId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            // 合规整改( )：开票审批通过 → 生成应收单 + 收入确认凭证（销项闭环）
            if (Boolean.TRUE.equals(event.getApproved())) {
                try {
                    createReceivableAndVoucher(invoice, invoiceId);
                } catch (Exception ex2) {
                    log.error("开票 {} 生成应收单/凭证失败: {}", invoice.getInvoiceNo(), ex2.getMessage(), ex2);
                }
            }
            log.info("发票 {} 审批{}: DRAFT → {}", invoice.getInvoiceNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过" : "驳回",
                    Boolean.TRUE.equals(event.getApproved()) ? "ISSUED" : "REJECTED");
        } catch (Exception ex) {
            log.error("开票 {} 审批收尾失败: {}", invoiceId, ex.getMessage(), ex);
        }
    }

    /** 开票审批通过后生成应收单并确认收入凭证（客户按购买方名称匹配主数据，无则新建） */
    private void createReceivableAndVoucher(InvInvoice invoice, Long invoiceId) {
        BigDecimal amount = invoice.getAmount() == null ? BigDecimal.ZERO : invoice.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("开票 {} 金额为 0，跳过应收单生成", invoice.getInvoiceNo());
            return;
        }
        // 修复补充：开票 UI(Invoice.vue 销售开票 tab)将「购买方名称」绑定在 invoiceTitle(发票抬头)，
        // buyerName 多为 NULL——客户解析优先 buyerName，为空回落 invoiceTitle，保证应收单能挂到真实购买方。
        String buyer = invoice.getBuyerName() != null && !invoice.getBuyerName().isBlank()
                ? invoice.getBuyerName() : invoice.getInvoiceTitle();
        Long customerId = resolveOrCreateCustomer(buyer, invoice.getBuyerTaxNo());
        if (customerId == null) {
            log.warn("开票 {} 无法解析客户，跳过应收单生成", invoice.getInvoiceNo());
            return;
        }
        RcReceivable rec = new RcReceivable();
        rec.setTenantId(com.fssc.framework.tenant.TenantContext.getTenantId());
        rec.setBillNo("AR-INV-" + (invoice.getInvoiceNo() == null ? String.valueOf(invoiceId) : invoice.getInvoiceNo()));
        rec.setCustomerId(customerId);
        rec.setBillDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now());
        rec.setDueDate(rec.getBillDate().plusDays(30));
        rec.setCurrencyCode(invoice.getCurrencyCode() == null ? "CNY" : invoice.getCurrencyCode());
        rec.setAmount(amount);
        rec.setReceivedAmount(BigDecimal.ZERO);
        rec.setRemainAmount(amount);
        rec.setStatus("NORMAL");
        rec.setSourceDocType("INVOICE_ISSUE");
        rec.setSourceDocId(invoiceId);
        rec.setCompanyId(invoice.getCompanyId());
        receivableMapper.insert(rec);
        voucherService.generateReceivableVoucher(rec);
        log.info("开票 {} 已生成应收单并确认收入凭证", invoice.getInvoiceNo());
    }

    private Long resolveOrCreateCustomer(String buyerName, String buyerTaxNo) {
        if (buyerName == null || buyerName.isBlank()) return null;
        List<com.fssc.customer.entity.CrmCustomer> hits = crmCustomerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fssc.customer.entity.CrmCustomer>()
                        .eq(com.fssc.customer.entity.CrmCustomer::getCustomerName, buyerName.trim())
                        .orderByAsc(com.fssc.customer.entity.CrmCustomer::getId)
                        .last("limit 1"));
        if (!hits.isEmpty()) return hits.get(0).getId();
        com.fssc.customer.entity.CrmCustomer c = new com.fssc.customer.entity.CrmCustomer();
        c.setTenantId(com.fssc.framework.tenant.TenantContext.getTenantId());
        c.setCustomerCode("CUST" + System.currentTimeMillis());
        c.setCustomerName(buyerName.trim());
        c.setCustomerType("CUSTOMER");
        c.setStatus("NORMAL");
        crmCustomerMapper.insert(c);
        return c.getId();
    }
}
