// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.service.ExpenseBillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ExpenseBillService 单元测试。
 *
 * <p>覆盖两块核心金额逻辑：
 * <ol>
 *   <li>{@code calcPayable} —— 核定付款 = 核定报销 - 借款冲抵，负值归零（不得出现负付款）。</li>
 *   <li>{@code assertApproveTrilogy} —— 审批核定三层勾稽：只能核减不能核增、不得为负、不得全空。</li>
 * </ol>
 */
@DisplayName("L1-B-01 ExpenseBillService")
class L1B01ExpenseBillServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertBd(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + " 实际 " + actual);
    }

    @Test
    @DisplayName("calcPayable: 正常冲抵 = 核定报销 - 冲抵额")
    void calcPayable正常冲抵() {
        assertBd("700", ExpenseBillService.calcPayable(bd("1000"), bd("300")));
    }

    @Test
    @DisplayName("calcPayable: 冲抵额超过核定报销时归零，不产生负付款")
    void calcPayable超额冲抵归零() {
        assertBd("0", ExpenseBillService.calcPayable(bd("100"), bd("300")));
        assertBd("0", ExpenseBillService.calcPayable(bd("100"), bd("100")));
    }

    @Test
    @DisplayName("calcPayable: 入参为空按 0 处理")
    void calcPayable空值容错() {
        assertBd("0", ExpenseBillService.calcPayable(null, null));
        assertBd("500", ExpenseBillService.calcPayable(bd("500"), null));
        assertBd("0", ExpenseBillService.calcPayable(null, bd("500")));
    }

    @Test
    @DisplayName("审批核定: 超出申请金额应被拒（审批只能核减不能核增）")
    void 核定金额超出申请应拒绝() {
        ExpBill bill = new ExpBill();
        bill.setTotalAmount(bd("1000"));
        Exception e = assertThrows(Exception.class, () -> L1TestSupport.call(billSvc(),
                "assertApproveTrilogy",
                new Class<?>[]{ExpBill.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                bill, bd("2000"), null, null));
        assertEquals(true, e.getMessage() != null && e.getMessage().contains("超出申请金额"),
                "异常信息应说明超出申请金额，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("审批核定: 负数核定应被拒")
    void 核定金额为负应拒绝() {
        ExpBill bill = new ExpBill();
        bill.setTotalAmount(bd("1000"));
        Exception e = assertThrows(Exception.class, () -> L1TestSupport.call(billSvc(),
                "assertApproveTrilogy",
                new Class<?>[]{ExpBill.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                bill, bd("-1"), null, null));
        assertEquals(true, e.getMessage() != null && e.getMessage().contains("不能为负"),
                "异常信息应说明不能为负，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("审批核定: 三个核定金额全空应被拒")
    void 核定金额全空应拒绝() {
        ExpBill bill = new ExpBill();
        bill.setTotalAmount(bd("1000"));
        Exception e = assertThrows(Exception.class, () -> L1TestSupport.call(billSvc(),
                "assertApproveTrilogy",
                new Class<?>[]{ExpBill.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                bill, null, null, null));
        assertEquals(true, e.getMessage() != null && e.getMessage().contains("全为空"),
                "异常信息应说明不能全为空，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("审批核定: 合法核减通过（1000 → 800）")
    void 合法核减通过() {
        ExpBill bill = new ExpBill();
        bill.setTotalAmount(bd("1000"));
        L1TestSupport.call(billSvc(), "assertApproveTrilogy",
                new Class<?>[]{ExpBill.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                bill, bd("800"), null, null);
    }

    private static ExpenseBillService billSvc() {
        return L1TestSupport.mockService(ExpenseBillService.class);
    }
}
