package com.fssc.framework.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ⓪ 链路追踪防线（Order = 0，早于 JwtAuthFilter=1 / TenantFilter=2）。
 *
 * <p>解决的问题：全项目原先无 traceId，一次请求在日志里散成几十行无法串联，
 * 生产排障只能靠时间戳猜。本过滤器为每个请求建立唯一 traceId：
 * <ol>
 *   <li>优先取上游透传的 {@code X-Trace-Id}（网关 / 调用方指定），并做格式校验，
 *       非法值一律丢弃重新生成（防注入脏字符进日志）；</li>
 *   <li>写入 SLF4J {@link MDC}，由 logback pattern 的 {@code %X{traceId}} 输出到每一行日志；</li>
 *   <li>回写响应头 {@code X-Trace-Id}，前端/调用方可直接拿到，报障时一句话即可定位；</li>
 *   <li>请求结束 {@code MDC.remove}，避免线程池复用导致串号（与 UserContext/TenantContext 同口径）。</li>
 * </ol>
 *
 * <p>另记录慢请求：耗时 ≥ {@link #SLOW_MS} 的请求打 WARN，作为无 APM 时的兜底观测手段。
 * 常规耗时/错误率指标由 Micrometer（{@code http.server.requests}）提供，不在此重复采集。
 */
@Slf4j
@Component
@Order(0)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC key，与 logback pattern 中的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";
    /** 请求/响应头名 */
    public static final String HEADER = "X-Trace-Id";
    /** 慢请求阈值（毫秒） */
    private static final long SLOW_MS = 1000L;
    /** 允许透传的 traceId 形态：8–64 位字母数字与连字符，防止把换行/控制字符写进日志 */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || !VALID.matcher(traceId.trim()).matches()) {
            traceId = newTraceId();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= SLOW_MS) {
                log.warn("慢请求 {} {} 耗时 {}ms status={}",
                        request.getMethod(), request.getRequestURI(), cost, response.getStatus());
            }
            MDC.remove(MDC_KEY);
        }
    }

    /** 16 位十六进制随机段（去掉 UUID 连字符后截断），全局唯一且比自增更有辨识度 */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
