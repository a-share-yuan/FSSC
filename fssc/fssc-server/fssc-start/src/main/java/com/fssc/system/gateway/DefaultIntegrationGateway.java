// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.connector.credential.CredentialResolver;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.framework.trace.TraceIdFilter;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.entity.IntegrationCallLog;
import com.fssc.system.mapper.ExtInterfaceConfigMapper;
import com.fssc.system.mapper.IntegrationCallLogMapper;
import com.fssc.system.service.ExtInterfaceConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Default IntegrationGateway implementation: reads config from ext_interface_config,
 * performs HTTP calls with auth/timeout, and logs every call to integration_call_log.
 *
 * <p>Production can override with a @Primary bean that adds retry, circuit-breaker,
 * message transformation, or ESB routing — business services are agnostic.</p>
 *
 * <p><b>P0-2 唯一键强制化</b>：{@link #post(String, Object, String)} 同 (租户, 唯一键) 只真实外呼一次，
 * 之后回放首次 {@code response_body} 并落 {@code is_duplicate=1} 记录，防重复付款/重复推送。</p>
 *
 * <p><b>P0-3 失败记录与重试</b>：每次外呼记录 {@code trace_id}/{@code retry_count}；只读类通道
 * （AI_MODEL/OCR/INVOICE_VERIFY/INVOICE_CERTIFY，以及 ERP 查询动作 {PROVIDER}_{ACTION} 中
 * {@code *_GET}/{@code *_QUERY}）失败可有界重试，变更类无唯一键时禁止自动重试；
 * 历史失败由 {@link #retryFailed(int, int)} 供扫描 Job 串行重投。</p>
 *
 * <p><b>零回归</b>：鉴权头语义（apiKey→{@code Authorization: Bearer}、apiSecret→{@code X-API-Secret}）、
 * 掩码不外发、{@code error_code} 内嵌错误识别、15s 连接超时与超时解析逻辑均保持原样；
 * 2 参 {@link #post(String, Object)} 委托 3 参重载（key=null），行为与既有完全一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultIntegrationGateway implements IntegrationGateway {

    /** P0-3 只读类分类：重试安全（无副作用），失败可就地重试并被 Job 重投 */
    private static final Set<String> READ_ONLY_CATEGORIES =
            Set.of("AI_MODEL", "OCR", "INVOICE_VERIFY", "INVOICE_CERTIFY");

    /**
     * P0-3 ERP 只读动作后缀：多 ERP 通道分类为 {PROVIDER}_{ACTION}（如 {PROVIDER}_ORDER_GET /
     * {PROVIDER}_ORDER_QUERY），动作后缀命中即为查询类，无副作用，可与固定分类一样安全重试。
     */
    private static final List<String> READ_ONLY_SUFFIXES = List.of("_GET", "_QUERY");

    /** retryFailed 扫描用的等价 SQL 条件（RIGHT 取后缀，避免 LIKE 通配符歧义；与 READ_ONLY_SUFFIXES 同源） */
    private static final String ERP_READ_ONLY_SQL =
            "(RIGHT(UPPER(category), 4) = '_GET' OR RIGHT(UPPER(category), 6) = '_QUERY')";

    private final ExtInterfaceConfigService extConfigService;
    private final ExtInterfaceConfigMapper extConfigMapper;
    private final IntegrationCallLogMapper callLogMapper;
    private final IntegrationCallLogWriter logWriter;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** F 步：报文记录体积上限（字符）；≤0 表示不截断（全量）。默认 4000，与改造前一致。 */
    @Value("${fssc.connector.log.body-max:4000}")
    private int logBodyMax = 4000;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public GatewayResponse post(String category, Object body) {
        return post(category, body, null);
    }

    @Override
    public GatewayResponse post(String category, Object body, String idempotencyKey) {
        ExtInterfaceConfig cfg = getConfig(category);
        if (cfg == null) {
            return new GatewayResponse(0, null, 0, false,
                    "No enabled interface config for category: " + category);
        }
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return new GatewayResponse(0, null, 0, false, "Failed to serialize body: " + e.getMessage());
        }
        return doCall(cfg, "POST", jsonBody, idempotencyKey);
    }

    @Override
    public GatewayResponse get(String category) {
        ExtInterfaceConfig cfg = getConfig(category);
        if (cfg == null) {
            return new GatewayResponse(0, null, 0, false,
                    "No enabled interface config for category: " + category);
        }
        return doCall(cfg, "GET", null, null);
    }

    @Override
    public ExtInterfaceConfig getConfig(String category) {
        try {
            return extConfigService.getEnabledByCategory(category);
        } catch (Exception e) {
            log.warn("Failed to get interface config for category {}: {}", category, e.getMessage());
            return null;
        }
    }

    @Override
    public GatewayResponse testConnection(Long configId) {
        ExtInterfaceConfig cfg = extConfigMapper.selectById(configId);
        if (cfg == null) {
            return new GatewayResponse(0, null, 0, false, "Config not found: " + configId);
        }
        return doCall(cfg, "GET", null, null);
    }

    /**
     * P0-3 失败外呼重投：跨租户扫描近期 success=0 且未达上限的记录，逐条在原租户上下文内重投，
     * 复用同一 log 行（自增 retry_count 封顶），避免无限重试。返回本轮成功数。
     */
    @Override
    public int retryFailed(int maxRetry, int lookbackHours) {
        if (maxRetry <= 0) {
            return 0;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, lookbackHours));
        List<IntegrationCallLog> failed = new ArrayList<>();
        try {
            TenantContext.runIgnoreTenant(() -> failed.addAll(
                    callLogMapper.selectList(new LambdaQueryWrapper<IntegrationCallLog>()
                            .eq(IntegrationCallLog::getSuccess, 0)
                            .and(w -> w.isNull(IntegrationCallLog::getRetryCount)
                                    .or().lt(IntegrationCallLog::getRetryCount, maxRetry))
                            .ge(IntegrationCallLog::getCreateTime, since)
                            .and(w -> w.isNotNull(IntegrationCallLog::getIdempotencyKey)
                                    .ne(IntegrationCallLog::getIdempotencyKey, "")
                                    .or().in(IntegrationCallLog::getCategory, READ_ONLY_CATEGORIES)
                                    .or().apply(ERP_READ_ONLY_SQL))
                            .orderByAsc(IntegrationCallLog::getId)
                            .last("limit 200"))));
        } catch (Exception e) {
            log.warn("P0-3 失败外呼扫描异常: {}", e.getMessage());
            return 0;
        }
        if (failed.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (IntegrationCallLog row : failed) {
            try {
                Long tid = row.getTenantId();
                final boolean[] hit = {false};
                Runnable task = () -> hit[0] = replayOne(row);
                if (tid == null) {
                    task.run();
                } else {
                    TenantContext.callAs(tid, task);
                }
                if (hit[0]) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("P0-3 重投失败 logId={}: {}", row.getId(), e.getMessage());
            }
        }
        return ok;
    }

    /** 单条重投：按原 category 读当前生效配置 → 原样重发 → 回写同一 log 行 */
    private boolean replayOne(IntegrationCallLog row) {
        ExtInterfaceConfig cfg = getConfig(row.getCategory());
        if (cfg == null) {
            return false;
        }
        String method = (row.getMethod() == null || row.getMethod().isBlank()) ? "GET" : row.getMethod();
        GatewayResponse r = sendOnce(cfg, method, row.getRequestUrl(), row.getRequestBody());
        try {
            IntegrationCallLog upd = new IntegrationCallLog();
            upd.setId(row.getId());
            upd.setRetryCount((row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1);
            upd.setSuccess(r.isSuccess() ? 1 : 0);
            upd.setStatusCode(r.getStatusCode());
            upd.setResponseBody(r.bodyForLog());
            upd.setDurationMs((int) r.getDurationMs());
            upd.setErrorMsg(truncate(r.getError(), 1000));
            upd.setTraceId(newTrace());
            callLogMapper.updateById(upd);
        } catch (Exception e) {
            log.warn("P0-3 重投回写失败 logId={}: {}", row.getId(), e.getMessage());
        }
        return r.isSuccess();
    }

    /**
     * Core HTTP call method: 去重判定 → 有界重试 → 记录。
     */
    private GatewayResponse doCall(ExtInterfaceConfig cfg, String method, String jsonBody, String idempotencyKey) {
        String url = cfg.getApiUrl();
        if (url == null || url.isBlank()) {
            GatewayResponse resp = new GatewayResponse(0, null, 0, false, "API URL not configured");
            saveLog(cfg, method, url, jsonBody, resp, idempotencyKey, false, 0);
            return resp;
        }

        // P0-2 去重命中：不重复外呼，回放首次结果
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IntegrationCallLog prev = findPrior(cfg, idempotencyKey);
            if (prev != null) {
                GatewayResponse replay = new GatewayResponse(
                        prev.getStatusCode() == null ? 0 : prev.getStatusCode(),
                        prev.getResponseBody(),
                        0,
                        prev.getSuccess() != null && prev.getSuccess() == 1,
                        prev.getErrorMsg());
                replay.setDuplicate(true);
                saveLog(cfg, method, url, jsonBody, replay, idempotencyKey, true, 0);
                log.info("幂等命中回放 category={} key={} logId={}", cfg.getCategory(), idempotencyKey, prev.getId());
                return replay;
            }
        }

        int maxRetry = resolveMaxRetry(cfg, idempotencyKey);
        long start = System.currentTimeMillis();
        GatewayResponse result;
        int attempt = 0;
        while (true) {
            result = sendOnce(cfg, method, url, jsonBody);
            if (result.isSuccess() || attempt >= maxRetry) {
                break;
            }
            attempt++;
            if (log.isDebugEnabled()) {
                log.debug("外呼失败，第 {} 次重试 category={} url={} err={}",
                        attempt, cfg.getCategory(), url, result.getError());
            }
            sleepBackoff(attempt);
        }
        result = new GatewayResponse(result.getStatusCode(), result.getBody(),
                System.currentTimeMillis() - start, result.isSuccess(), result.getError());
        saveLog(cfg, method, url, jsonBody, result, idempotencyKey, false, attempt);
        return result;
    }

    /** 纯外呼：不含去重/记录/重试逻辑，首呼与重投共用；请求头与错误识别保持原实现语义 */
    private GatewayResponse sendOnce(ExtInterfaceConfig cfg, String method, String url, String jsonBody) {
        if (url == null || url.isBlank()) {
            return new GatewayResponse(0, null, 0, false, "API URL not configured");
        }
        long start = System.currentTimeMillis();
        try {
            int timeoutSec = parseTimeoutSeconds(cfg);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json; charset=utf-8");

            // R5-D-038 真值只从 Mapper/Service 出，掩码只存在于 HTTP 出参；此处拒把星号串当凭据外发
            String apiKey = credentialResolver.effective(cfg.getApiKey());
            if (apiKey != null && !apiKey.isBlank()) {
                if (ExtInterfaceConfig.looksMasked(apiKey)) {
                    log.warn("接口配置 apiKey 为掩码形态，已跳过外发 category={} code={}",
                            cfg.getCategory(), cfg.getInterfaceCode());
                } else {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
            }
            String apiSecret = credentialResolver.effective(cfg.getApiSecret());
            if (apiSecret != null && !apiSecret.isBlank()) {
                if (ExtInterfaceConfig.looksMasked(apiSecret)) {
                    log.warn("接口配置 apiSecret 为掩码形态，已跳过外发 category={} code={}",
                            cfg.getCategory(), cfg.getInterfaceCode());
                } else {
                    builder.header("X-API-Secret", apiSecret);
                }
            }

            if ("POST".equals(method) && jsonBody != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long duration = System.currentTimeMillis() - start;
            int code = resp.statusCode();
            boolean ok = code >= 200 && code < 300;
            String body = resp.body();
            String err = ok ? null : "HTTP " + code;
            if (ok) {
                // 百度系/部分网关故障时仍回 HTTP 200，错误藏在响应体（如 {"error_code":23,...}）。
                // 不在这里判出来的话，调用方会把"鉴权失败的错误体"当成正常返回，静默降级且无从告警。
                String providerErr = providerErrorOf(body);
                if (providerErr != null) {
                    ok = false;
                    err = "HTTP 200 但通道返回错误：" + providerErr;
                }
            }
            return new GatewayResponse(code, body, duration, ok, err);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Integration call failed: category={} url={} err={}",
                    cfg.getCategory(), url, msg);
            return new GatewayResponse(0, null, duration, false, msg);
        }
    }

    /** P0-2 查询首次调用记录（is_duplicate=0），用于去重回放 */
    private IntegrationCallLog findPrior(ExtInterfaceConfig cfg, String idempotencyKey) {
        try {
            Long tenantId = TenantContext.getTenantId();
            return callLogMapper.selectOne(new LambdaQueryWrapper<IntegrationCallLog>()
                    .eq(IntegrationCallLog::getCategory, cfg.getCategory())
                    .eq(IntegrationCallLog::getIdempotencyKey, idempotencyKey)
                    .eq(tenantId != null, IntegrationCallLog::getTenantId, tenantId)
                    .eq(IntegrationCallLog::getIsDuplicate, 0)
                    .orderByAsc(IntegrationCallLog::getId)
                    .last("limit 1"));
        } catch (Exception e) {
            log.warn("幂等键判重失败，按未命中处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * P0-3 内联重试次数：优先读 extra_config.retry；未配置时只读类默认 2 次、变更类默认 0 次。
     * 安全护栏：非只读类（付款/凭证推送）且无唯一键时一律 0 次，防重复提交。
     */
    private int resolveMaxRetry(ExtInterfaceConfig cfg, String idempotencyKey) {
        boolean hasIdem = idempotencyKey != null && !idempotencyKey.isBlank();
        boolean readOnly = isReadOnly(cfg.getCategory());
        Integer configured = retryFromConfig(cfg);
        if (configured != null) {
            if (!readOnly && !hasIdem) {
                return 0;
            }
            return Math.max(0, Math.min(5, configured));
        }
        return readOnly ? 2 : 0;
    }

    /** P0-3 只读判定：固定分类命中，或 ERP {PROVIDER}_{ACTION} 的查询类动作后缀命中。 */
    private static boolean isReadOnly(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        if (READ_ONLY_CATEGORIES.contains(category)) {
            return true;
        }
        String upper = category.toUpperCase();
        for (String suffix : READ_ONLY_SUFFIXES) {
            if (upper.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private Integer retryFromConfig(ExtInterfaceConfig cfg) {
        String extra = cfg.getExtraConfig();
        if (extra == null || extra.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(extra);
            if (node.has("retry") && node.get("retry").isNumber()) {
                return node.get("retry").asInt();
            }
        } catch (Exception ignored) {
            // extra_config 非合法 JSON 时忽略，走默认策略
        }
        return null;
    }

    private void sleepBackoff(int attempt) {
        long ms = Math.min(300L * (1L << Math.min(attempt - 1, 3)), 3000L);
        // 退避等待：LockSupport 不抛 InterruptedException；中断时提前返回（中断位保留）
        com.fssc.common.util.Backoff.pauseMillis(ms);
    }

    /** 通道侧错误信封识别：命中返回人话错误，未命中（含非 JSON 体）返回 null。 */
    private String providerErrorOf(String body) {
        if (body == null || body.isBlank() || body.charAt(0) != '{') {
            return null;
        }
        try {
            var node = objectMapper.readTree(body);
            var ec = node.get("error_code");
            if (ec != null && ec.isNumber() && ec.asInt() != 0) {
                return "error_code=" + ec.asInt() + " " + node.path("error_msg").asText("");
            }
            var err = node.get("error");
            if (err != null && !err.isNull()) {
                return err.isObject() ? err.path("message").asText(err.toString()) : err.asText();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Parse timeout from extraConfig JSON or fall back to 15s default */
    private int parseTimeoutSeconds(ExtInterfaceConfig cfg) {
        if (cfg.getExtraConfig() != null && !cfg.getExtraConfig().isBlank()) {
            try {
                var node = objectMapper.readTree(cfg.getExtraConfig());
                if (node.has("timeout") && node.get("timeout").isNumber()) {
                    return node.get("timeout").asInt();
                }
            } catch (Exception ignored) { /* fall through to default */ }
        }
        return 15;
    }

    /** Persist call log to integration_call_log (async-safe: failure does not block business logic) */
    private void saveLog(ExtInterfaceConfig cfg, String method, String url,
                         String reqBody, GatewayResponse resp,
                         String idempotencyKey, boolean duplicate, int retryCount) {
        try {
            IntegrationCallLog logEntry = new IntegrationCallLog();
            logEntry.setCategory(cfg.getCategory());
            logEntry.setInterfaceCode(cfg.getInterfaceCode());
            logEntry.setMethod(method);
            logEntry.setRequestUrl(url);
            logEntry.setRequestBody(truncateMaybe(reqBody, logBodyMax));
            logEntry.setResponseBody(truncateMaybe(resp.getBody(), logBodyMax));
            logEntry.setStatusCode(resp.getStatusCode());
            logEntry.setSuccess(resp.isSuccess() ? 1 : 0);
            logEntry.setDurationMs((int) resp.getDurationMs());
            logEntry.setErrorMsg(truncate(resp.getError(), 1000));
            logEntry.setIdempotencyKey(truncate(idempotencyKey, 128));
            logEntry.setIsDuplicate(duplicate ? 1 : 0);
            logEntry.setRetryCount(retryCount);
            logEntry.setTraceId(mdcTrace());
            logWriter.write(logEntry);
        } catch (Exception e) {
            log.warn("Failed to save integration call log: {}", e.getMessage());
        }
    }

    private String mdcTrace() {
        try {
            return MDC.get(TraceIdFilter.MDC_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    private static String newTrace() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** F 步：按配置记录；max<=0 表示不截断（全量记录）。 */
    private String truncateMaybe(String s, int max) {
        if (s == null || max <= 0) {
            return s;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
