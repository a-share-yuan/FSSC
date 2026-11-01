// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Web 层配置。这里只放跨域；认证/租户过滤器由各自的 @Component + @Order 注册，
 * CORS 过滤器用最高优先级，确保预检 OPTIONS 先于认证链路处理。
 * <p>
 * fssc.cors.allowed-origin-patterns 默认 * 仅供开发/演示；
 * 生产必须配置为前端真实域名清单（逗号分隔），不得使用通配。
 */
@Configuration
public class WebMvcConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${fssc.cors.allowed-origin-patterns:*}") String allowedOriginPatterns) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带 Cookie / 鉴权头
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        // CorsFilter 默认 Ordered.HIGHEST_PRECEDENCE，早于 JwtAuthFilter(Order=1)
        return new CorsFilter(source);
    }
}
