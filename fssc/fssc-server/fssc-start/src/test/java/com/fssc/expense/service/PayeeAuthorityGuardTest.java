// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.l1.L1TestSupport;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.system.service.NumberRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对公结算单收款方名称的服务端权威口径——来源缺失与编辑腿两侧。
 *
 * <p>口径：户名只由服务端按 customer_id → crm_customer.customer_name、
 * po_id → po_order.supplier_name 反查得出，前端回传的串一律不信；
 * 两个来源各自查不到主数据时当场拒绝，文案点明是哪一个 id 悬空，
 * 让页面能直接定位到要重选的那个选择器。
 *
 * <p>本类只覆盖两条易漏面：
 * <ol>
 *   <li>来源悬空（id 有值但主数据无此行）——新建腿与编辑腿都必须在写库前拦住；</li>
 *   <li>编辑腿——{@code updateById} 走的是「请求未回传则取库中现单」这条回落路径
 *       （{@code applyPayeeAuthority} 的第二入参 legacy），户名同样要被服务端反查覆盖。</li>
 * </ol>
 */
@DisplayName("收款方名称权威口径：来源悬空与编辑腿")
class PayeeAuthorityGuardTest {

    private static final String HONGYI = "弘毅贸易有限公司";
    private static final String WANWEI = "万维物流有限公司";
    private static final Long BILL_ID = 9001L;

    private ExpenseBillService svc;
    private ExpBillMapper expBillMapper;
    private CrmCustomerMapper crmCustomerMapper;
    private PoOrderMapper poOrderMapper;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(ExpenseBillService.class);
        expBillMapper = Mockito.mock(ExpBillMapper.class);
        crmCustomerMapper = Mockito.mock(CrmCustomerMapper.class);
        poOrderMapper = Mockito.mock(PoOrderMapper.class);
        NumberRuleService numberRuleService = Mockito.mock(NumberRuleService.class);
        L1TestSupport.inject(svc, "expBillMapper", expBillMapper);
        L1TestSupport.inject(svc, "crmCustomerMapper", crmCustomerMapper);
        L1TestSupport.inject(svc, "poOrderMapper", poOrderMapper);
        L1TestSupport.inject(svc, "numberRuleService", numberRuleService);
        Mockito.when(expBillMapper.insert(Mockito.any(ExpBill.class))).thenAnswer(inv -> {
            ((ExpBill) inv.getArgument(0)).setId(BILL_ID);
            return 1;
        });
        Mockito.when(numberRuleService.next(Mockito.any())).thenReturn("FK202610120099");
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

    /** 库内 SUPPLIER 型往来单位（crm_customer.customer_type=SUPPLIER 的账号形状）。 */
    private void supplier(Long id, String name) {
        CrmCustomer c = new CrmCustomer();
        c.setId(id);
        c.setCustomerName(name);
        c.setCustomerType("SUPPLIER");
        Mockito.when(crmCustomerMapper.selectById(id)).thenReturn(c);
    }

    private void po(Long id, String supplierName) {
        PoOrder o = new PoOrder();
        o.setId(id);
        o.setSupplierName(supplierName);
        Mockito.when(poOrderMapper.selectById(id)).thenReturn(o);
    }

    /** 新建腿（id 为空 → insert）。户名位先填上一张单的残留串。 */
    private ExpBill newBill(Long customerId, Long poId) {
        ExpBill bill = new ExpBill();
        bill.setBillType("PAY");
        bill.setBillSubType("SETTLE");
        bill.setTitle("对公结算");
        bill.setTotalAmount(new BigDecimal("60000"));
        bill.setCustomerId(customerId);
        bill.setPoId(poId);
        bill.setPayeeName(HONGYI);
        return bill;
    }

    /** 编辑腿（id 非空 → 先按库中现单校验归属，再 updateById）。 */
    private ExpBill editPayload(Long customerId, Long poId) {
        ExpBill bill = newBill(customerId, poId);
        bill.setId(BILL_ID);
        return bill;
    }

