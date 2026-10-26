package com.fssc.framework.facade.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.facade.ImageStatsFacade;
import com.fssc.framework.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * 影像统计 Facade 的远程实现（只读服务用）。
 *
 * <p>只在 {@code fssc.remote.image.enabled=true} 的进程里生效（报表只读服务）；
 * 单体进程里 ImageFacadeImpl 直接实现同一接口，不产生任何 HTTP 调用。</p>
 *
 * <p>身份透传：报表请求是<b>真人触发</b>的，直接转发现有 {@code Authorization}
 * （以及 X-Company-Id、traceId），影像服务按同一把尺子做鉴权与数据范围判定，
 * 不引入服务账号/内部密钥。影像服务不可用时<b>抛错</b>而不是返回 0
 * （报表若因上游不可用而显示 0，是伪造数据）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fssc.remote.image", name = "enabled", havingValue = "true")
public class RemoteImageStatsFacade implements ImageStatsFacade {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = build();

    @Value("${fssc.remote.image.base-url:http://127.0.0.1:8090}")
    private String baseUrl;

    public RemoteImageStatsFacade(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static RestTemplate build() {
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(15_000);
        return new RestTemplate(f);
    }

    @Override
    public Long countBatches() {
        JsonNode data = get("/api/tenant/image/stats/batch-count");
        return data == null || data.isNull() ? 0L : data.asLong();
    }

    @Override
    public List<Map<String, Object>> statBatchByBizType() {
        JsonNode data = get("/api/tenant/image/stats/by-biz-type");
        if (data == null || data.isNull()) {
            return List.of();
        }
        return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private JsonNode get(String path) {
        String url = trimSlash(baseUrl) + path;
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest req = currentRequest();
        if (req != null) {
            String auth = req.getHeader("Authorization");
            if (auth != null) {
                headers.set("Authorization", auth);
            }
            String company = req.getHeader("X-Company-Id");
            if (company != null) {
                headers.set("X-Company-Id", company);
            }
        }
        String traceId = currentTraceId();
        if (traceId != null) {
            headers.set(TraceIdFilter.HEADER, traceId);
        }
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "影像服务返回错误：" + root.path("msg").asText(""));
            }
            return root.path("data");
        } catch (BizException ex) {
            throw ex;
        } catch (Throwable t) {
            log.warn("远程影像统计失败 url={}: {}", url, t.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "影像服务不可用，报表暂无法取影像统计：" + t.getMessage());
        }
    }

    private static String trimSlash(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private static String currentTraceId() {
        return org.slf4j.MDC.get(TraceIdFilter.MDC_KEY);
    }
}
