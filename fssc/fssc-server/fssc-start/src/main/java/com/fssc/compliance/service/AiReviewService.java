// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.connector.api.ConnectorFacade;
import com.fssc.connector.api.ChannelResponse;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 审单服务（重构：通过 IntegrationGateway 统一网关调用大模型接口）。
 * <p>
 * 原 实现直接读 {@code sys_config} + 裸 {@code HttpClient}，现改为：
 * <ul>
 *   <li>配置来源：{@code ext_interface_config} 表 category=AI_MODEL（URL/Key/超时统一管理）</li>
 *   <li>HTTP 调用：经 {@link IntegrationGateway#post} 统一鉴权/超时/日志</li>
 *   <li>开关逻辑：AI_MODEL 分类下有启用配置即视为开启，无需额外 sys_config 开关</li>
 * </ul>
 * <p>
 * 请求体：{@code {"billNo":"...", "billData":{...}, "riskContext":{...}}}
 * <p>
 * 响应体约定：{@code {"action":"AUTO_PASS|AUTO_REJECT|MANUAL_REVIEW", "riskLevel":"LOW|MEDIUM|HIGH",
 *   "riskScore":0-100, "remark":"...", "confidence":0.0-1.0}}
 * <p>
 * 一切失败（未配置/网络/超时/非200/响应非法）均返回 null，调用方回退规则引擎。
 * <p>
 * 现网 {@code AI_MODEL} 配的是会话补全接口（chat/completions），与本请求体不同型，
 * 调用恒失败 ⇒ 本类不每单刷一条 WARN（按时间窗聚合、累计计数），失败结论由调用方落
 * 「AI 未采纳·回退规则引擎（检查点）」业务记录；真实 AI 接入另行判定，当前不启用，AI 结论也不得进 AUTO_PASS 链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final ConnectorFacade connector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CATEGORY = "AI_MODEL";

    /** 失败日志时间窗——同因失败 10 分钟内只复述一次，累计次数进日志，避免每单刷噪音 */
    private static final long FAIL_LOG_WINDOW_MS = 600_000L;
    private final AtomicLong failCount = new AtomicLong();
    private final AtomicLong lastFailLogAt = new AtomicLong();

    /**
     * 调用 AI 审单接口。
     *
     * @param billNo 单据编号
     * @param billData 单据数据（费用明细、金额、供应商等）
     * @param riskContext 风险上下文（反欺诈检测结果、信用分等）
     * @return AI 审单结果；未启用/配置缺失/调用失败返回 null（回退规则引擎）
     */
    public AiReviewResult review(String billNo, Map<String, Object> billData, Map<String, Object> riskContext) {
        if (!isEnabled()) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("billNo", billNo == null ? "" : billNo);
            body.put("billData", billData == null ? Map.of() : billData);
            body.put("riskContext", riskContext == null ? Map.of() : riskContext);

            ChannelResponse resp = connector.invokeRead(CATEGORY, body);
            if (resp == null || !resp.isOk()) {
                noteFailure(billNo, "code=" + (resp != null ? resp.getStatusCode() : -1)
                        + " err=" + (resp != null ? resp.getError() : "null response"));
                return null;
            }
            AiReviewResult parsed = parseResponse(resp.getBody());
            if (parsed == null) {
                noteFailure(billNo, "响应不合约定（无 action 字段或非对象）");
            }
            return parsed;
        } catch (Exception ex) {
            noteFailure(billNo, "调用异常 " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * 乙：AI 未采纳的累计次数（日志降噪后仍可从日志的累计值读出，不静默丢失败）。
     * 首失败即报 WARN，其后同因失败在一个时间窗内只留 DEBUG＋计数。
     */
    private void noteFailure(String billNo, String detail) {
        long total = failCount.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastFailLogAt.get();
        if (now - last >= FAIL_LOG_WINDOW_MS && lastFailLogAt.compareAndSet(last, now)) {
            log.warn("AI 审单未采纳（累计 {} 次）billNo={}：{}；本单回退规则引擎（检查点），同因日志 {} 分钟内不重复",
                    total, billNo, detail, FAIL_LOG_WINDOW_MS / 60000);
        } else {
            log.debug("AI 审单未采纳（累计 {} 次）billNo={}：{}", total, billNo, detail);
        }
    }

    /** AI 审单是否启用：AI_MODEL 分类下有启用的接口配置即为开 */
    public boolean isEnabled() {
        ExtInterfaceConfig cfg = connector.getConfig(CATEGORY);
        return cfg != null;
    }

    private AiReviewResult parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            String action = textOf(root, "action");
            if (action == null || action.isBlank()) {
                return null;
            }
            String riskLevel = textOf(root, "riskLevel");
            String remark = textOf(root, "remark");
            int riskScore = root.has("riskScore") && root.get("riskScore").isNumber()
                    ? root.get("riskScore").asInt() : 0;
            double confidence = root.has("confidence") && root.get("confidence").isNumber()
                    ? root.get("confidence").asDouble() : 0.0;
            return new AiReviewResult(action, riskLevel, riskScore, remark, confidence);
        } catch (Exception e) {
            // 解析失败的 WARN 已收拢到 noteFailure（带累计），此处降为 DEBUG 不再重复刷
            log.debug("AI 审单响应解析失败: {}", truncate(json));
            return null;
        }
    }

    private String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** AI 审单结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AiReviewResult {
        /** AUTO_PASS / AUTO_REJECT / MANUAL_REVIEW */
        private String action;
        /** LOW / MEDIUM / HIGH */
        private String riskLevel;
        /** 0-100 */
        private int riskScore;
        /** AI 审核意见 */
        private String remark;
        /** AI 置信度 0.0-1.0 */
        private double confidence;
    }
}
