// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.ExtInterfaceConfigMapper;
import com.fssc.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 HTTP OCR 通道（fssc.ocr.channel=http 时生效，HttpClient 零新依赖）。
 *
 * <p>接入任意真通道（百度/腾讯/开灵等）只需配置、无需改代码：在「系统设置 → 全局参数」（sys_config）维护——</p>
 * <ul>
 *   <li>{@code ocr.api.url}：识别接口地址（POST JSON，请求体 {"image": Base64, "fileName": 文件名}）；</li>
 *   <li>{@code ocr.api.token}：鉴权令牌，非空时以请求头 {@code Authorization: Bearer <token>} 发送；</li>
 *   <li>{@code ocr.api.mapping}：字段映射表 JSON（OcrResult字段 → 响应JSON一层点号路径），
 *       留空即用 {@link OcrFieldMapper#DEFAULT_MAPPING}（百度式字段名）。</li>
 * </ul>
 *
 * <p>约定：超时 10s；一切失败（未配置/网络/非200/响应非法）均以 ok=false + errorMsg 返回，不抛穿，
 * 由 OcrService 置页面 FAILED（与原先识别失败语义一致）。每次实时读 sys_config，改配置即生效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpOcrChannel implements OcrChannel {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final SysConfigMapper configMapper;
    private final ExtInterfaceConfigMapper extInterfaceConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        // 注意：此处不能读 sys_config——@PostConstruct 在启动期执行，无租户上下文（TenantContext fail-closed）。
        // 配置在 recognize() 内按租户实时读取，未配置时以 fail 结果优雅返回。
        log.info("OCR http 通道已装配（首次识别时按租户读取 ocr.api.url/token）；若当前通道为 mock 则本类不生效");
    }

    /** 分库改造：每次实时读的 sys_config 在 master 库；NOT_SUPPORTED 挂起外层
     *  OcrService 的 image 库 事务后取 master 连接，私有 getConfig 随之同连接。 */
    @Override
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public OcrResult recognize(byte[] image, String fileName) {
        try {
            String url = getConfig("ocr.api.url", "");
            if (url.isBlank()) {
                return OcrResult.fail("未配置 OCR 接口地址（sys_config ocr.api.url）");
            }
            String token = resolveCredential();
            String mapping = getConfig("ocr.api.mapping", "");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("image", image == null ? "" : Base64.getEncoder().encodeToString(image));
            body.put("fileName", fileName == null ? "" : fileName);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8));
            if (!token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return OcrResult.fail("OCR 接口 HTTP " + resp.statusCode());
            }
            OcrResult result = OcrFieldMapper.map(mapping, resp.body());
            if (result.isOk()) {
                log.info("OCR http 通道识别成功 fileName={} invoiceNo={}", fileName, result.getInvoiceNo());
            }
            return result;
        } catch (Exception ex) {
            // 不抛穿：网络/超时/配置异常统一转失败结果
            log.warn("OCR http 通道识别失败 fileName={}: {}", fileName, ex.getMessage());
            return OcrResult.fail("OCR 通道异常: " + ex.getMessage());
        }
    }

    /**
     * 凭据读取路径收敛：优先取统一凭据表 {@code ext_interface_config(category=OCR)}——
     * 集中托管、出参自动掩码，凭据不再只散落在 sys_config 明文；
     * 该分类未配置时回退 sys_config {@code ocr.api.token}，兼容历史配置、行为不变。
     * 取值顺序：api_secret（Bearer 令牌）→ api_key → sys_config 兜底。
     */
    private String resolveCredential() {
        try {
            ExtInterfaceConfig cfg = extInterfaceConfigMapper.selectOne(
                    new LambdaQueryWrapper<ExtInterfaceConfig>()
                            .eq(ExtInterfaceConfig::getCategory, "OCR")
                            .eq(ExtInterfaceConfig::getStatus, "ENABLED")
                            .orderByDesc(ExtInterfaceConfig::getIsDefault)
                            .last("limit 1"));
            if (cfg != null) {
                if (cfg.getApiSecret() != null && !cfg.getApiSecret().isBlank()) {
                    return cfg.getApiSecret();
                }
                if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
                    return cfg.getApiKey();
                }
            }
        } catch (Exception e) {
            log.warn("读取 OCR 凭据(ext_interface_config category=OCR)失败，回退 sys_config", e);
        }
        return getConfig("ocr.api.token", "");
    }

    /** 读取租户全局参数（sys_config ocr.api.*），无则返回默认——与 NotifyService.getConfig 同法 */
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
            log.warn("读取 OCR 通道配置失败 key={}", key, e);
        }
        return def;
    }
}
