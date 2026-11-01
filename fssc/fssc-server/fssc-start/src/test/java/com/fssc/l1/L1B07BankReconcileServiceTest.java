// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.bank.service.BankReconcileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BankReconcileService 单元测试：银行对账。
 *
 * <p>契约要求：
 * <ol>
 *   <li>无未达流水时 run() 返回各计数为 0 的汇总 Map，不得抛异常（调度任务空跑是常态）。</li>
 *   <li>对不存在的流水做认领必须拒绝，防止凭空生成勾稽记录。</li>
 * </ol>
 */
@DisplayName("L1-B-07 BankReconcileService 银行对账")
class L1B07BankReconcileServiceTest {

    private static BankReconcileService svc() {
        return L1TestSupport.mockService(BankReconcileService.class);
    }

    @Test
    @DisplayName("无未达流水时 run() 返回零值汇总（空跑不报错）")
    void 无流水时返回零() {
        // statementMapper 由 mockService 自动注入 mock，selectList 默认返回空列表。
        // run() 的返回已从「条数 int」扩成汇总 Map（scanned/matched/unmatched/ambiguous/...），
        // 空跑的契约是「汇总里各计数为 0 且不抛异常」，不再是「返回值为 0」。
        Map<String, Object> r = svc().run();
        assertEquals(0, ((Number) r.get("scanned")).intValue());
        assertEquals(0, ((Number) r.get("matched")).intValue());
    }

    @Test
    @DisplayName("认领不存在的流水必须拒绝")
    void 认领不存在流水被拒() {
        assertThrows(Exception.class, () -> svc().claimReceipt(999999L, 999999L));
    }

    @Test
    @DisplayName("余额调节表: 参数为空时不得抛出 Error 级异常")
    void 调节表空参不崩() {
        try {
            Object r = svc().sheet(null, null);
            assertNotNull(r, "sheet 有返回时不得为 null");
        } catch (Throwable t) {
            // 业务异常（参数校验）属可预期；Error 级不可接受
            assertTrue(!(t instanceof Error), "不应抛出 Error 级异常: " + t);
        }
    }
}
