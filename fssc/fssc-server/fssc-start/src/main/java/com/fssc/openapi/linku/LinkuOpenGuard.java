package com.fssc.openapi.linku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.Result;
import com.fssc.connector.callback.InboundCallbackVerifier;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.openapi.linku.mapper.LinkuOpenLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * LinkU 开放接口统一守卫（F-1 / F-4）：验签 → 幂等 → 执行 → 审计。
 *
 * <p>与既有回调 {@code SupplierReconController#guardAndExecute} 同范式，差异点：</p>
 * <ol>
 *   <li>查询端点（GET）无请求体，签名原文为 <b>空串</b>（对端须以空 body 签名）；</li>
 *   <li>幂等键取请求体的 {@code idempotencyKey}（而非回调的事件+对账单 id），
 *       以支撑「查询无关、写入可重放」的语义；列缺失时自动降级为无幂等保护并告警；</li>
 *   <li>全部 DB 访问包在 {@code TenantContext.runIgnoreTenant} 内：开放端点为匿名调用，
 *       无租户上下文，业务 SQL 一律以入参 tenantId 显式过滤（fail-closed 由入参校验兜底）。</li>
 * </ol>
 *
 * <p><b>未过签不落业务表、也不写审计表</b>（D-DEC-241 口径）：仅记应用日志（IP + 事件类型 + 拒绝码），
 * 不落请求体，避免未鉴权写入面。已过签的业务失败仍照常落库 —— 那是合法来源的审计线索。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkuOpenGuard {

    private final InboundCallbackVerifier verifier;
    private final LinkuOpenProperties props;
    private final LinkuOpenLogMapper logMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 具备副作用、可能抛异常的开放接口动作。 */
    @FunctionalInterface
    public interface LinkuAction {
        Object run() throws Exception;
    }

    /**
     * 统一入口。
     *
     * @param rawBody          参与验签的原始请求体（GET 为空串）
     * @param defaultEventType 未带 {@code X-BCP-Event-Type} 时的缺省事件类型
     * @param action           业务动作（内部自行按 tenantId 读写；本方法已建忽略租户上下文）
     */
    public Result<Object> guard(HttpServletRequest req, HttpServletResponse resp, String rawBody,
                                String defaultEventType, LinkuAction action) {
        long start = System.currentTimeMillis();
        String eventType = req.getHeader("X-BCP-Event-Type");
        if (!StringUtils.hasText(eventType)) {
            eventType = defaultEventType;
        }
        Map<String, Object> body = parseBody(rawBody);
        if (body.isEmpty()) {
            body = queryParams(req);
        }
        String idempotencyKey = str(body.get("idempotencyKey"));
        Long linkuReconId = asLong(body.get("linkuReconId"));
        Long supplierId = asLong(body.get("supplierId"));

        // 0) 密钥未注入：fail-closed，拒绝全部请求且不落审计
        if (!props.hasSecret()) {
            resp.setStatus(401);
            log.warn("[LinkU开放接口] 拒绝请求（密钥未配置） ip={} eventType={} uri={}",
                    req.getRemoteAddr(), eventType, req.getRequestURI());
            return Result.fail(401, "LINKU_OPEN_DISABLED|" + props.secretHint());
        }

        // 1) HMAC 验签 + 时间窗
        InboundCallbackVerifier.Result sig = verifier.verify(props.effectiveSecret(),
                req.getHeader("X-BCP-Timestamp"), req.getHeader("X-BCP-Signature"),
                rawBody, System.currentTimeMillis() / 1000, props.maxSkewSeconds());
        if (!sig.ok()) {
            boolean tsErr = sig.isTimestampError();
            resp.setStatus(tsErr ? 400 : 401);
            log.warn("[LinkU开放接口] 验签失败 ip={} eventType={} uri={} reason={}",
                    req.getRemoteAddr(), eventType, req.getRequestURI(), sig.reason());
            return Result.fail(tsErr ? 400 : 401,
                    tsErr ? "INVALID_LINKU_TIMESTAMP" : "INVALID_LINKU_SIGNATURE");
        }

        // 2) 幂等：同事件 + 同幂等键已成功处理过 → 直接返回 200 且不重复执行
        if (StringUtils.hasText(idempotencyKey) && alreadyProcessed(eventType, idempotencyKey)) {
            log.info("[LinkU开放接口] 幂等命中，跳过执行 eventType={} idempotencyKey={}", eventType, idempotencyKey);
            Map<String, Object> ack = new HashMap<>();
            ack.put("alreadyProcessed", true);
            ack.put("idempotencyKey", idempotencyKey);
            return Result.success(ack);
        }

        // 3) 执行（匿名：忽略租户过滤，业务 SQL 以入参 tenantId 显式过滤）
        try {
            final Object[] result = {null};
            TenantContext.runIgnoreTenant(() -> {
                try {
                    result[0] = action.run();
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception checked) {
                    throw new IllegalStateException(checked.getMessage(), checked);
                }
            });
            Object data = result[0];
            writeLog(eventType, linkuReconId, supplierId, rawBody, idempotencyKey,
                    "SUCCESS", null, (int) (System.currentTimeMillis() - start));
            Map<String, Object> ack = new HashMap<>();
            ack.put("alreadyProcessed", false);
            if (StringUtils.hasText(idempotencyKey)) {
                ack.put("idempotencyKey", idempotencyKey);
            }
            if (data != null) {
                ack.put("data", data);
            } else if (linkuReconId != null) {
                ack.put("linkuReconId", linkuReconId);
            }
            return Result.success(ack);
        } catch (Exception ex) {
            log.error("[LinkU开放接口] 处理失败 eventType={} idempotencyKey={} err={}",
                    eventType, idempotencyKey, ex.getMessage(), ex);
            writeLog(eventType, linkuReconId, supplierId, rawBody, idempotencyKey,
                    "FAILED", truncate(ex.getMessage(), 500), (int) (System.currentTimeMillis() - start));
            resp.setStatus(500);
            return Result.fail(500, "LINKU_OPEN_FAILED");
        }
        // 不在此清 TenantContext：runIgnoreTenant 内部已自动 restore，请求结束由 JwtAuthFilter 统一 clear。
    }

    /** 幂等判定；{@code idempotency_key} 列缺失时降级为「无幂等保护」并告警。 */
    private boolean alreadyProcessed(String eventType, String idempotencyKey) {
        final boolean[] hit = {false};
        try {
            TenantContext.runIgnoreTenant(
                    () -> hit[0] = logMapper.countSuccessByIdempotencyKey(eventType, idempotencyKey) > 0);
            return hit[0];
        } catch (DataAccessException e) {
            log.warn("[LinkU开放接口] 幂等判定不可用（linku_sync_log.idempotency_key 列缺失？），"
                    + "本次按无幂等保护处理，请执行 DDL 补列 err={}", e.getMessage());
            return false;
        }
    }

    /** 落审计；列缺失时自动去掉幂等键列重试。 */
    private void writeLog(String eventType, Long linkuReconId, Long supplierId, String rawBody,
                          String idempotencyKey, String status, String errorMsg, int durationMs) {
        try {
            TenantContext.runIgnoreTenant(() -> {
                try {
                    logMapper.insertWithKey(eventType, linkuReconId, supplierId, truncate(rawBody, 4000),
                            status, errorMsg, durationMs, idempotencyKey, LocalDateTime.now());
                } catch (DataAccessException e) {
                    logMapper.insertWithoutKey(eventType, linkuReconId, supplierId, truncate(rawBody, 4000),
                            status, errorMsg, durationMs, LocalDateTime.now());
                }
            });
        } catch (DataAccessException e) {
            // 审计本身失败不再上抛：原始业务结果已确定，别用次生异常掩盖它
            log.error("[LinkU开放接口] 审计落库失败 eventType={} status={} err={}", eventType, status, e.getMessage());
        }
    }

    private Map<String, Object> parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** GET 场景：把查询参数并入 Map，供幂等/审计上下文读取（tenantId 亦由此取得）。 */
    private Map<String, Object> queryParams(HttpServletRequest req) {
        Map<String, Object> m = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                m.put(k, v[0]);
            }
        });
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
