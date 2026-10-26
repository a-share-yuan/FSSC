// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.l1.L1TestSupport;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.sharing.entity.VoucherTemplate;
import com.fssc.system.service.NumberRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #3b：凭证摘要统一「收款人优先、为空回落标题」。
 *
 * <p>全仓 6 处摘要位（含已上线的两处）收敛到同一条口径——{@code VoucherService#payeeNameOrTitle}。
 * 收款人取服务端反查主数据后写进 {@code exp_bill.payee_name} 的户名；标题是用户手填文本、页面换
 * 往来单位时不跟着变，只有收款人为空的老单据/子类型才回落标题，保证这类单据行为逐字不变、摘要非空。
 *
 * <p>覆盖面：
 * <ol>
 *   <li>端到端走真实出证口 {@code generateByTemplate(billId, sourceType)}——模板里 {@code {billTitle}}
 *       这一位现在喂的是「收款人优先」而非裸标题（对应改动点之一的模板摘要）；</li>
 *   <li>对公共口径函数 {@code payeeNameOrTitle} 逐分支断言（收款人非空 / 空白 / null / 标题也为 null）。
 *       其余摘要位（EXPENSE／EXPENSE_PAY 直取标题两处、两处合同模板）都委派到这一个函数，口径同此锁定。</li>
 * </ol>
 */
@DisplayName("#3b 凭证摘要收款人优先为空回落标题")
class VoucherSummaryPayeeFallbackTest {

    private static final String WANWEI = "万维物流有限公司";
    /** 前端残留标题：上一张打开的单据名，与本页收款方无关。 */
    private static final String STALE_TITLE = "V4人工-N1 弘毅对公结算";

    private VoucherService svc;
    private OpVoucherMapper opVoucherMapper;
    private ExpBillMapper expBillMapper;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(VoucherService.class);
        opVoucherMapper = Mockito.mock(OpVoucherMapper.class);
        expBillMapper = Mockito.mock(ExpBillMapper.class);
        L1TestSupport.inject(svc, "opVoucherMapper", opVoucherMapper);
        L1TestSupport.inject(svc, "opVoucherLineMapper", Mockito.mock(OpVoucherLineMapper.class));
        L1TestSupport.inject(svc, "expBillMapper", expBillMapper);
        L1TestSupport.inject(svc, "numberRuleService", Mockito.mock(NumberRuleService.class));
        // findSubjectIdByCode 走 selectList——不桩则 mock 回 null，方法内 .isEmpty() 直接 NPE
        MdSubjectMapper mdSubjectMapper = Mockito.mock(MdSubjectMapper.class);
        Mockito.when(mdSubjectMapper.selectList(Mockito.any())).thenReturn(Collections.emptyList());
        L1TestSupport.inject(svc, "mdSubjectMapper", mdSubjectMapper);
        NumberRuleService numberRuleService = Mockito.mock(NumberRuleService.class);
        Mockito.when(numberRuleService.next(Mockito.any())).thenReturn("JZ2026101200888");
        L1TestSupport.inject(svc, "numberRuleService", numberRuleService);
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 已审批对公结算单：户名是服务端反查落库的值，标题仍是用户手填的残留串。 */
    private ExpBill approvedBill(String payeeName, String title) {
        ExpBill bill = new ExpBill();
        bill.setId(1L);
        bill.setBillNo("FK202610120010");
        bill.setBillType("PAY");
        bill.setBillSubType("SETTLE");
        bill.setStatus("APPROVED");
        bill.setTitle(title);
        bill.setPayeeName(payeeName);
        bill.setTotalAmount(new BigDecimal("60000"));
        // companyId 留空：模板出证路径据此跳过期间结账校验（periodService 不参与本次断言）
        bill.setPeriodYear(2026);
        bill.setPeriodMonth(10);
        return bill;
    }

    private void stubTemplate(String summaryTemplate) {
        VoucherTemplate template = new VoucherTemplate();
        template.setSummaryTemplate(summaryTemplate);
        template.setDebitSubjectCode("1123");
        template.setDebitSubjectName("预付账款");
        template.setCreditSubjectCode("2202");
        template.setCreditSubjectName("应付账款");
        VoucherTemplateService voucherTemplateService = Mockito.mock(VoucherTemplateService.class);
        Mockito.when(voucherTemplateService.resolveTemplate(Mockito.any(), Mockito.any())).thenReturn(template);
        L1TestSupport.inject(svc, "voucherTemplateService", voucherTemplateService);
    }

    private String voucherSummaryFromTemplatePath(ExpBill bill) {
        Mockito.when(expBillMapper.selectById(bill.getId())).thenReturn(bill);
        svc.generateByTemplate(bill.getId(), "EXPENSE_PAY");
        ArgumentCaptor<OpVoucher> cap = ArgumentCaptor.forClass(OpVoucher.class);
        Mockito.verify(opVoucherMapper).insert(cap.capture());
        return cap.getValue().getSummary();
    }

    @Test
    @DisplayName("模板摘要 {billTitle} 位现喂收款人优先名，不带残留标题的另一家")
    void 模板摘要取收款人名而非标题() {
        stubTemplate("对公结算-{billTitle}");

        String summary = voucherSummaryFromTemplatePath(approvedBill(WANWEI, STALE_TITLE));

        assertEquals("对公结算-" + WANWEI, summary, "模板位应替换为收款人名，实际: " + summary);
        assertFalse(summary.contains("弘毅"), "标题残留的「弘毅」不得出现在万维这张单的摘要里: " + summary);
    }

    @Test
    @DisplayName("模板无 summaryTemplate（走 else 分支）同样收款人优先")
    void 模板无摘要位else分支也取收款人() {
        stubTemplate(null);

        String summary = voucherSummaryFromTemplatePath(approvedBill(WANWEI, STALE_TITLE));

        assertEquals(WANWEI, summary, "无模板摘要时 else 分支取收款人名，实际: " + summary);
    }

    @Test
    @DisplayName("收款人为空才回落标题，摘要不留空尾缀（老单据行为逐字不变）")
    void 收款人为空回落标题() {
        stubTemplate("对公结算-{billTitle}");

        String summary = voucherSummaryFromTemplatePath(approvedBill("   ", STALE_TITLE));

        assertEquals("对公结算-" + STALE_TITLE, summary,
                "收款人空白时按既有标题兜底，与本口径改前逐字一致，实际: " + summary);
    }

    @Test
    @DisplayName("公共口径函数逐分支：非空取收款人／空白或 null 取标题／两者皆 null 取空串")
    void 公共口径函数逐分支() {
        ExpBill withPayee = new ExpBill();
        withPayee.setPayeeName(WANWEI);
        withPayee.setTitle(STALE_TITLE);
        assertEquals(WANWEI, payeeNameOrTitle(withPayee), "收款人非空即取收款人");

        ExpBill blankPayee = new ExpBill();
        blankPayee.setPayeeName("  ");
        blankPayee.setTitle(STALE_TITLE);
        assertEquals(STALE_TITLE, payeeNameOrTitle(blankPayee), "收款人空白回落标题");

        ExpBill nullPayee = new ExpBill();
        nullPayee.setTitle(STALE_TITLE);
        assertEquals(STALE_TITLE, payeeNameOrTitle(nullPayee), "收款人 null 回落标题");

        ExpBill bothNull = new ExpBill();
        assertEquals("", payeeNameOrTitle(bothNull), "两者皆空取空串，摘要不抛 NPE");
    }

    @SuppressWarnings("unchecked")
    private String payeeNameOrTitle(ExpBill bill) {
        return (String) L1TestSupport.call(svc, "payeeNameOrTitle", new Class[]{ExpBill.class}, bill);
    }
}
