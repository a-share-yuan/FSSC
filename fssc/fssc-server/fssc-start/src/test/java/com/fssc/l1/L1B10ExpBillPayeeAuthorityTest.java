package com.fssc.l1;

import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.service.ExpenseBillService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.system.service.NumberRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对公结算单的收款方名称与标题（服务端权威口径）。
 *
 * <p>三条口径：
 * <ol>
 *   <li>往来单位/采购订单选定万维 ⇒ exp_bill.payee_name 落「万维物流有限公司」，
 *       即使请求体里回传的是上一张单的「弘毅贸易有限公司」也以服务端反查为准；</li>
 *   <li>customer_id 与 po_id 指向不同供应商 ⇒ 当场抛业务异常且一行不写；</li>
 *   <li>title 原样落用户输入；用户没填时按单据编号生成，且与上一张单的标题不同（不沿用残留）。</li>
 * </ol>
 *
 * <p>走真实入口 {@code saveDraft}（非直接调私有方法），因此断言的是
 * {@code expBillMapper.insert} 实际收到的那个 ExpBill——即真正会落库的值。
 */
@DisplayName("L1-B-10 对公结算收款方名称与标题")
class L1B10ExpBillPayeeAuthorityTest {

    private static final String HONGYI = "弘毅贸易有限公司";
    private static final String WANWEI = "万维物流有限公司";
    private static final String SHENGTAI = "晟泰贸易有限公司";

    private ExpenseBillService svc;
    private ExpBillMapper expBillMapper;
    private CrmCustomerMapper crmCustomerMapper;
    private PoOrderMapper poOrderMapper;
    private NumberRuleService numberRuleService;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(ExpenseBillService.class);
        expBillMapper = Mockito.mock(ExpBillMapper.class);
        crmCustomerMapper = Mockito.mock(CrmCustomerMapper.class);
        poOrderMapper = Mockito.mock(PoOrderMapper.class);
        numberRuleService = Mockito.mock(NumberRuleService.class);
        L1TestSupport.inject(svc, "expBillMapper", expBillMapper);
        L1TestSupport.inject(svc, "crmCustomerMapper", crmCustomerMapper);
        L1TestSupport.inject(svc, "poOrderMapper", poOrderMapper);
        L1TestSupport.inject(svc, "numberRuleService", numberRuleService);
        // 新建腿需要 id 才能走完 insert 之后的行/附件写入
        Mockito.when(expBillMapper.insert(Mockito.any(ExpBill.class))).thenAnswer(inv -> {
            ((ExpBill) inv.getArgument(0)).setId(9001L);
            return 1;
        });
        LoginUser u = new LoginUser();
        u.setUserId(7L);
        u.setUserName("zhangsan");
        u.setNickName("张三");
        UserContext.set(u);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 编号取号桩：按调用顺序返回，模拟连续开多张单。 */
    private void numberRules(String... billNos) {
        String first = billNos[0];
        String[] rest = new String[billNos.length - 1];
        System.arraycopy(billNos, 1, rest, 0, rest.length);
        Mockito.when(numberRuleService.next(Mockito.any())).thenReturn(first, rest);
    }

    private void counterName(Long customerId, String name) {
        CrmCustomer c = new CrmCustomer();
        c.setId(customerId);
        c.setCustomerName(name);
        Mockito.when(crmCustomerMapper.selectById(Mockito.any())).thenReturn(c);
    }

    private void poSupplierName(Long poId, String name) {
        PoOrder po = new PoOrder();
        po.setId(poId);
        po.setSupplierName(name);
        Mockito.when(poOrderMapper.selectById(Mockito.any())).thenReturn(po);
    }

    /** 对公结算（PAY + SETTLE）草稿头：户名位先填入「上一张单的串」，用于证明服务端不信任前端值。 */
    private ExpBill settleBill(String title, Long customerId, Long poId) {
        ExpBill bill = new ExpBill();
        bill.setBillType("PAY");
        bill.setBillSubType("SETTLE");
        bill.setTitle(title);
        bill.setTotalAmount(new java.math.BigDecimal("12000"));
        bill.setPayTarget("SUPPLIER");
        bill.setCustomerId(customerId);
        bill.setPoId(poId);
        bill.setPayeeName(HONGYI);
        return bill;
    }

