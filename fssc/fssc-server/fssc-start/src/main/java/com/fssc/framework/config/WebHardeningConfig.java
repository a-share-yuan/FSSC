// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 维度4 安全加固（SEC-02/SEC-03）：
 * <ul>
 *   <li>安全响应头（SEC-03）：CSP / X-Content-Type-Options / X-Frame-Options / Referrer-Policy，
 *       阻断注入载荷的脚本执行与点击劫持。</li>
 *   <li>Origin/Referer 校验（SEC-02）：写操作（POST/PUT/DELETE/PATCH）若携带 Origin 或 Referer，
 *       其 host 必须为同源、localhost 系或 fssc.security.allowed-origins 白名单；纯 API 客户端
 *       （curl/服务间调用）不携带这些头直接放行。本项目鉴权走 Authorization 头不经 Cookie，
 *       此过滤器为 CSRF 纵深防御。</li>
 * </ul>
 */
@Configuration
public class WebHardeningConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                res.setHeader("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: blob:; object-src 'none'; frame-ancestors 'self'");
                res.setHeader("X-Content-Type-Options", "nosniff");
                res.setHeader("X-Frame-Options", "DENY");
                res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                chain.doFilter(req, res);
            }
        });
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("securityHeadersFilter");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> originGuardFilter(
            @Value("${fssc.security.allowed-origins:}") String allowedOrigins) {
        Set<String> extra = new HashSet<>();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).forEach(extra::add);
        }
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OncePerRequestFilter() {

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String m = request.getMethod();
                return !"POST".equals(m) && !"PUT".equals(m) && !"DELETE".equals(m) && !"PATCH".equals(m);
            }

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String origin = req.getHeader("Origin");
                String referer = req.getHeader("Referer");
                String src = origin != null ? origin : referer;
                if (src == null || src.isBlank()) {
                    // 非浏览器客户端（无 Origin/Referer）：放行，认证仍由 JwtAuthFilter 把守
                    chain.doFilter(req, res);
                    return;
                }
                String srcHost = hostOf(src);
                String selfHost = req.getServerName();
                if (srcHost == null) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin/Referer 非法");
                    return;
                }
                if (srcHost.equalsIgnoreCase(selfHost) || isLocal(srcHost) || extra.contains(srcHost)) {
                    chain.doFilter(req, res);
                    return;
                }
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin/Referer 校验失败（CSRF 防护）");
            }

            private String hostOf(String url) {
                try {
                    return new URI(url).getHost();
                } catch (URISyntaxException e) {
                    return null;
                }
            }

            private boolean isLocal(String host) {
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host)
                        || host.startsWith("127.");
            }
        });
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        reg.setName("originGuardFilter");
        return reg;
    }
}
