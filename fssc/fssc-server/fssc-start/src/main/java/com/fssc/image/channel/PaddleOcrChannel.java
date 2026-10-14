// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * PaddleOCR 本地发票识别通道（fssc.ocr.channel=paddle 时生效）。
 *
 * <p>对接本机 PaddleOCR 微服务（kb_ocr_server.py）的 {@code POST /invoice_ocr}：
 * multipart/form-data（字段 {@code file=图片/扫描件字节}），服务端按文件类型自动分流
 * （图片 → RT-DETR 发票区域检测 + PP-OCRv5 识别；pdf/ofd/xml → 本地版式解析），
 * 响应即 fssc 标准 InvoiceResult 结构（success/invoiceType/invoiceCode/invoiceNo/invoiceDate/
 * amount/taxAmount/netAmount/sellerName/sellerTaxNo/buyerName/buyerTaxNo/primaryItemName/rawText），
 * 与 {@link OcrResult} 字段一一对应，无需 ocr.api.mapping 字段映射。</p>
 *
 * <p>sys_config 可配（系统管理 → OCR通道配置，改后即时生效）：</p>
 * <ul>
 *   <li>{@code ocr.paddle.url}：识别端点，默认 {@code http://127.0.0.1:8087/invoice_ocr}（本机回环，OCR 与应用同机部署）；</li>
 *   <li>{@code ocr.paddle.key}：私有密钥，OCR 侧设置 OCR_API_KEY 时以 {@code X-Ocr-Key} 头携带；</li>
 *   <li>{@code ocr.paddle.timeout-ms}：请求超时，默认 120000——OCR 服务冷启动首次识别含模型加载
 *       约 50s，超时必须放宽（HttpOcrChannel 的 10s 在此不适用）。</li>
 * </ul>
 *
 * <p>约定同 HttpOcrChannel：一切失败（未配置/网络/非200/响应非法/success=false）以 ok=false + errorMsg
 * 返回，不抛穿，由调用方（OcrService / 上传接口）决定页面状态；每次实时读 sys_config。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaddleOcrChannel implements OcrChannel {

    /** PaddleOCR 微服务默认识别端点（仅本机回环，与 OCR 服务同机部署拓扑约定一致） */
    public static final String DEFAULT_URL = "http://127.0.0.1:8087/invoice_ocr";
    private static final long DEFAULT_TIMEOUT_MS = 120_000L;

    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder()
                // 显式 HTTP/1.1：Java HttpClient 默认对明文 HTTP 携带 Upgrade: h2c，
                // uvicorn/FastAPI 不支持 h2c 升级会把请求体解析失败（422 Unprocessable Content）
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("OCR paddle 通道已装配（首次识别时按租户读取 ocr.paddle.url/key/timeout-ms，默认 {}）", DEFAULT_URL);
    }

    /** 分库改造：sys_config 在 master 库；NOT_SUPPORTED 挂起外层（可能为 image 库）事务后取 master 连接。 */
    @Override
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public OcrResult recognize(byte[] image, String fileName) {
        try {
            if (image == null || image.length == 0) {
                return OcrResult.fail("PaddleOCR 通道需要图片字节，本次调用未携带影像内容");
            }
            String url = getConfig("ocr.paddle.url", DEFAULT_URL);
            if (url == null || url.isBlank()) {
                url = DEFAULT_URL;
            }
            String key = getConfig("ocr.paddle.key", "");
            long timeoutMs = parseTimeout(getConfig("ocr.paddle.timeout-ms", ""));

            // 手工拼 multipart/form-data（java.net.http 零新依赖；文件名截断为 ASCII 免编码歧义）
            String boundary = "----FsscPaddleOcr" + System.currentTimeMillis();
            String ext = extOf(fileName);
            byte[] body = multipartBody(boundary, image, "invoice" + (ext.isEmpty() ? ".jpg" : "." + ext));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (key != null && !key.isBlank()) {
                builder.header("X-Ocr-Key", key.trim());
            }
            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return OcrResult.fail("PaddleOCR 服务 HTTP " + resp.statusCode());
            }
            OcrResult result = parse(resp.body());
            if (result.isOk()) {
                log.info("OCR paddle 通道识别成功 fileName={} invoiceNo={} invoiceType={} amount={}",
                        fileName, result.getInvoiceNo(), result.getInvoiceType(), result.getAmount());
            }
            return result;
        } catch (Exception ex) {
            // 不抛穿：网络/超时/配置异常统一转失败结果（如 OCR 服务未启动 → 连接被拒）
            log.warn("OCR paddle 通道识别失败 fileName={}: {}", fileName, ex.getMessage());
            return OcrResult.fail("PaddleOCR 通道异常: " + ex.getMessage()
                    + "（请确认本机 PaddleOCR 服务已启动: kb_ocr_server.py, 默认 127.0.0.1:8087）");
        }
    }

    /** 解析 /invoice_ocr 响应（fssc InvoiceResult 结构）→ OcrResult。 */
    private OcrResult parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return OcrResult.fail("PaddleOCR 响应非 JSON: " + trim(e.getMessage()));
        }
        if (root == null || !root.isObject()) {
            return OcrResult.fail("PaddleOCR 响应格式异常（非 JSON 对象）");
        }
        if (!root.path("success").asBoolean(false)) {
            return OcrResult.fail("PaddleOCR 识别失败: " + trim(root.path("error").asText("")));
        }
        OcrResult r = new OcrResult();
        r.setInvoiceCode(text(root, "invoiceCode"));
        r.setInvoiceNo(text(root, "invoiceNo"));
        r.setInvoiceType(text(root, "invoiceType"));
        String date = text(root, "invoiceDate");
        r.setInvoiceDate(date == null ? null : OcrFieldMapper.normalizeDate(date));
        r.setAmount(OcrFieldMapper.toDecimal(root.path("amount").asText(null)));
        r.setTaxAmount(OcrFieldMapper.toDecimal(root.path("taxAmount").asText(null)));
        r.setNetAmount(OcrFieldMapper.toDecimal(root.path("netAmount").asText(null)));
        r.setSellerName(text(root, "sellerName"));
        r.setSellerTaxNo(text(root, "sellerTaxNo"));
        r.setBuyerName(text(root, "buyerName"));
        r.setBuyerTaxNo(text(root, "buyerTaxNo"));
        r.setPrimaryItemName(text(root, "primaryItemName"));
        r.setRawText(text(root, "rawText"));
        return r;
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isMissingNode() || n.isNull() || n.isContainerNode()) {
            return null;
        }
        String t = n.asText();
        if (t == null) {
            return null;
        }
        t = t.trim();
        return t.isEmpty() ? null : t;
    }

    private static long parseTimeout(String v) {
        try {
            long ms = Long.parseLong(v.trim());
            return ms > 0 ? ms : DEFAULT_TIMEOUT_MS;
        } catch (Exception e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "bmp", "webp", "tif", "tiff");

    private static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return IMAGE_EXTS.contains(ext) ? ext : "jpg";
    }

    /** multipart/form-data 请求体：单文件字段 file */
    private static byte[] multipartBody(String boundary, byte[] file, String fileName) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(file);
        bos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return bos.toByteArray();
    }

    private static String trim(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    /** 读取租户全局参数（sys_config ocr.paddle.*），无则返回默认——与 HttpOcrChannel.getConfig 同法 */
    private String getConfig(String key, String def) {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                return c.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("读取 PaddleOCR 通道配置失败 key={}", key, e);
        }
        return def;
    }
}
