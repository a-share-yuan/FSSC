// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gateway.health;

import com.fssc.bcp.BcpOpenProperties;
import com.fssc.openapi.linku.LinkuOpenProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 集成网关健康检查（/actuator/health 的 gatewayDomain 组件）。
 *
 * <p>判据分两类：</p>
 * <ul>
 *   <li><b>硬故障（DOWN）</b>：master 库不可用——入站开放接口与通道调用日志都写不进去，服务失去意义。</li>
 *   <li>软状态（明细，不影响 UP）：密钥未注入 / 出站基址 / 已登记通道数。密钥缺失是"该通道 fail-closed"，
 *       与单体口径一致，不能因此把整个网关摘出流量（其余通道仍可用）。明细里不出现任何密钥值。</li>
 * </ul>
 *
 * <p>入站面用 {@link RequestMappingHandlerMapping} 实数 /api/open/linku 下的端点个数，
 * 直接证明"开放端点确实在这个进程里注册了"，而不是相信配置文件。</p>
 */
@Slf4j
@Component("gatewayDomain")
@ConditionalOnProperty(prefix = "fssc.integration-gateway.health", name = "enabled", havingValue = "true")
public class GatewayDomainHealthIndicator implements HealthIndicator {

    private static final String INBOUND_PREFIX = "/api/open/linku";

    private final HikariDataSource master;
    private final LinkuOpenProperties linkuProps;
    private final BcpOpenProperties bcpProps;
    private final RequestMappingHandlerMapping handlerMapping;

    @Value("${fssc.integration-gateway.standalone:false}")
    private boolean standalone;

    public GatewayDomainHealthIndicator(@Qualifier("masterDataSource") HikariDataSource master,
                                        LinkuOpenProperties linkuProps,
                                        BcpOpenProperties bcpProps,
                                        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.master = master;
        this.linkuProps = linkuProps;
        this.bcpProps = bcpProps;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public Health health() {
        boolean up = true;
        Map<String, Object> details = new LinkedHashMap<>();

        Map<String, Object> db = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();
        try (Connection c = master.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select 1")) {
            rs.next();
            db.put("status", "UP");
            db.put("latencyMs", System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            db.put("status", "DOWN");
            db.put("latencyMs", System.currentTimeMillis() - t0);
            db.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
            up = false;
        }
        details.put("database", db);

        details.put("inbound", inbound());

        Map<String, Object> bcp = new LinkedHashMap<>();
        bcp.put("baseUrl", bcpProps.baseUrl());
        bcp.put("secretConfigured", bcpProps.hasSecret());
        bcp.put("status", bcpProps.hasSecret() ? "READY" : "FAIL_CLOSED_NO_SECRET");
        details.put("outboundBcp", bcp);

        details.put("standalone", standalone);
        return (up ? Health.up() : Health.down()).withDetails(details).build();
    }

    private Map<String, Object> inbound() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("secretConfigured", linkuProps.hasSecret());
        try {
            long count = handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> {
                        var classic = info.getPatternsCondition();
                        var parsed = info.getPathPatternsCondition();
                        if (classic != null && !classic.getPatterns().isEmpty()) {
                            return classic.getPatterns().stream();
                        }
                        if (parsed != null) {
                            return parsed.getPatterns().stream().map(pp -> pp.getPatternString());
                        }
                        return Stream.empty();
                    })
                    .filter(p -> p != null && p.startsWith(INBOUND_PREFIX))
                    .distinct()
                    .count();
            m.put("endpoints", count);
            m.put("status", count > 0 ? "REGISTERED" : "MISSING");
        } catch (Throwable t) {
            m.put("status", "UNKNOWN");
            m.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return m;
    }
}
