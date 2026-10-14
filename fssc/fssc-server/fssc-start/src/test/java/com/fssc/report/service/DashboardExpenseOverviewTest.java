// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.service;

import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.l1.L1TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D-V4R-40：费用概览在「本人口径下没有任何预算行」时不得 500。
 *
 * <p>实案（2026-10-13 17:2x 运行态，共享会计 Y000185 打开 管理报表›部门报销排名）：
 * 概览四格调 {@code GET /tenant/dashboard/expense/overview} 回 {@code {"code":500,"msg":"系统异常，请稍后重试"}}，
 * 页面「费用概览」卡整块显示「本次未刷新成功」。栈顶
 * {@code NullPointerException: Cannot invoke "java.util.Map.get(Object)" because "row" is null}
 * at DashboardService.expenseOverview(DashboardService.java:203)——
 * 该处判过 {@code !bgRows.isEmpty()} 却没判 {@code get(0)} 本身：
 * MySQL 的 sum() 在零行命中时仍回一行且三列全 NULL，MyBatis 把整行 NULL 映射成 list 里的 null 元素，
 * 于是 isEmpty()=false 而 get(0)=null。同一方法里前三枚指标走的是已带兜底的 {@code scalar(...)}，只有第四枚漏了。</p>
 */
@DisplayName("费用概览第四格：预算行取不到时回落 0，不抛 NPE")
class DashboardExpenseOverviewTest {

    private DashboardService svc;
    private BgItemMapper bgItemMapper;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(DashboardService.class);
        bgItemMapper = Mockito.mock(BgItemMapper.class);
        L1TestSupport.inject(svc, "bgItemMapper", bgItemMapper);
    }

    /** sum() 零行命中：一行、三列全 NULL ⇒ list 里躺着一枚 null 元素 */
    private static List<Map<String, Object>> nullRowOnly() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(null);
        return rows;
    }

    @Test
    @DisplayName("预算口径查不到行：概览照常返回，执行率回落 0（改前此处 NPE→整卡 500）")
    void overviewSurvivesNullAggregateRow() {
        Mockito.when(bgItemMapper.selectMaps(ArgumentMatchers.any())).thenReturn(nullRowOnly());

        Map<String, Object> out = assertDoesNotThrow(() -> svc.expenseOverview(),
                "本人口径下没有 bg_item 行时，费用概览不得抛异常");
        assertEquals(BigDecimal.ZERO, new BigDecimal(String.valueOf(out.get("budgetRate"))),
                "取不到预算行时执行率必须回落 0，而不是把空聚合当 0/0 去除");
        assertEquals(BigDecimal.ZERO, new BigDecimal(String.valueOf(out.get("paidAmount"))));
    }

    @Test
    @DisplayName("预算行齐备：执行率按 used/(total+adjust) 算，修 NPE 没改坏正常口径")
    void overviewStillComputesRateWhenRowsPresent() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("total", new BigDecimal("100000.00"));
        row.put("adjust", new BigDecimal("18000.00"));
        row.put("used", new BigDecimal("11800.00"));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        Mockito.when(bgItemMapper.selectMaps(ArgumentMatchers.any())).thenReturn(rows);

        Map<String, Object> out = svc.expenseOverview();
        assertEquals(0, new BigDecimal(String.valueOf(out.get("budgetRate")))
                .compareTo(new BigDecimal("10.00")), "11,800 /(100,000+18,000)=10.00%");
    }
}
