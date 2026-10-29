// SPDX-License-Identifier: Apache-2.0
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
 * 对公结算「采购订单」主键的落库与交叉校验口径。
 *
 * <p>归因（读代码得出，非猜测）：CorpPay 的采购订单下拉把选中项主键写进
 * {@code bill.ext_json.poId}（v-model），而 {@code exp_bill.po_id} 列的取值源是<b>顶层</b>
 * {@code bill.poId}；把 ext_json.poId 拷进顶层只发生在 {@code onBeforeSubmit}（提交审批腿），
 * 「保存草稿」不经该钩子。服务端闸 {@code applyPayeeAuthority} 读的是顶层 {@code bill.getPoId()}，
 * 保存草稿腿取到 null ⇒ {@code hasPo=false} ⇒ 两源不同家的交叉校验被整段跳过（冲突单被放过的真因）。
 *
 * <p>修复口径：闸用请求 ext_json.poId 兜底，取到即回写顶层列位，使「闸比较的 po_id」
 * ＝「insert/update 落库的 po_id」＝用户在下拉里选中那条 PO 的主键；顶层已带值时一律以顶层为准。
 *
 * <p>本类只走真实入口 {@code saveDraft}，断言的是 {@code expBillMapper.insert} 实收的那个 ExpBill
 * ＝真正会落库的值；反置形状均为「行为可观测」，不依赖反射读私有字段。
 */
@DisplayName("采购订单主键落库与交叉校验")
class PoIdAuthorityGuardTest {

    private static final String HONGYI_TRADE = "弘毅贸易有限公司";
    private static final String HONGYI_INFO = "弘毅信息有限公司";
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
        Mockito.when(numberRuleService.next(Mockito.any())).thenReturn("FK202610120199");
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

    /**
     * 对公结算（PAY + SETTLE）草稿：顶层 poId 与 ext_json 分开填，复刻下拉只写 ext_json 的真实形状。
     * topPoId=null 即「只在下拉里选了 PO、没经提交钩子」——正是保存草稿腿的形态。
     */
    private ExpBill settle(Long customerId, Long topPoId, String extJson) {
        ExpBill bill = new ExpBill();
        bill.setBillType("PAY");
        bill.setBillSubType("SETTLE");
        bill.setTitle("对公结算");
        bill.setTotalAmount(new BigDecimal("60000"));
        bill.setPayTarget("SUPPLIER");
        bill.setCustomerId(customerId);
        bill.setPoId(topPoId);
        bill.setExtJson(extJson);
        bill.setPayeeName(HONGYI_TRADE);
        return bill;
    }

    private ExpBill inserted() {
        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        Mockito.verify(expBillMapper).insert(cap.capture());
        return cap.getValue();
    }

    private void assertNothingWritten() {
        Mockito.verify(expBillMapper, Mockito.never()).insert(Mockito.any(ExpBill.class));
        Mockito.verify(expBillMapper, Mockito.never()).updateById(Mockito.any(ExpBill.class));
    }

    @Test
    @DisplayName("反置A: 保存草稿仅带 ext_json.poId 且两源不同家时，闸必须拦下且点名两家名＋两个 id")
    void 保存草稿仅extJson携带PO两源冲突必须拒绝() {
        supplier(46L, HONGYI_TRADE);
        po(1L, HONGYI_INFO);

        BizException e = assertThrows(BizException.class,
                () -> svc.saveDraft(settle(46L, null, "{\"poId\":1}"), null, null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains(HONGYI_TRADE) && e.getMessage().contains(HONGYI_INFO),
                "文案须同时点出来往单位与采购订单两家，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("customer_id=46") && e.getMessage().contains("po_id=1"),
                "文案须带上两个来源的 id 供定位，实际: " + e.getMessage());
        // 改前此场景被整段跳过（顶层 poId 为空），「冲突被放过」正是这一条
        assertNothingWritten();
    }

    @Test
    @DisplayName("反置B: 保存草稿 ext_json.poId 与往来单位同一家时，落库 po_id 必须＝选中的那条主键")
    void 保存草稿extJson同家时落库po_id为选中主键() {
        supplier(46L, HONGYI_TRADE);
        po(2L, HONGYI_TRADE);

        svc.saveDraft(settle(46L, null, "{\"poId\":2}"), null, null);

        ExpBill written = inserted();
        assertEquals(Long.valueOf(2L), written.getPoId(),
                "落库 po_id 必须是用户在下拉里选中那条 PO 的主键（从 ext_json 兜底回写列位），实际: " + written.getPoId());
        assertEquals(HONGYI_TRADE, written.getPayeeName(), "户名以往来单位反查为准");
    }

    @Test
    @DisplayName("反置C: 顶层已带 po_id 时闸只比这个最终落库值，ext_json 里的旧值不参与、也不改写列位")
    void 顶层poId为准extJson旧值不参与比较() {
        supplier(46L, HONGYI_TRADE);
        po(2L, HONGYI_TRADE); // 顶层 2＝同一家
        // 故意把 999 留在 ext_json：若闸错读了 ext_json，会因 selectById(999)=null 抛「采购订单不存在」而失败
        svc.saveDraft(settle(46L, 2L, "{\"poId\":999}"), null, null);

        assertEquals(Long.valueOf(2L), inserted().getPoId(), "闸与落库都必须用顶层最终 po_id=2，不能被 ext_json=999 顶掉");
    }

    @Test
    @DisplayName("回归护栏: 闸跑过到 insert 之间无任何代码改写 po_id——置一个孤立 poId，最终落库值仍是它")
    void 闸后到落库不改动po_id() {
        // 无往来单位（hasCustomer=false ⇒ 无从判冲突，闸不会因不匹配抛错），只带一个顶层 poId
        po(7L, "某某设备工程有限公司");

        svc.saveDraft(settle(null, 7L, null), null, null);

        ExpBill written = inserted();
        assertEquals(Long.valueOf(7L), written.getPoId(),
                "断言 applyPayeeAuthority 之后到 insert 之间没有代码把 po_id 改成别的值，实际: " + written.getPoId());
        assertEquals("某某设备工程有限公司", written.getPayeeName(),
                "户名由 PO 供应商反查得出（无往来单位时），且不牵连 po_id 变化");
    }

    @Test
    @DisplayName("ext_json 以字符串写 poId 同样被识别回写")
    void extJson字符串形式poId被识别() {
        supplier(46L, HONGYI_TRADE);
        po(5L, HONGYI_TRADE);

        svc.saveDraft(settle(46L, null, "{\"poId\":\"5\"}"), null, null);

        assertEquals(Long.valueOf(5L), inserted().getPoId(),
                "字符串写法的 poId 也要落进列位＝选中主键");
    }

    @Test
    @DisplayName("ext_json 悬空指向不存在的 PO 时按最终落库值点名 po_id 并拒绝")
    void extJson悬空PO点名po_id并拒绝() {
        supplier(46L, HONGYI_TRADE);
        // po(8888) 不桩定 → selectById 返回 null

        BizException e = assertThrows(BizException.class,
                () -> svc.saveDraft(settle(46L, null, "{\"poId\":8888}"), null, null));

        assertTrue(e.getMessage().contains("po_id=8888"),
                "文案须点名悬空的 po_id，实际: " + e.getMessage());
        assertNothingWritten();
    }
}
