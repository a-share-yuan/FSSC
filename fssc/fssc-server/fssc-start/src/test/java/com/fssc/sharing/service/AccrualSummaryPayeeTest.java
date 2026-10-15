package com.fssc.sharing.service;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.l1.L1TestSupport;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.service.NumberRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对公结算计提应付凭证摘要的取名口径。
 *
 * <p>口径：摘要要说清「这笔应付是谁家的」，取数只能落在服务端反查主数据后写进
 * {@code exp_bill.payee_name} 的那个户名上；单据标题是用户手填文本，页面换往来单位时
 * 不跟着变，一旦进了摘要就会让凭证指向另一家的往来账。
 * 户名为空（无往来单位也无采购订单的子类型）才回落标题，保证摘要不为空串。
 */
@DisplayName("计提应付凭证摘要取收款人名而非标题")
class AccrualSummaryPayeeTest {

    private static final String WANWEI = "万维物流有限公司";
    private static final String HONGYI = "弘毅贸易有限公司";
    /** 前端残留标题：上一张打开的单据名，与本页收款方无关。 */
    private static final String STALE_TITLE = "V4人工-N1 弘毅对公结算";

    private VoucherService svc;
    private OpVoucherMapper opVoucherMapper;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(VoucherService.class);
        opVoucherMapper = Mockito.mock(OpVoucherMapper.class);
        ExpBillMapper expBillMapper = Mockito.mock(ExpBillMapper.class);
        NumberRuleService numberRuleService = Mockito.mock(NumberRuleService.class);
        L1TestSupport.inject(svc, "opVoucherMapper", opVoucherMapper);
        L1TestSupport.inject(svc, "opVoucherLineMapper", Mockito.mock(OpVoucherLineMapper.class));
        L1TestSupport.inject(svc, "expBillMapper", expBillMapper);
        L1TestSupport.inject(svc, "numberRuleService", numberRuleService);
        Mockito.when(numberRuleService.next(Mockito.any())).thenReturn("JZ2026101200999");
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 已审批的对公结算单：户名是服务端反查落库的值，标题仍是用户手填的残留串。 */
    private ExpBill settledBill(String payeeName) {
        ExpBill bill = new ExpBill();
        bill.setId(4L);
        bill.setBillNo("FK202610120010");
        bill.setBillType("PAY");
        bill.setBillSubType("SETTLE");
        bill.setStatus("APPROVED");
        bill.setTitle(STALE_TITLE);
        bill.setPayeeName(payeeName);
        bill.setPoId(4L);
        bill.setPayableAmount(new BigDecimal("60000"));
        return bill;
    }

    private OpVoucher insertedVoucher() {
        ArgumentCaptor<OpVoucher> cap = ArgumentCaptor.forClass(OpVoucher.class);
        Mockito.verify(opVoucherMapper).insert(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("判据4: 摘要取 payee_name 的收款人名，不带标题里的另一家名字")
    void 摘要取收款人名而非标题() {
        svc.generateSettlementAccrual(settledBill(WANWEI));

        String summary = insertedVoucher().getSummary();
        assertEquals("对公结算计提应付-" + WANWEI, summary,
                "摘要必须点出这笔应付的收款方，实际: " + summary);
        assertFalse(summary.contains(HONGYI),
                "标题残留的「弘毅」不得出现在万维这张单的摘要里: " + summary);
    }

    @Test
    @DisplayName("判据4补: 户名有值时摘要与标题脱钩——改标题不影响摘要")
    void 摘要不随标题变() {
        ExpBill bill = settledBill(WANWEI);
        bill.setTitle("随手填的另一个标题");
        svc.generateSettlementAccrual(bill);

        assertTrue(insertedVoucher().getSummary().endsWith(WANWEI),
                "户名权威时标题一律不参与摘要");
    }

    @Test
    @DisplayName("判据4补: 户名为空的子类型回落标题，摘要不留空尾缀")
    void 户名为空回落标题() {
        svc.generateSettlementAccrual(settledBill("  "));

        assertEquals("对公结算计提应付-" + STALE_TITLE, insertedVoucher().getSummary(),
                "无往来单位也无采购订单时按既有标题兜底，避免摘要只剩前缀");
    }

    @Test
    @DisplayName("判据4补: 凭证来源与幂等键不因摘要改口径而变动")
    void 来源与幂等键不变() {
        svc.generateSettlementAccrual(settledBill(WANWEI));

        OpVoucher v = insertedVoucher();
        assertEquals(VoucherService.SOURCE_SETTLE, v.getSourceType());
        assertEquals(Long.valueOf(4L), v.getSourceDocId());
        assertEquals(Long.valueOf(4L), v.getBillId());
        assertEquals("DRAFT", v.getStatus());
    }

    /** 分录行摘要仍按既有口径出（本次只改凭证头摘要一处），此处只锁住「行不丢、方向与金额守恒」。 */
    @Test
    void 分录行不受摘要改口径影响() {
        OpVoucherLineMapper lineMapper = Mockito.mock(OpVoucherLineMapper.class);
        L1TestSupport.inject(svc, "opVoucherLineMapper", lineMapper);

        svc.generateSettlementAccrual(settledBill(WANWEI));

        ArgumentCaptor<OpVoucherLine> cap = ArgumentCaptor.forClass(OpVoucherLine.class);
        Mockito.verify(lineMapper, Mockito.atLeast(2)).insert(cap.capture());
        long debits = cap.getAllValues().stream().filter(l -> "DEBIT".equals(l.getDirection())).count();
        long credits = cap.getAllValues().stream().filter(l -> "CREDIT".equals(l.getDirection())).count();
        assertEquals(1, debits);
        assertEquals(1, credits);
        assertEquals(0, new BigDecimal("60000").compareTo(
                cap.getAllValues().get(0).getAmount()), "计提金额按应付口径整笔入借贷两行");
    }
}
