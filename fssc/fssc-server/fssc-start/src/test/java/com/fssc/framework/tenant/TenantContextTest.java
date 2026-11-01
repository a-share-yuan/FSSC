// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.tenant;

import com.fssc.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantContext 单测：验证多租户铁律。
 * 纯逻辑测试，不启动 Spring 上下文（避免 JDK 26 下反射限制影响测试稳定性）。
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void callAs_设置并恢复上下文() {
        // 先建立一个上下文
        TenantContext.set(1L);
        // callAs 临时切换
        TenantContext.callAs(99L, () -> {
            assertEquals(99L, TenantContext.getTenantId());
            assertFalse(TenantContext.isIgnoreTenant());
        });
        // 执行后自动恢复旧值
        assertEquals(1L, TenantContext.getTenantId());
    }

    @Test
    void callAs_无旧上下文时执行后为null() {
        TenantContext.callAs(2L, () -> assertEquals(2L, TenantContext.getTenantId()));
        // 恢复为 null，此时没有上下文
        assertFalse(TenantContext.hasContext());
    }

    @Test
    void runIgnoreTenant_聚合模式返回null不抛异常() {
        TenantContext.runIgnoreTenant(() -> {
            assertTrue(TenantContext.isIgnoreTenant());
            // 忽略模式下 getTenantId 允许 null（跨租户聚合）
            assertNull(TenantContext.getTenantId());
        });
        assertFalse(TenantContext.isIgnoreTenant());
    }

    @Test
    void failClosed_业务查询无租户上下文必须抛异常() {
        // 有上下文但 tenantId=null（平台端误查业务表场景），getTenantId 必须失败
        TenantContext.setPlatform();
        assertThrows(BizException.class, TenantContext::getTenantId);
    }

    @Test
    void failClosed_完全无上下文也必须抛异常() {
        assertThrows(BizException.class, TenantContext::getTenantId);
    }

    @Test
    void platform_标识正确() {
        TenantContext.setPlatform();
        assertTrue(TenantContext.isPlatform());
        assertFalse(TenantContext.isIgnoreTenant());
    }
}
