// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.expense.mapper.ExpBillRelationMapper;
import com.fssc.expense.mapper.ExpLoanOffsetMapper;
import com.fssc.l1.L1TestSupport;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报销 &lt; 借款 时，"员工应还多少"这件事只留在借款单上，不再进付款池。
 *
 * <p>实案（第八次执行 F4）：报销 3,900 冲抵借款 4,000 后，
 * {@code handleLoanClaimDiff} 走了 diff&lt;0 分支，往 {@code exp_bill_payment} 插了一条
 * {@code pay_status=WAITING}、{@code amount=100.00}、方向只写在 remark 里的"差额退款"记录。
 * {@code PayBatchService.groupWaiting} 只按 {@code WAITING}＋收款方归组、不辨方向
 * ⇒ 该记录被自动组成待执行<b>付款</b>批次，页面上与真付款批次一模一样，
 * 点【执行付款】就是把员工该还给公司的 100 元付给员工。同一笔钱又已有正规出口
 * ＝还款登记 RP_1【出纳确认还款到账】→RP_2【还款核销审核】（借款单由此归零转 REPAID）。</p>
 */
@DisplayName("报销<借款的差额退款：不进付款池，补付/平账口径不变")
class LoanClaimDiffPaymentRecordTest {

    private ExpenseWfEventListener svc;
    private ExpBillRelationMapper relationMapper;
    private ExpLoanOffsetMapper offsetMapper;
    private ExpBillPaymentMapper paymentMapper;

    @BeforeEach
    void setUp() throws Exception {
        svc = L1TestSupport.mockService(ExpenseWfEventListener.class);
        relationMapper = Mockito.mock(ExpBillRelationMapper.class);
        offsetMapper = Mockito.mock(ExpLoanOffsetMapper.class);
        paymentMapper = Mockito.mock(ExpBillPaymentMapper.class);
        L1TestSupport.inject(svc, "expBillRelationMapper", relationMapper);
        L1TestSupport.inject(svc, "expLoanOffsetMapper", offsetMapper);
        L1TestSupport.inject(svc, "expBillPaymentMapper", paymentMapper);
        when(relationMapper.selectList(ArgumentMatchers.<Wrapper<ExpBillRelation>>any()))
                .thenReturn(List.of());
    }

    private static ExpBill claim(String no, String total) {
        ExpBill b = new ExpBill();
        b.setId(4L);
        b.setBillNo(no);
        b.setApplyUserId(3808L);
        b.setApplyUserName("报销员工·人力资源部-01");
        b.setTotalAmount(new BigDecimal(total));
        return b;
    }

    private void stubOffsets(String amount) {
        ExpLoanOffset o = new ExpLoanOffset();
        o.setBillId(4L);
        o.setOffsetType("CLAIM");
        o.setOffsetAmount(new BigDecimal(amount));
        when(offsetMapper.selectList(ArgumentMatchers.<Wrapper<ExpLoanOffset>>any()))
                .thenReturn(List.of(o));
    }

    private BigDecimal diff(ExpBill bill) throws Exception {
        Method m = ExpenseWfEventListener.class.getDeclaredMethod("handleLoanClaimDiff", ExpBill.class);
        m.setAccessible(true);
        return (BigDecimal) m.invoke(svc, bill);
    }

    @Test
    @DisplayName("报销 3,900 < 借款冲抵 4,000：应付现金＝0，且一条付款记录都不许建")
    void refundLegCreatesNoPaymentRecord() throws Exception {
        stubOffsets("4000");

        BigDecimal payable = diff(claim("BX202610130002", "3900.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(payable), "报销<借款 ⇒ 本单无现金流出");
        verify(paymentMapper, never()).insert(ArgumentMatchers.<ExpBillPayment>any());
    }

    @Test
    @DisplayName("报销 5,000 > 借款冲抵 4,000：应付现金＝差额 1,000（补付仍由主链路统一生成）")
    void supplementLegReturnsDiffOnly() throws Exception {
        stubOffsets("4000");

        assertEquals(0, new BigDecimal("1000.00").compareTo(diff(claim("BX202610130009", "5000.00"))));
        verify(paymentMapper, never()).insert(ArgumentMatchers.<ExpBillPayment>any());
    }

    @Test
    @DisplayName("报销 4,000 ＝ 借款冲抵 4,000：平账，应付现金＝0")
    void equalLegSettlesWithoutCash() throws Exception {
        stubOffsets("4000");

        assertEquals(0, BigDecimal.ZERO.compareTo(diff(claim("BX202610130010", "4000.00"))));
        verify(paymentMapper, never()).insert(ArgumentMatchers.<ExpBillPayment>any());
    }

    @Test
    @DisplayName("行锁判定 isLockWait：只认锁等待/死锁，其余异常不许触发重试")
    void lockWaitPredicate() throws Exception {
        Method m = ExpenseWfEventListener.class.getDeclaredMethod("isLockWait", Throwable.class);
        m.setAccessible(true);

        assertEquals(Boolean.TRUE, m.invoke(null,
                new RuntimeException(new SQLException("Lock wait timeout exceeded; try restarting transaction"))));
        assertEquals(Boolean.TRUE, m.invoke(null, new IllegalStateException("Deadlock found when trying to get lock")));
        assertEquals(Boolean.FALSE, m.invoke(null, new IllegalArgumentException("字段不存在: used_amount")));
    }

    @Test
    @DisplayName("借款单账号落在 ext_json.accountNo 时，付款记录必须镜像出 bank_account（否则组批被防付错闸拒）")
    void loanAccountNoMirrorsIntoPaymentRecord() throws Exception {
        ExpBill loan = claim("JK202610130002", "2000.00");
        loan.setExtJson("{\"loanType\":\"LOAN\",\"payMethod\":\"BANK\",\"accountNo\":\"6217000012345678\",\"bankName\":\"中国工商银行上海分行\"}");
        ExpBillPayment p = new ExpBillPayment();

        invokeInherit(loan, p);

        assertEquals("6217000012345678", p.getBankAccount(), "放款付款记录要带收款账号，组批的防付错闸读的就是这一列");
        assertEquals("中国工商银行上海分行", p.getBankName());
        assertEquals("BANK", p.getPayMethod());
    }

    @Test
    @DisplayName("反向：单上已有 payee_account_no 时不得被 ext_json 覆盖（用户显式值优先）")
    void explicitPayeeAccountWins() throws Exception {
        ExpBill claim = claim("BX202610130003", "500.00");
        claim.setExtJson("{\"payMethod\":\"CASH\",\"accountNo\":\"9999-应被忽略\"}");
        ExpBillPayment p = new ExpBillPayment();
        p.setBankAccount("6217000012345678");

        invokeInherit(claim, p);

        assertEquals("6217000012345678", p.getBankAccount(), "已有值一律不覆盖（与户名镜像同口径）");
        assertEquals("CASH", p.getPayMethod());
    }

    private void invokeInherit(ExpBill bill, ExpBillPayment p) throws Exception {
        Method m = ExpenseWfEventListener.class.getDeclaredMethod("inheritPayMethodAndBank", ExpBill.class,
                ExpBillPayment.class);
        m.setAccessible(true);
        m.invoke(svc, bill, p);
    }
}
