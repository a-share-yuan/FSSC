package com.fssc.sharing.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 HTTP 凭证推送通道（{@code voucher.push.channel=http} 时生效，java.net.http 零新依赖）。
 *
 * <p>对接 同类系统 / IFS 只需配置、无需改代码：在「系统设置 → 全局参数」（sys_config）维护——</p>
 * <ul>
 *   <li>{@code voucher.push.url}：推送接口地址（POST JSON，请求体即 {@code {target, voucher...}}）；</li>
 *   <li>{@code voucher.push.token}：鉴权令牌，非空时以请求头 {@code Authorization: Bearer <token>} 发送；</li>
 *   <li>{@code voucher.push.externalNoPath}：响应 JSON 中回执单号的点号路径（默认 {@code data.docNo}）。</li>
 * </ul>
 *
 * <p>约定：超时 10s；一切失败（未配置/网络/非200/响应非法）均以 ok=false + message 返回，
 * 不抛穿，由 VoucherPushService 落库为 FAILED。每次实时读 sys_config，改配置即生效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpVoucherPushChannel implements VoucherPushChannel {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        // 注意：此处不能读 sys_config——@PostConstruct 在启动期执行，无租户上下文（TenantContext fail-closed）。
        // 配置在 push() 内按租户实时读取，未配置时以 FAILED 结果优雅返回。
        log.info("凭证推送 http 通道已装配（首次推送时按租户读取 voucher.push.url/token）");
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public VoucherPushResult push(String target, Map<String, Object> payload) {
        try {
            String url = getConfig("voucher.push.url", "");
            if (url.isBlank()) {
                return VoucherPushResult.fail(name(), "未配置推送接口地址（sys_config voucher.push.url）", null);
            }
            String token = getConfig("voucher.push.token", "");
            String externalNoPath = getConfig("voucher.push.externalNoPath", "data.docNo");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("target", target);
            body.putAll(payload);
            String reqJson = objectMapper.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8));
            if (!token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respBody = truncate(resp.body());
            if (resp.statusCode() != 200) {
                return VoucherPushResult.fail(name(), "推送接口 HTTP " + resp.statusCode(), respBody);
            }
            String externalNo = extractPath(resp.body(), externalNoPath);
            if (externalNo == null || externalNo.isBlank()) {
                return VoucherPushResult.fail(name(), "推送响应缺少回执单号（路径 " + externalNoPath + "）", respBody);
            }
            log.info("凭证推送 http 通道成功 target={} voucherNo={} externalNo={}",
                    target, payload.get("voucherNo"), externalNo);
            return VoucherPushResult.ok(externalNo, name(), respBody);
        } catch (Exception ex) {
            // 不抛穿：网络/超时/配置异常统一转失败结果
            log.warn("凭证推送 http 通道失败 target={}: {}", target, ex.getMessage());
            return VoucherPushResult.fail(name(), "推送通道异常: " + ex.getMessage(), null);
        }
    }

    /** 读取响应 JSON 的一层点号路径（如 data.docNo），缺失返回 null */
    private String extractPath(String json, String path) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            for (String seg : path.split("\\.")) {
                if (node == null) {
                    return null;
                }
                node = node.get(seg);
            }
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            log.warn("解析推送回执路径失败 path={}", path);
            return null;
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    /** 读取租户全局参数（sys_config voucher.push.*），无则返回默认 */
    private String getConfig(String key, String def) {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            if (c != null && c.getConfigValue() != null) {
                return c.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("读取凭证推送配置失败 key={}", key, e);
        }
        return def;
    }
}
