// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.health;

import com.fssc.ai.AiActionCatalog;
import com.fssc.ai.knowledge.KnowledgeIndex;
import com.fssc.ai.skill.SkillRegistry;
import com.fssc.connector.api.ConnectorFacade;
import com.fssc.system.entity.ExtInterfaceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 域健康检查（/actuator/health 的 aiDomain 组件）。
 *
 * <p>四项明细：</p>
 * <ul>
 *   <li><b>upstream</b>——按 {@code fssc.ai.gateway.base-url} 真实 HTTP 探业务服务的 /actuator/health；
 *       这是"回环→真 HTTP"的连通性判据，断开即 AI 全部执行类能力不可用。</li>
 *   <li><b>loopbackSelf</b>——base-url 是否指回自己：独立进程下指回自己＝配置错误（转发必 404），
 *       单体进程内自转发是设计内的，因此只在 {@code fssc.ai.standalone=true} 时判红。</li>
 *   <li><b>model</b>——AI_MODEL 通道是否 ENABLED（只报状态，不返回任何密钥）。</li>
 *   <li><b>knowledge / catalog</b>——知识库与动作目录是否加载（构建期产物，缺失即答不了也调不了）。</li>
 * </ul>
 *
 * <p>本组件只进 readiness 组：AI 上游抖动时应摘流量，不应触发 Pod 重启。</p>
 */
@Slf4j
@Component("aiDomain")
@ConditionalOnProperty(prefix = "fssc.ai.health", name = "enabled", havingValue = "true")
public class AiDomainHealthIndicator implements HealthIndicator {

    private final ConnectorFacade connector;
    private final KnowledgeIndex knowledgeIndex;
    private final SkillRegistry skillRegistry;
    private final AiActionCatalog catalog;

    @Value("${fssc.ai.gateway.base-url:http://127.0.0.1:8089}")
    private String baseUrl;

    @Value("${server.port:8089}")
    private int serverPort;

    @Value("${fssc.ai.standalone:false}")
    private boolean standalone;

    @Value("${fssc.ai.inspect.enabled:true}")
    private boolean inspectEnabled;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AiDomainHealthIndicator(ConnectorFacade connector, KnowledgeIndex knowledgeIndex,
                                   SkillRegistry skillRegistry, AiActionCatalog catalog) {
        this.connector = connector;
        this.knowledgeIndex = knowledgeIndex;
        this.skillRegistry = skillRegistry;
        this.catalog = catalog;
    }

    @Override
    public Health health() {
        boolean up = true;
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("upstream", probeUpstream());
        if (!"UP".equals(((Map<?, ?>) details.get("upstream")).get("status"))) {
            up = false;
        }

        boolean loopbackSelf = pointsToSelf();
        Map<String, Object> loop = new LinkedHashMap<>();
        loop.put("self", loopbackSelf);
        loop.put("standalone", standalone);
        loop.put("status", standalone && loopbackSelf ? "DOWN" : "UP");
        details.put("loopbackSelf", loop);
        if (standalone && loopbackSelf) {
            up = false;
        }

        details.put("model", probeModel());

        Map<String, Object> kb = new LinkedHashMap<>();
        try {
            kb.put("enabled", knowledgeIndex.isEnabled());
            kb.put("corpusHash", knowledgeIndex.corpusHash());
            kb.put("skills", skillRegistry.size());
            kb.put("actions", catalog.size());
            kb.put("status", knowledgeIndex.isEnabled() && catalog.size() > 0 ? "UP" : "DOWN");
        } catch (Throwable t) {
            kb.put("status", "DOWN");
            kb.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
            up = false;
        }
        details.put("knowledge", kb);

        details.put("inspectJob", inspectEnabled ? "IN_MONOLITH" : "DISABLED_IN_THIS_PROCESS");
        details.put("baseUrl", baseUrl);

        return (up ? Health.up() : Health.down()).withDetails(details).build();
    }

    private Map<String, Object> probeUpstream() {
        Map<String, Object> m = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() == null ? "" : resp.body();
            boolean up = resp.statusCode() == 200 && body.contains("\"status\":\"UP\"");
            m.put("status", up ? "UP" : "DOWN");
            m.put("httpStatus", resp.statusCode());
            m.put("latencyMs", System.currentTimeMillis() - start);
            if (!up) {
                m.put("body", body.length() > 200 ? body.substring(0, 200) : body);
            }
        } catch (Throwable t) {
            m.put("status", "DOWN");
            m.put("latencyMs", System.currentTimeMillis() - start);
            m.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return m;
    }

    /** base-url 的端口是否等于本进程监听端口（host 不判，容器内 localhost 与 Pod IP 都可能指向自身）。 */
    private boolean pointsToSelf() {
        try {
            URI u = URI.create(baseUrl);
            return u.getPort() == serverPort;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> probeModel() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            ExtInterfaceConfig cfg = connector.getConfig("AI_MODEL");
            m.put("configured", cfg != null);
            if (cfg != null) {
                m.put("provider", cfg.getProvider());
                m.put("model", cfg.getModelName());
                m.put("status", "ENABLED".equals(cfg.getStatus()) ? "UP" : "DISABLED");
            } else {
                m.put("status", "DISABLED");
            }
        } catch (Throwable t) {
            m.put("status", "UNKNOWN");
            m.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return m;
    }
}
