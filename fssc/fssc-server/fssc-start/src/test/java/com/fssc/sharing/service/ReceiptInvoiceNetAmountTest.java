package com.fssc.sharing.service;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.l1.L1TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G#39：收货单开票回写（po_receipt.invoice_amount）与「开票 ≤ 收货」硬闸的价税口径。
 *
 * <p>po_receipt.amount 是不含税入账额，而 inv_bill_invoice.amount 是价税合计。旧实现拿价税
 * 直接比不含税收货额，标准组合「净额收货＋价税开票」被恒判超收货 ⇒ 拒绝回写并派 INVOICE_MATCH
 * 异常单（G2 实证：收货 36,000 对开票 40,680，超出的 4,680 恰为税额）。
 * 修复后两处回写点都取勾稽净额，与 {@link ThreeWayMatchService#check} 的 invoiceNetAmt 同尺。
 */
@DisplayName("G#39 收货单开票回写的价税口径")
class ReceiptInvoiceNetAmountTest {

    /** G2 实案：收货单 GRN192436 不含税 36,000；关联发票 04721910 价税 40,680（税额 4,680） */
    private static final BigDecimal RECEIPT_NET = new BigDecimal("36000");
    private static final BigDecimal INVOICE_GROSS = new BigDecimal("40680");
    private static final BigDecimal INVOICE_TAX = new BigDecimal("4680");

    private ThreeWayMatchService svcWithTax(BigDecimal taxAmount) {
        ThreeWayMatchService svc = L1TestSupport.mockService(ThreeWayMatchService.class);

        ExpBillMapper billMapper = Mockito.mock(ExpBillMapper.class);
        ExpBill bill = new ExpBill();
        bill.setId(77L);
        Mockito.when(billMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(bill));
        L1TestSupport.inject(svc, "expBillMapper", billMapper);

        // 勾稽行与发票读取走 InvoiceDomainFacade（服务已不再直连 inv_* mapper）
        InvBillInvoice link = new InvBillInvoice();
        link.setBillId(77L);
        link.setInvoiceId(9L);
        link.setAmount(INVOICE_GROSS);

        InvInvoice inv = new InvInvoice();
        inv.setId(9L);
        inv.setTaxAmount(taxAmount);

        InvoiceDomainFacade facade = Mockito.mock(InvoiceDomainFacade.class);
        Mockito.when(facade.findLinksByBillIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(link));
        Mockito.when(facade.findById(9L)).thenReturn(inv);
        L1TestSupport.inject(svc, "invoiceFacade", facade);

        return svc;
    }

    @Test
    @DisplayName("有税额发票：勾稽净额＝价税−税额，不再误判超收货")
    void 净额口径不超收货() {
        BigDecimal net = svcWithTax(INVOICE_TAX).sumLinkedInvoiceNetAmount(1L);
        assertEquals(0, net.compareTo(RECEIPT_NET), "勾稽净额应为 " + RECEIPT_NET + "，实际 " + net);
        assertFalse(ThreeWayMatchService.invoiceExceedsReceiptCap(net, RECEIPT_NET),
                "净额 " + net + " 不得判超收货 " + RECEIPT_NET);
        // 反置器：同一组数据按旧口径（价税合计）必判超限 ⇒ 本断言不是恒假，回退口径即转红
        assertTrue(ThreeWayMatchService.invoiceExceedsReceiptCap(INVOICE_GROSS, RECEIPT_NET),
                "价税 " + INVOICE_GROSS + " 按旧口径应判超收货（复现 G#39）");
    }

    @Test
    @DisplayName("发票无税额信息：净额退化为价税合计（向后兼容）")
    void 无税额退化为价税() {
        BigDecimal net = svcWithTax(null).sumLinkedInvoiceNetAmount(1L);
        assertEquals(0, net.compareTo(INVOICE_GROSS), "无税额时应等于价税合计，实际 " + net);
        assertTrue(ThreeWayMatchService.invoiceExceedsReceiptCap(net, RECEIPT_NET),
                "无税额信息的 40,680 仍应判超收货 36,000（与旧口径一致）");
    }

    @Test
    @DisplayName("收货单无勾稽明细：净额为 0（不产生假超限）")
    void 无勾稽明细为零() {
        ThreeWayMatchService svc = L1TestSupport.mockService(ThreeWayMatchService.class);
        ExpBillMapper billMapper = Mockito.mock(ExpBillMapper.class);
        Mockito.when(billMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());
        L1TestSupport.inject(svc, "expBillMapper", billMapper);
        assertEquals(0, svc.sumLinkedInvoiceNetAmount(1L).compareTo(BigDecimal.ZERO));
    }
}
