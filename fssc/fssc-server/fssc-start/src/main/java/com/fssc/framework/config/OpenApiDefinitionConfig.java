// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * OpenAPI 文档元信息（springdoc-openapi 3.1.0，Spring Boot 4 兼容线）。
 * <p>
 * 端点：/v3/api-docs（全量）、/v3/api-docs/tenant、/v3/api-docs/platform（分组，见 application.yml）；
 * UI：/swagger-ui.html。以上路径均已在 JwtAuthFilter 白名单放行，接口调试时通过右上角
 * Authorize 填入登录 /api/auth/login 返回的 JWT（Authorization: Bearer 头）。
 */
@OpenAPIDefinition(
        info = @Info(
                title = "集团财务共享平台 FSSC API",
                version = "1.0.0",
                description = "多租户 SaaS 财务共享系统后端接口（租户端 /api/tenant/**，平台端 /api/platform/**，认证 /api/auth/**）",
                contact = @Contact(name = "FSSC 平台组")),
        security = @SecurityRequirement(name = "bearerJwt"))
@SecurityScheme(
        name = "bearerJwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "登录接口返回的 token，填写时不带 Bearer 前缀")
public class OpenApiDefinitionConfig {
}
