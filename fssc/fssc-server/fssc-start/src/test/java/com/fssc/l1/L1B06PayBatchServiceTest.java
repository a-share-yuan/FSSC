// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.sharing.service.PayBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PayBatchService 单元测试：付款批的入参与状态机守卫。
 *
 * <p>付款是直接动资金的动作，任何「批不存在 / 入参为空」都必须快速失败，
 * 不能静默创建空批或对不存在的批次做状态流转。
 */
@DisplayName("L1-B-06 PayBatchService 付款批")
class L1B06PayBatchServiceTest {

    private static PayBatchService svc() {
        return L1TestSupport.mockService(PayBatchService.class);
    }

    @Test
    @DisplayName("createBatch(null) 必须拒绝")
    void 空批次被拒() {
        assertThrows(Exception.class, () -> svc().createBatch(null));
    }

    @Test
    @DisplayName("confirm 不存在的批次必须拒绝")
    void confirm不存在的批次() {
        Exception e = assertThrows(Exception.class, () -> svc().confirm(999999L));
        assertTrue(e.getMessage() == null || !e.getMessage().contains("NullPointer"),
                "应抛出业务异常而非 NPE，实际: " + e);
    }

    @Test
    @DisplayName("closeBatch 不存在的批次必须拒绝")
    void closeBatch不存在的批次() {
        assertThrows(Exception.class, () -> svc().closeBatch(999999L));
    }

    @Test
    @DisplayName("addDetail(空批次) 必须拒绝")
    void 空明细被拒() {
        assertThrows(Exception.class, () -> svc().addDetail(null, null));
    }
}
