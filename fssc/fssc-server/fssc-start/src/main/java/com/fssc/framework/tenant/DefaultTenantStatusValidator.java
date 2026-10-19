// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.tenant;

import org.springframework.stereotype.Component;

/**
 * ② 状态防线默认实现（P1 占位）：一律放行。
 * <p>
 * P2 平台运营域实现后，由读取 pf_tenant 表的真实实现以 {@code @Primary} 覆盖本类，
 * 校验 正常 / 冻结 / 过期欠费 / 注销 状态，返回拦截原因或 null。
 * 这样状态防线的"接线"在 P1 就已经就位，后续只换实现、不动调用方。
 */
@Component
public class DefaultTenantStatusValidator implements TenantStatusValidator {

    @Override
    public String check(Long tenantId) {
        // P1 暂不校验租户生命周期状态，直接放行
        return null;
    }
}