    /** insert 实收参数＝将落库的那一行。 */
    private ExpBill inserted() {
        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        Mockito.verify(expBillMapper).insert(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("往来单位+PO 都指万维时 payee_name 落万维，不保留前端回传的弘毅串")
    void 选万维的PO建单_payeeName必须是万维() {
        numberRules("FK202610120010");
        counterName(44L, WANWEI);
        poSupplierName(4L, WANWEI);

        svc.saveDraft(settleBill("V4人工-N5 万维对公结算", 44L, 4L), null, null);

        ExpBill written = inserted();
        assertEquals(WANWEI, written.getPayeeName(),
                "payee_name 必须是服务端按往来单位反查出的万维，实际: " + written.getPayeeName());
        assertNotEquals(HONGYI, written.getPayeeName(), "不得残留上一张单（弘毅）的户名");
        assertEquals(Long.valueOf(44L), written.getCustomerId());
        assertEquals(Long.valueOf(4L), written.getPoId());
    }

    @Test
    @DisplayName("仅选往来单位（无 PO）时 payee_name 取往来单位；PO 单独存在时取 PO 供应商")
    void 单一来源各自反查() {
        numberRules("FK202610120011", "FK202610120012");
        counterName(47L, SHENGTAI);
        svc.saveDraft(settleBill("只选往来单位", 47L, null), null, null);
        assertEquals(SHENGTAI, inserted().getPayeeName(), "无 PO 时以往来单位为权威");

        poSupplierName(3L, SHENGTAI);
        ExpBill onlyPo = settleBill("只挂 PO", null, 3L);
        onlyPo.setPoId(3L);
        svc.saveDraft(onlyPo, null, null);
        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        Mockito.verify(expBillMapper, Mockito.times(2)).insert(cap.capture());
        assertEquals(SHENGTAI, cap.getAllValues().get(1).getPayeeName(), "无往来单位时以 PO 供应商为权威");
    }

    @Test
    @DisplayName("往来单位指万维而 PO 指弘毅时当场拒绝，文案点名两个来源且一行不落库")
    void 两源指向不同供应商应拒绝且不落库() {
        numberRules("FK202610120013");
        counterName(44L, WANWEI);
        poSupplierName(2L, HONGYI);

        Exception e = assertThrows(Exception.class,
                () -> svc.saveDraft(settleBill("两源冲突", 44L, 2L), null, null));
        String msg = e.getMessage();
        assertTrue(msg != null && msg.contains(WANWEI) && msg.contains(HONGYI),
                "异常文案须同时点出来往单位与采购订单两个来源，实际: " + msg);
        assertTrue(msg.contains("44") && msg.contains("2"), "文案须带上两个来源的 id 供定位，实际: " + msg);
        Mockito.verify(expBillMapper, Mockito.never()).insert(Mockito.any(ExpBill.class));
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));
    }

    @Test
    @DisplayName("title 原样落用户输入；留空时按单据编号生成且不等于上一张单的标题")
    void title落用户原文留空按编号生成() {
        numberRules("FK202610120007", "FK202610120010");
        counterName(44L, WANWEI);
        poSupplierName(4L, WANWEI);

        String 用户输入 = "  V4人工-N5 万维对公结算  ";
        svc.saveDraft(settleBill(用户输入, 44L, 4L), null, null);

        // 第二张：用户没填标题 ⇒ 按编号生成，且不得等于第一张的标题（残留）
        ExpBill blankTitle = settleBill("", 44L, 4L);
        blankTitle.setTitle(null);
        svc.saveDraft(blankTitle, null, null);

        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        Mockito.verify(expBillMapper, Mockito.times(2)).insert(cap.capture());
        assertEquals(用户输入, cap.getAllValues().get(0).getTitle(), "用户输入的标题必须原样落库");

        ExpBill generated = cap.getAllValues().get(1);
        assertEquals("对公付款-FK202610120010", generated.getTitle(),
                "留空标题应按「对公付款-单据编号」生成，实际: " + generated.getTitle());
        assertNotEquals(用户输入, generated.getTitle(), "生成的标题不得沿用上一张单的标题");
        assertNotEquals("对公付款-FK202610120007", generated.getTitle(), "编号每单唯一，标题也不得相撞");
    }

    @Test
    @DisplayName("非对公单据（CLAIM）不套用本口径，收款方与标题保持原行为")
    void 报销单不受影响() {
        numberRules("CLM202610120001");
        ExpBill claim = new ExpBill();
        claim.setBillType("CLAIM");
        claim.setTitle("差旅报销");
        claim.setTotalAmount(new java.math.BigDecimal("600"));
        claim.setPayeeName("张三");
        svc.saveDraft(claim, null, null);

        ExpBill written = inserted();
        assertEquals("张三", written.getPayeeName(), "报销单收款方仍按原口径（申请人/前端值），不被往来单位反查改写");
        assertEquals("差旅报销", written.getTitle());
        Mockito.verify(crmCustomerMapper, Mockito.never()).selectById(Mockito.any());
        Mockito.verify(poOrderMapper, Mockito.never()).selectById(Mockito.any());
    }
}
