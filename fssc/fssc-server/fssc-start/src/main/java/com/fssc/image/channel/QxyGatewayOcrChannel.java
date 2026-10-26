// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * 发票能力网关（InvoiceGateway，8096）OCR 通道——<b>系统内发票识别的默认通道</b>
 * （{@code fssc.ocr.channel} 缺省即 gateway，改造前为 mock）。
 *
 * <p>本类<b>只对接自家网关的中性契约</b>（{@code POST {base}/api/tax/v1/ocr/recognize}，
 * 请求体 {@code {imageBase64, fileName}}，响应 {@code {success, code, message, data:{invoices[]}}}）：
 * 上游接口的路径、字段名、签名与令牌全部收敛在网关进程内，FSSC 侧不感知、也不得出现。</p>
 *
 * <p>配置项（与验真/认证抵扣/开票共用同一组键，三系统统一规则）：
 * <ul>
 *   <li>{@code invoice.gateway.base-url}：yml 默认 {@code http://127.0.0.1:8096}；
 *       sys_config {@code invoice.gateway.base-url} 可按租户覆盖；置空即不启用。</li>
 *   <li>{@code invoice.gateway.api-key}：网关侧配置了内部密钥时以请求头
 *       {@code X-Gateway-Api-Key} 携带；网关留空则为内网免密钥模式。</li>
 *   <li>{@code invoice.gateway.enabled}：{@code false}（yml {@code fssc.gateway.enabled}
 *       或 sys_config {@code invoice.gateway.enabled}）可整体回退，识别链路退回原通道选择。</li>
 * </ul></p>
 *
 * <p>约定与 {@link HttpOcrChannel} 一致：一切失败（网关关闭/不可达/非 200/未识别出票面）
 * 均以 {@code ok=false + errorMsg} 返回，绝不抛穿、绝不伪造票面。识别超时 60s
 * （网关侧上游 OCR 含图片下载与多票切分，比通用 http 通道的 10s 宽松）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QxyGatewayOcrChannel implements OcrChannel {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    /** 网关内部请求头（与 InvoiceGateway 的 ApiKeyFilter 对齐） */
    public static final String API_KEY_HEADER = "X-Gateway-Api-Key";

    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;

    /** yml 回落值（sys_config 未配置时生效） */
    @Value("${invoice.gateway.base-url:http://127.0.0.1:8096}")
    private String ymlBaseUrl;

    @Value("${invoice.gateway.api-key:}")
    private String ymlApiKey;

    /** 总开关：false 时本通道不参与路由（回退改造前行为） */
    @Value("${fssc.gateway.enabled:true}")
    private boolean ymlEnabled;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        log.info("OCR gateway 通道已装配（默认通道；识别时按租户实时读 invoice.gateway.*，"
                + "yml 回落 base-url={}）", ymlBaseUrl);
    }

    @Override
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public OcrResult recognize(byte[] image, String fileName) {
        String url = "";
        try {
            if (!enabled()) {
                return OcrResult.fail("发票能力网关通道未启用（invoice.gateway.enabled=false）");
            }
            String base = baseUrl();
            if (base.isBlank()) {
                return OcrResult.fail("未配置发票能力网关地址（invoice.gateway.base-url）");
            }
            url = base + "/api/tax/v1/ocr/recognize";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("imageBase64", image == null ? "" : Base64.getEncoder().encodeToString(image));
            body.put("fileName", fileName == null ? "" : fileName);
            if (image == null || image.length == 0) {
                return OcrResult.fail("影像字节为空，无法识别");
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8));
            String apiKey = apiKey();
            if (!apiKey.isBlank()) {
                builder.header(API_KEY_HEADER, apiKey);
            }

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                // 网关侧未配置上游凭证/上游异常都会走到这里（如 503 UPSTREAM_NOT_CONFIGURED）：
                // 只回失败原因，不退化成 mock 识别，与 D-DEC-115「没有真实凭据就不给结论」同一规则。
                return OcrResult.fail("发票能力网关 HTTP " + resp.statusCode() + ": " + brief(resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.path("success").asBoolean(false)) {
                return OcrResult.fail("发票能力网关返回失败: " + root.path("code").asText("")
                        + " " + root.path("message").asText(""));
            }
            JsonNode invoices = root.path("data").path("invoices");
            if (!invoices.isArray() || invoices.isEmpty()) {
                return OcrResult.fail("网关未识别出票面（发票识别结果为空）");
            }
            OcrResult result = mapInvoice(invoices.get(0), resp.body());
            if (result.isOk()) {
                log.info("OCR gateway 通道识别成功 fileName={} invoiceNo={} 票面数={}",
                        fileName, result.getInvoiceNo(), invoices.size());
            }
            return result;
        } catch (Exception ex) {
            // 不抛穿：网络/超时/响应非法统一转失败结果
            log.warn("OCR gateway 通道识别失败 fileName={} url={}: {}", fileName, url, ex.getMessage());
            return OcrResult.fail("发票能力网关识别异常: " + ex.getMessage());
        }
    }

    /** 网关归一化票面 → OcrResult（字段含义见 InvoiceGateway 的中性契约） */
    private OcrResult mapInvoice(JsonNode inv, String rawBody) {
        OcrResult r = new OcrResult();
        r.setOk(inv.path("recognized").asBoolean(true));
        if (!r.isOk()) {
            r.setOk(false);
            r.setErrorMsg("票面识别未成功（网关 recognized=false）");
            return r;
        }
        r.setInvoiceCode(text(inv, "invoiceCode"));
        // 全电/数电票无发票代码，号码优先取 20 位数字发票号码
        String number = text(inv, "invoiceNumber");
        String digital = text(inv, "digitalInvoiceNumber");
        r.setInvoiceNo(digital != null && !digital.isBlank() ? digital : number);
        r.setInvoiceDate(normalizeDate(text(inv, "invoiceDate")));
        r.setAmount(decimal(inv, "totalWithTax"));
        r.setTaxAmount(decimal(inv, "taxAmount"));
        r.setNetAmount(decimal(inv, "amount"));
        r.setSellerName(text(inv, "sellerName"));
        r.setSellerTaxNo(text(inv, "sellerTaxNo"));
        r.setBuyerName(text(inv, "buyerName"));
        r.setBuyerTaxNo(text(inv, "buyerTaxNo"));
        JsonNode items = inv.path("items");
        r.setPrimaryItemName(items.isArray() && !items.isEmpty() ? text(items.get(0), "name") : "");
        // 票面类型：优先按票种名称走既有推断名称表（与前端 constants/invoiceType.js 对齐）
        String typeName = text(inv, "invoiceTypeName");
        r.setInvoiceType(typeName != null && !typeName.isBlank()
                ? OcrLocalParsers.inferType(typeName) : OcrLocalParsers.T_OTHER);
        r.setRawText(rawBody != null && rawBody.length() > 8000 ? rawBody.substring(0, 8000) : rawBody);
        return r;
    }

    // ===================== 配置读取（sys_config 优先，yml 回落） =====================

    /** 生效网关基址：去尾部斜杠；sys_config 未配置时用 yml 默认值 */
    public String baseUrl() {
        String v = getConfig("invoice.gateway.base-url", ymlBaseUrl);
        if (v == null) {
            return "";
        }
        v = v.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    public String apiKey() {
        String v = getConfig("invoice.gateway.api-key", ymlApiKey);
        return v == null ? "" : v.trim();
    }

    public boolean enabled() {
        String v = getConfig("invoice.gateway.enabled", null);
        if (v == null || v.isBlank()) {
            return ymlEnabled;
        }
        return !"false".equalsIgnoreCase(v.trim());
    }

    /** 读取租户全局参数（sys_config invoice.gateway.*），无则返回默认——与 HttpOcrChannel 同法 */
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
            log.warn("读取发票网关配置失败 key={}，回退 yml", key, e);
        }
        return def;
    }

    // ===================== 小工具 =====================

    private static String text(JsonNode n, String f) {
        JsonNode v = n.path(f);
        // R5-D-224：Jackson 对 NullNode 调 asText("") 会返回字面量 "null" 字符串，
        // 上游字段为 JSON null 时会把 "null" 写进票面（购方名等列随之错乱）；先显式判空。
        if (v == null || v.isNull()) {
            return null;
        }
        return v.isValueNode() ? v.asText("") : null;
    }

    private static BigDecimal decimal(JsonNode n, String f) {
        String t = text(n, f);
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(t.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 开票日期归一为 yyyy-MM-dd。R5-D-224：原实现对取不到日期的杂串（发票号等）原样透传、
     * 或截成伪日期，与"invoice_date 存成发票号"的错乱互为因果；现改为严格校验——
     * 取前 8 位数字组成日期并做真实月日校验，非法一律返回 null（宁可留空、不得存错），
     * 后续由 OcrService 的要素强制校验拦住不判「通过」。
     */
    private static String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().replaceAll("[^0-9]", "");
        // 少于 8 位凑不出日期；多于 14 位（yyyyMMddHHmmss 上限）的纯数字串必是发票号之类的杂物
        if (v.length() < 8 || v.length() > 14) {
            return null;
        }
        String ymd = v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8);
        try {
            java.time.LocalDate.parse(ymd);
            return ymd;
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String v = body.replaceAll("\\s+", " ").trim();
        return v.length() <= 200 ? v : v.substring(0, 200) + "…";
    }
}
