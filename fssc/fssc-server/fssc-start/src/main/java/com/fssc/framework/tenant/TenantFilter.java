// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.tenant;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.exception.FrameworkErrorRenderer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ② 状态防线（Order = 2，紧随 JwtAuthFilter 之后）。
 * <p>
 * 职责：对【已建立租户上下文】的请求，校验租户是否处于可服务状态
 * （冻结 / 过期欠费 / 注销 等由 {@link TenantStatusValidator} 判定）。
 * 过期欠费租户若不被拦截，就等于放它继续调接口、产生欠费数据。
 * <p>
 * 注意：本过滤器运行在 JwtAuthFilter 的 chain 内部，只校验、不清空上下文
 * （上下文由 JwtAuthFilter 在 finally 统一清空）。
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final TenantStatusValidator validator;
    private final FrameworkErrorRenderer errorRenderer;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            if (TenantContext.isPlatform()) {
                // 平台端不经过租户状态校验
                chain.doFilter(request, response);
                return;
            }
            if (TenantContext.hasContext() && !TenantContext.isIgnoreTenant()) {
                Long tenantId = TenantContext.getTenantId();
                String reason = validator.check(tenantId);
                if (reason != null) {
                    throw new BizException(ErrorCode.FORBIDDEN, reason);
                }
            }
            chain.doFilter(request, response);
        } catch (BizException ex) {
            errorRenderer.write(response, ex);
        } catch (Exception ex) {
            errorRenderer.writeSystemError(response, ex);
        }
    }
}
