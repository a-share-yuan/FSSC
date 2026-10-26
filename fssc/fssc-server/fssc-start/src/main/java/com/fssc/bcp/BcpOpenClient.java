// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BCP 门户开放接口出站客户端（{@code FSSC → BCP /api/open/linku/**}）—— HMAC-SHA256 签名调用。
 *
 * <h3>为什么是 HTTP 而不是直连库</h3>
 * FSSC 只有本系统单数据源，读不到 {@code bcp_*} 表；伙伴提交的行级异议存在 BCP 侧。
 * 因此「BCP 异议审核」页只能经此通道查询并回写结论。
 *
 * <h3>签名规则（与 BCP 侧 {@code BcpLinkuOpenGuard} 逐字一致，两侧共用一把密钥）</h3>
 * <pre>
 *   X-BCP-Signature = "sha256=" + 小写hex( HMAC-SHA256(secret, timestamp + "." + rawBody) )
 *   X-BCP-Timestamp = Unix 秒
 *   X-BCP-Event-Type = 事件类型（本通道仅用于日志辨识）
 *   查询端点（GET） 的 rawBody 为 <b>空串</b>；写入端点（POST）的 rawBody 为 <b>请求体原文</b>。
 * </pre>
 * 原文里带时间戳，使旧请求体的签名无法被挪到新时间戳上重用；签名在 BCP 侧以常量时间比较。
 *
 * <h3>请求体原文必须逐字节一致</h3>
 * POST 先序列化成字符串、对<b>该字符串</b>签名，再以 {@code StringHttpMessageConverter}
 * 原样发出（{@code application/json;charset=UTF-8}）。若让框架二次序列化，签名与原文就会错位。
 */
@Slf4j
@Component
public class BcpOpenClient {

    private static final String SIG_PREFIX = "sha256=";
    private static final String PATH_QUERY_OBJECTIONS = "/api/open/linku/query/objections";
    private static final String PATH_SYNC_REVIEW = "/api/open/linku/sync/objection-review";
    private static final String EVENT_QUERY_OBJECTIONS = "QUERY_OBJECTIONS";
    private static final String EVENT_OBJECTION_REVIEW = "OBJECTION_REVIEW";

    private final BcpOpenProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public BcpOpenClient(BcpOpenProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 拉取 BCP 侧待审的行级异议。
     *
     * @param direction 可选 AP/AR 过滤；null/空=全部
     * @param limit     条数上限（BCP 侧钳位 1..500）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPending(String direction, Integer limit) {
        requireSecret();
        StringBuilder query = new StringBuilder();
        if (StringUtils.hasText(direction)) {
            query.append("direction=").append(urlEncode(direction));
        }
        if (limit != null) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append("limit=").append(limit);
        }
        String url = props.baseUrl() + PATH_QUERY_OBJECTIONS + (query.length() > 0 ? "?" + query : "");
        Object data = exchange(HttpMethod.GET, url, "", EVENT_QUERY_OBJECTIONS).get("data");
        return data instanceof List ? (List<Map<String, Object>>) data : List.of();
    }

    /**
     * 回写行级异议审核结论。
     *
     * @param approve true=同意（BCP 以伙伴提交的数量/单价替换明细行）；false=驳回（明细行不动）
     * @param reviewer 审核人（FSSC AP/AR 岗用户名，随正文一起被签名覆盖，BCP 侧据此记录）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> review(Long objectionId, boolean approve, String reviewer, String remark) {
        requireSecret();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("objectionId", objectionId);
        body.put("approve", approve);
        body.put("reviewer", reviewer);
        body.put("remark", remark);
        String raw = toJson(body);
        Object data = exchange(HttpMethod.POST, props.baseUrl() + PATH_SYNC_REVIEW, raw,
                EVENT_OBJECTION_REVIEW).get("data");
        return data instanceof Map ? (Map<String, Object>) data : Map.of();
    }

    // ==================== 传输 ====================

    /**
     * 发起一次签名调用并解析 BCP 的统一信封。
     *
     * <p>BCP 侧失败信封为 {@code {success:false, status, code, message}}：400=参数/归属不合法、
     * 409=状态冲突（例如异议已被他人审核，不允许改判）、401=验签未过、500=服务端故障。
     * 这里把 BCP 的 {@code message} 原文透出给页面 —— 它正是"为什么没审成"的答案。</p>
     */
    private Map<String, Object> exchange(HttpMethod method, String url, String rawBody, String eventType) {
        long ts = System.currentTimeMillis() / 1000;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.set("X-BCP-Timestamp", String.valueOf(ts));
        headers.set("X-BCP-Signature", SIG_PREFIX + hmacHex(props.secret(), ts + "." + rawBody));
        headers.set("X-BCP-Event-Type", eventType);
        HttpEntity<String> entity =
                new HttpEntity<>(HttpMethod.POST.equals(method) ? rawBody : null, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            return parseEnvelope(resp.getBody());
        } catch (HttpStatusCodeException e) {
            log.warn("[BCP开放通道] 调用被拒 url={} event={} httpStatus={} body={}",
                    url, eventType, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(failureCode(e.getStatusCode().value()), failureMessage(e));
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("[BCP开放通道] 调用异常 url={} event={} err={}", url, eventType, e.getMessage());
            throw new BizException(500, "BCP 门户接口不可达或响应异常：" + e.getMessage());
        }
    }

    /** 解析成功信封；{@code success} 不为 true 视为失败（正常 2xx 不该出现，默认处理防静默）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEnvelope(String body) {
        Map<String, Object> m = parseJson(body);
        if (!Boolean.TRUE.equals(m.get("success"))) {
            Object message = m.get("message");
            throw new BizException(502,
                    message == null ? "BCP 门户接口未返回成功" : String.valueOf(message));
        }
        return m;
    }

    /** BCP 侧 message 优先（业务语义最准）；无 message 时退回 code，再退回 HTTP 码。 */
    private String failureMessage(HttpStatusCodeException e) {
        Map<String, Object> m = parseJsonQuietly(e.getResponseBodyAsString());
        Object message = m.get("message");
        if (message != null && StringUtils.hasText(String.valueOf(message))) {
            return String.valueOf(message);
        }
        Object code = m.get("code");
        if (code != null && StringUtils.hasText(String.valueOf(code))) {
            return String.valueOf(code);
        }
        return "BCP 门户接口调用失败（HTTP " + e.getStatusCode().value() + "）";
    }

    /** 4xx 原样透出（参数/状态冲突语义要保住），其余归 500 —— 不要把 5xx 当业务码喂给前端。 */
    private int failureCode(int bcpStatus) {
        return bcpStatus >= 400 && bcpStatus < 500 ? bcpStatus : 500;
    }

    private void requireSecret() {
        if (!props.hasSecret()) {
            throw new BizException(500, props.secretHint());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            throw new BizException(502, "BCP 门户接口返回体不是合法 JSON");
        }
    }

    /** 仅用于错误分支的宽松解析：解析不出来就当没有，不掩盖原始失败。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonQuietly(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(500, "序列化请求体失败：" + e.getMessage());
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** HMAC-SHA256(secret, data) → 小写十六进制（与 BCP 侧同实现）。 */
    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 算法名固定，正常不会失败；失败即抛出让调用方拒绝，不静默发出未签名请求
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
