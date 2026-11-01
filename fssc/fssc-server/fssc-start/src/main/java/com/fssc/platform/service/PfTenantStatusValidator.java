// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.service;

import com.fssc.framework.tenant.TenantStatusValidator;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.mapper.PfTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ② 状态防线真实现（DB 驱动，@Primary 覆盖默认放行实现）。
 * 每个租户请求由 TenantFilter 调用，校验租户生命周期状态：
 * FROZEN 冻结 / CANCELLED 注销 / 过期欠费 → 返回拦截原因，请求被 403 拦截。
 * <p>
 * TODO(性能)：单机高频下可加 30s 级缓存（Caffeine），当前先用直查保证正确性。
 */
@Component
@Primary
@RequiredArgsConstructor
public class PfTenantStatusValidator implements TenantStatusValidator {

    private final PfTenantMapper pfTenantMapper;

    @Override
    public String check(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        PfTenant t = pfTenantMapper.selectById(tenantId);
        if (t == null || (t.getDelFlag() != null && t.getDelFlag() == 1)) {
            return "租户不存在或已删除";
        }
        return switch (t.getStatus() == null ? "" : t.getStatus()) {
            case "NORMAL" -> {
                if (t.getExpireTime() != null && t.getExpireTime().isBefore(LocalDateTime.now())) {
                    yield "租户套餐已过期，功能已被冻结";
                }
                yield null;
            }
            case "APPLYING" -> "租户待平台审核";
            case "FROZEN" -> "租户已冻结，请联系平台运营";
            case "CANCELLING" -> "租户注销中";
            case "CANCELLED" -> "租户已注销";
            case "REJECTED" -> "租户入驻申请已被拒绝";
            default -> "租户状态异常";
        };
    }
}
