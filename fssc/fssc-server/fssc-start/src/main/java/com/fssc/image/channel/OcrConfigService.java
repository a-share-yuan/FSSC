// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OCR 通道配置的管理员读写服务（系统管理 → OCR通道配置 页面后端）。
 *
 * <p>配置全部落 sys_config（租户级）：ocr.channel（gateway/mock/http/paddle，空=跟随 yml 默认 gateway）、
 * ocr.api.url / ocr.api.token / ocr.api.mapping（http 通用通道）、
 * ocr.paddle.url / ocr.paddle.key / ocr.paddle.timeout-ms（PaddleOCR 本地通道）、
 * invoice.gateway.base-url / invoice.gateway.api-key / invoice.gateway.enabled（发票能力网关，默认通道）。
 * 读取即时生效（PaddleOcrChannel/HttpOcrChannel/QxyGatewayOcrChannel/OcrChannelRouter 每次识别实时读），
 * 保存即切换，无需重启。key/token 回显一律掩码，保存时留空表示不修改。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrConfigService {

    public static final String K_CHANNEL = "ocr.channel";
    public static final String K_URL = "ocr.api.url";
    public static final String K_TOKEN = "ocr.api.token";
    public static final String K_MAPPING = "ocr.api.mapping";
    public static final String K_PADDLE_URL = "ocr.paddle.url";
    public static final String K_PADDLE_KEY = "ocr.paddle.key";
    /** 发票能力网关（默认通道）三键：与 BCP / UniErp 使用同一组键名 */
    public static final String K_GW_BASE_URL = "invoice.gateway.base-url";
    public static final String K_GW_API_KEY = "invoice.gateway.api-key";
    public static final String K_GW_ENABLED = "invoice.gateway.enabled";

    private final SysConfigMapper configMapper;
    private final OcrChannelRouter router;

    /** 当前配置回显（key/token 掩码 + 生效通道） */
    public Map<String, Object> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", readRaw(K_CHANNEL));
        m.put("effectiveChannel", router.currentChannel());
        m.put("url", readRaw(K_URL));
        m.put("tokenMasked", mask(readRaw(K_TOKEN)));
        m.put("mapping", readRaw(K_MAPPING));
        m.put("paddleUrl", readRaw(K_PADDLE_URL));
        m.put("paddleKeyMasked", mask(readRaw(K_PADDLE_KEY)));
        m.put("gatewayUrl", readRaw(K_GW_BASE_URL));
        m.put("gatewayKeyMasked", mask(readRaw(K_GW_API_KEY)));
        m.put("gatewayEnabled", readRaw(K_GW_ENABLED));
        return m;
    }

    /** 保存配置；key/token 传空串/null 表示保持原值不动 */
    @Transactional(rollbackFor = Exception.class)
    public void save(Map<String, String> req) {
        String channel = req.get("channel");
        if (channel != null && !channel.isBlank()
                && !Set.of("gateway", "mock", "http", "paddle").contains(channel.trim().toLowerCase())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通道仅支持 gateway / mock / http / paddle");
        }
        upsert(K_CHANNEL, channel == null ? "" : channel.trim().toLowerCase(),
                "OCR通道(gateway/mock/http/paddle，空=跟随系统配置)");
        if (req.get("url") != null) {
            upsert(K_URL, req.get("url").trim(), "OCR识别API地址");
        }
        if (req.get("token") != null && !req.get("token").isBlank()) {
            upsert(K_TOKEN, req.get("token").trim(), "OCR识别API Token");
        }
        if (req.get("mapping") != null) {
            upsert(K_MAPPING, req.get("mapping").trim(), "OCR响应字段映射(JSON)");
        }
        if (req.get("paddleUrl") != null) {
            upsert(K_PADDLE_URL, req.get("paddleUrl").trim(), "PaddleOCR识别端点(invoice_ocr)");
        }
        if (req.get("paddleKey") != null && !req.get("paddleKey").isBlank()) {
            upsert(K_PADDLE_KEY, req.get("paddleKey").trim(), "PaddleOCR私有密钥(X-Ocr-Key)");
        }
        if (req.get("gatewayUrl") != null) {
            upsert(K_GW_BASE_URL, req.get("gatewayUrl").trim(), "发票能力网关基址(默认通道)");
        }
        if (req.get("gatewayKey") != null && !req.get("gatewayKey").isBlank()) {
            upsert(K_GW_API_KEY, req.get("gatewayKey").trim(), "发票能力网关内部密钥(X-Gateway-Api-Key)");
        }
        if (req.get("gatewayEnabled") != null && !req.get("gatewayEnabled").isBlank()) {
            upsert(K_GW_ENABLED, req.get("gatewayEnabled").trim(), "发票能力网关总开关(true/false)");
        }
        log.info("OCR通道配置已保存 channel={} url={} paddleUrl={} gatewayUrl={}（key/token/mapping 变更见 sys_config）",
                channel, req.get("url"), req.get("paddleUrl"), req.get("gatewayUrl"));
    }

    private String readRaw(String key) {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getTenantId, TenantContext.getTenantId())
                .last("limit 1"));
        return c == null || c.getConfigValue() == null ? "" : c.getConfigValue();
    }

    private void upsert(String key, String value, String name) {
        Long tenantId = TenantContext.getTenantId();
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getTenantId, tenantId)
                .last("limit 1"));
        if (c == null) {
            c = new SysConfig();
            c.setTenantId(tenantId);
            c.setConfigKey(key);
            c.setConfigName(name);
            c.setConfigValue(value);
            configMapper.insert(c);
        } else {
            c.setConfigValue(value);
            c.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(c);
        }
    }

    private String mask(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        return token.length() <= 4 ? "****" : token.substring(0, 2) + "****" + token.substring(token.length() - 2);
    }
}