    /** 库中现单：草稿态、归属当前登录人、往来单位/PO 已按上一轮反查落好。 */
    private void storedBill(Long customerId, Long poId) {
        ExpBill exist = new ExpBill();
        exist.setId(BILL_ID);
        exist.setBillNo("FK202610120099");
        exist.setBillType("PAY");
        exist.setBillSubType("SETTLE");
        exist.setStatus("DRAFT");
        exist.setApplyUserId(7L);
        exist.setApplyUserName("张三");
        exist.setCustomerId(customerId);
        exist.setPoId(poId);
        Mockito.when(expBillMapper.selectById(BILL_ID)).thenReturn(exist);
    }

    private void assertNothingWritten() {
        Mockito.verify(expBillMapper, Mockito.never()).insert(Mockito.any(ExpBill.class));
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));
    }

    private ExpBill updatedOnce() {
        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        Mockito.verify(expBillMapper).updateById(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("customer_id 指向不存在的往来单位时拒绝，文案点名 customer_id= 且一行不落库")
    void 往来单位悬空应点名customer_id且不落库() {
        supplier(44L, WANWEI);

        BizException e = assertThrows(BizException.class,
                () -> svc.saveDraft(newBill(9999L, null), null, null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("customer_id=9999"),
                "文案须点名悬空的 customer_id 供页面定位，实际: " + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("po_id 指向不存在的采购订单时拒绝，文案点名 po_id= 且不牵连往来单位")
    void 采购订单悬空应点名po_id且不落库() {
        supplier(44L, WANWEI);
        po(2L, HONGYI);

        BizException e = assertThrows(BizException.class,
                () -> svc.saveDraft(newBill(44L, 8888L), null, null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("po_id=8888"),
                "文案须点名悬空的 po_id，实际: " + e.getMessage());
        assertTrue(!e.getMessage().contains("customer_id="),
                "往来单位那一条腿查得到，文案不应把它一起拖进来: " + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("编辑腿的悬空来源同样拦在写库之前")
    void 编辑腿悬空来源不落库() {
        supplier(44L, WANWEI);
        storedBill(44L, 4L);

        assertThrows(BizException.class, () -> svc.saveDraft(editPayload(44L, 7777L), null, null));
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));

        storedBill(44L, 4L);
        assertThrows(BizException.class, () -> svc.saveDraft(editPayload(9999L, null), null, null));
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));
    }

    @Test
    @DisplayName("编辑腿往来单位与PO同指万维时 payee_name 被服务端反查覆盖，不保留前端残留串")
    void 编辑腿请求带往来单位时户名反查覆盖() {
        supplier(44L, WANWEI);
        po(4L, WANWEI);
        storedBill(46L, 2L);

        svc.saveDraft(editPayload(44L, 4L), null, null);

        ExpBill written = updatedOnce();
        assertEquals(WANWEI, written.getPayeeName(),
                "户名必须是服务端按往来单位反查的万维，实际: " + written.getPayeeName());
        assertEquals(Long.valueOf(44L), written.getCustomerId());
    }

    @Test
    @DisplayName("编辑腿请求未回传往来单位时按库中现单的 customer_id 反查，户名仍不跟前端残留")
    void 编辑腿请求不带来源时按库中现单反查() {
        supplier(44L, WANWEI);
        po(4L, WANWEI);
        storedBill(44L, 4L);

        svc.saveDraft(editPayload(null, null), null, null);

        assertEquals(WANWEI, updatedOnce().getPayeeName(),
                "请求里空的也要按库中 customer_id/po_id 反查，不能留前端串");
    }

    @Test
    @DisplayName("编辑腿两源指向不同供应商时拒绝，文案同时点名两家与两个 id 且不落库")
    void 编辑腿两源冲突应拒绝且不落库() {
        supplier(44L, WANWEI);
        po(2L, HONGYI);
        storedBill(44L, 4L);

        BizException e = assertThrows(BizException.class,
                () -> svc.saveDraft(editPayload(44L, 2L), null, null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains(WANWEI) && e.getMessage().contains(HONGYI),
                "文案须同时点出来往单位与采购订单两家，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("customer_id=44") && e.getMessage().contains("po_id=2"),
                "文案须带上两个来源的 id，实际: " + e.getMessage());
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));
    }
}
