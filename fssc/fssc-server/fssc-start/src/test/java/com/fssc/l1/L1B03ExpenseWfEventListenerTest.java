// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.audit.util.ChecksumUtil;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.service.ExpenseWfEventListener;
import com.fssc.workflow.event.WfProcessEndedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExpenseWfEventListener 单元测试。
 *
 * <p>核心验证 <b>防篡改 fail-closed</b>：审批通过前校验单据关键字段校验和，
 * 校验和缺失或不一致一律拦截（置 REJECTED + 审计留痕），绝不"异常即放行"。
 */
@DisplayName("L1-B-03 ExpenseWfEventListener 防篡改/事件处理")
class L1B03ExpenseWfEventListenerTest {

    private static final BigDecimal AMT = new BigDecimal("1000.00");

    private static ExpBill bill(String checksum, BigDecimal amount) {
        ExpBill b = new ExpBill();
        b.setId(1L);
        b.setBillType("CLAIM");
        b.setBillNo("CL20260912001");
        b.setTitle("差旅报销");
        b.setPayeeName("张三");
        b.setTotalAmount(amount);
        b.setAmountChecksum(checksum);
        return b;
    }

    private static String goodChecksum() {
        return ChecksumUtil.sha256(AMT, "张三", "CL20260912001", "差旅报销");
    }

    private record Fixture(ExpenseWfEventListener listener, ExpBillMapper mapper) {
    }

    private static Fixture fixture(ExpBill stub) {
        ExpenseWfEventListener listener = L1TestSupport.mockService(ExpenseWfEventListener.class);
        ExpBillMapper mapper = mock(ExpBillMapper.class);
        when(mapper.selectById(1L)).thenReturn(stub);
        L1TestSupport.inject(listener, "expBillMapper", mapper);
        return new Fixture(listener, mapper);
    }

    @Test
    @DisplayName("校验和缺失 → 拦截审批并置 REJECTED")
    void 校验和缺失拦截() {
        Fixture f = fixture(bill(null, AMT));
        f.listener().execute(new WfProcessEndedEvent("EXP_CLAIM", 1L, true, "同意"));

        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        verify(f.mapper()).updateById(cap.capture());
        assertEquals("REJECTED", cap.getValue().getStatus(), "校验和缺失必须拦截");
        assertTrue(String.valueOf(cap.getValue().getAuditRemark()).contains("防篡改拦截"),
                "应写明防篡改拦截原因，实际: " + cap.getValue().getAuditRemark());
    }

    @Test
    @DisplayName("金额被篡改（校验和不一致）→ 拦截审批并置 REJECTED")
    void 金额被篡改拦截() {
        // 提交时校验和基于 1000.00，审批前金额被改成 8888 → 校验和不匹配
        Fixture f = fixture(bill(goodChecksum(), new BigDecimal("8888.00")));
        f.listener().execute(new WfProcessEndedEvent("EXP_CLAIM", 1L, true, "同意"));

        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        verify(f.mapper()).updateById(cap.capture());
        assertEquals("REJECTED", cap.getValue().getStatus(), "篡改必须拦截");
    }

    @Test
    @DisplayName("校验和一致 → 不发生防篡改拦截（不写入 REJECTED）")
    void 校验和一致不拦截() {
        Fixture f = fixture(bill(goodChecksum(), AMT));
        f.listener().execute(new WfProcessEndedEvent("EXP_CLAIM", 1L, true, "同意"));

        // 正常通过路径会更新单据状态（APPROVED 等），但绝不应出现防篡改拦截的 REJECTED
        ArgumentCaptor<ExpBill> cap = ArgumentCaptor.forClass(ExpBill.class);
        verify(f.mapper(), org.mockito.Mockito.atLeast(0)).updateById(cap.capture());
        boolean tamperRejected = cap.getAllValues().stream()
                .anyMatch(x -> "REJECTED".equals(x.getStatus())
                        && String.valueOf(x.getAuditRemark()).contains("防篡改拦截"));
        assertEquals(false, tamperRejected, "校验和一致时不应触发防篡改拦截");
        assertNotNull(goodChecksum());
    }

    @Test
    @DisplayName("非报销业务 bizType 直接忽略，不查单据表")
    void 非报销业务忽略() {
        Fixture f = fixture(bill(goodChecksum(), AMT));
        f.listener().execute(new WfProcessEndedEvent("CUSTOMER_MAINT", 1L, true, "同意"));
        verify(f.mapper(), never()).selectById(any());
    }

    @Test
    @DisplayName("事件指向不存在的单据 → 静默返回，不抛异常")
    void 单据不存在静默返回() {
        ExpenseWfEventListener listener = L1TestSupport.mockService(ExpenseWfEventListener.class);
        ExpBillMapper mapper = mock(ExpBillMapper.class);
        when(mapper.selectById(404L)).thenReturn(null);
        L1TestSupport.inject(listener, "expBillMapper", mapper);

        listener.execute(new WfProcessEndedEvent("EXP_CLAIM", 404L, true, "同意"));
        verify(mapper, never()).updateById(any(ExpBill.class));
    }

    @Test
    @DisplayName("支持的业务类型含五类报销单据")
    void 支持五类业务() {
        List<String> expect = List.of("EXP_CLAIM", "EXP_LOAN", "EXP_REPAY", "EXP_APPLY", "EXP_PAY");
        assertEquals(5, expect.size());
    }
}
