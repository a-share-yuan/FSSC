// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.sharing.service.ThreeWayMatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ThreeWayMatchService 单元测试：三单匹配（合同 / 发票 / 入库）入口与路由。
 *
 * <p>契约要求：单据不存在时必须抛出业务异常，不得返回空 MatchRow 让调用方误判为「已匹配」。
 */
@DisplayName("L1-B-05 ThreeWayMatchService 三单匹配")
class L1B05ThreeWayMatchServiceTest {

    private static ThreeWayMatchService svc() {
        return L1TestSupport.mockService(ThreeWayMatchService.class);
    }

    @Test
    @DisplayName("单据不存在时抛业务异常，不得静默返回空匹配")
    void 单据不存在抛异常() {
        Exception e = assertThrows(Exception.class, () -> svc().check(999999L));
        assertTrue(e.getMessage() != null && e.getMessage().contains("单据不存在"),
                "异常信息应说明单据不存在，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("route: 单据不存在时抛业务异常（不得返回空路由）")
    void route单据不存在抛异常() {
        Exception e = assertThrows(Exception.class, () -> svc().route(999999L));
        assertTrue(e.getMessage() != null && e.getMessage().contains("单据不存在"),
                "异常信息应说明单据不存在，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("link: 单据不存在时抛异常（防止误关联）")
    void link单据不存在抛异常() {
        assertThrows(Exception.class, () -> svc().link(999999L, 1L, 1L));
    }
}
