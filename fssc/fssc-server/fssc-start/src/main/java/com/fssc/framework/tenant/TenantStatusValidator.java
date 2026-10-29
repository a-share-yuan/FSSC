// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.tenant;

/**
 * 租户状态校验器（② 状态防线的可插拔点）。
 * <p>
 * 返回 null 表示租户状态正常、可继续；
 * 返回非 null 字符串表示拦截原因（"租户已冻结"/"套餐已过期欠费"等）。
 * <p>
 * 先给一个默认放行实现；平台运营域落地后，由 pf_tenant 表驱动的真实实现
 * 以 @Primary 覆盖本默认实现，校验 正常/冻结/过期/注销 状态。
 */
public interface TenantStatusValidator {

    String check(Long tenantId);
}
