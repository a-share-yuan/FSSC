package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.enums.SubjectType;
import com.fssc.framework.exception.FrameworkErrorRenderer;
import com.fssc.framework.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * ① 身份防线（Order = 1，最先执行）。
 * <pre>
 *   1. 放行白名单（登录、文档、健康检查、静态资源）。
 *   2. 从 Authorization: Bearer &lt;token&gt; 取出 Token，验签后还原 LoginUser。
 *      —— tenantId 只能来自这里，绝不读任何请求头（防前端伪造租户）。
 *   3. 写入 UserContext + TenantContext：
 *      租户用户：TenantContext.callAs(tenantId) 建立隔离上下文；
 *      平台用户：TenantContext.setPlatform()（tenantId=null，只碰 pf_* 表）。
 *   4. 两级路由校验：/api/platform/** 必须是 PLATFORM，/api/tenant/** 必须是 TENANT。
 * </pre>
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final FrameworkErrorRenderer errorRenderer;

    private static final AntPathMatcher M = new AntPathMatcher();

    private static final List<String> WHITE_LIST = List.of(
            // 健康探活：K8s 检查 / SLB / 监控系统不可能持有业务 Token，必须免鉴权
            "/api/health",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // 认证入口整段匿名：POST /api/auth/login（登录前无 Token）、/api/auth/tenant/register，
            // 以及 C-3 图形验证码下发 GET /api/auth/captcha —— 验证码本来就是"登录前"取的，
            // 必须在白名单内，否则取图请求自身会被 401 拦死（AntPathMatcher: /api/auth/** 覆盖子路径）。
            // 注意：/api/auth/profile|changePassword 等登录后动作靠 UserContext.getRequired() 自守，
            // 白名单只免除 Token 必填，不免除业务层的身份要求。
            "/api/auth/**",
            // 文件下载：凭一次性令牌访问（token 即身份），不能要求 Authorization 头
            "/api/tenant/system/file/download",
            // LinkU Webhook 回调：供应商确认回函，外部系统匿名调用
            "/api/tenant/pay/recon/linku-callback",
            "/api/tenant/pay/recon/linku-match-result",
            "/api/tenant/pay/recon/linku-diff-resolved",
            "/api/tenant/pay/recon/linku-recon-confirmed",
            "/api/tenant/pay/recon/linku-invoice-verified",
            // LinkU 开放接口（BCP → FSSC 数据通道）：外部系统匿名调用，
            // 安全由 HMAC 验签 + 时间窗 + 去重把关（LinkuOpenGuard），白名单只免除 Token 必填。
            "/api/open/linku/**",
            "/error",
            "/favicon.ico"
    );

    /**
     * OpenAPI 文档路径。默认放行（开发/联调），
     * 生产环境通过 {@code fssc.security.openapi-public=false} 关闭匿名访问。
     */
    private static final List<String> DOCS_PATHS = List.of(
            "/doc.html",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui",
            "/swagger-ui/**",
            "/webjars/**"
    );

    /** OpenAPI 文档是否允许匿名访问。默认 true；生产建议置 false。 */
    @Value("${fssc.security.openapi-public:true}")
    private boolean openapiPublic;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        try {
            // CORS 预检放行：OPTIONS 请求无 Token，交给 CorsFilter 处理，不在认证链拦截
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            // 白名单路径（如 /api/auth/**）匿名放行；但若携带了有效 Token，仍解析并填充 UserContext，
            // 以便登录用户的 profile 等接口能通过 UserContext.getRequired() 取到当前用户。
            // 解析失败不拦截（保持白名单的匿名语义）。
            if (isWhitelisted(uri)) {
                String whitelistToken = resolveToken(request);
                if (whitelistToken != null && !tokenBlacklistService.isBlacklisted(whitelistToken)) {
                    try {
                        LoginUser whitelistUser = tokenService.parseToken(whitelistToken);
                        UserContext.set(whitelistUser);
                        if (whitelistUser.isPlatform()) {
                            TenantContext.setPlatform();
                        } else {
                            TenantContext.set(whitelistUser.getTenantId());
                        }
                    } catch (Exception ignored) {
                        // Token 无效/过期：白名单路径仍匿名放行
                        UserContext.clear();
                        TenantContext.clear();
                    }
                }
                chain.doFilter(request, response);
                return;
            }

            String token = resolveToken(request);
            if (token == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "缺少认证 Token");
            }

            // 检查 Token 黑名单（强制下线使用）
            if (tokenBlacklistService.isBlacklisted(token)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Token 已被强制下线");
            }

            LoginUser user = tokenService.parseToken(token);
            UserContext.set(user);

            // 建立租户上下文（tenantId 来自已验签 Token，整请求生命周期有效）
            if (user.isPlatform()) {
                TenantContext.setPlatform();
            } else {
                TenantContext.set(user.getTenantId());
            }

            // 两级路由校验
            if (uri.startsWith("/api/platform/") && !user.isPlatform()) {
                throw new BizException(ErrorCode.PLATFORM_ONLY);
            }
            if (uri.startsWith("/api/tenant/") && !user.isTenant()) {
                throw new BizException(ErrorCode.TENANT_ONLY);
            }

            chain.doFilter(request, response);
        } catch (BizException ex) {
            // 过滤器异常不经过 @RestControllerAdvice，必须在此直接渲染 JSON
            errorRenderer.write(response, ex);
        } catch (Exception ex) {
            errorRenderer.writeSystemError(response, ex);
        } finally {
            // 请求结束清空，防止线程复用串号
            UserContext.clear();
            TenantContext.clear();
        }
    }

    private boolean isWhitelisted(String uri) {
        if (WHITE_LIST.stream().anyMatch(p -> M.match(p, uri))) {
            return true;
        }
        // 文档路径仅在 openapi-public=true 时匿名放行，否则要求携带 Token
        return openapiPublic && DOCS_PATHS.stream().anyMatch(p -> M.match(p, uri));
    }

    private String resolveToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7).trim();
        }
        return null;
    }
}
