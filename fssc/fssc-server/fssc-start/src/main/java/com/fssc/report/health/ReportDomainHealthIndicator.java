// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.health;

import com.fssc.common.exception.BizException;
import com.fssc.framework.config.DataSourceConfig;
import com.fssc.framework.datasource.DataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报表域健康检查（/actuator/health 的 reportDomain 组件）。
 *
 * <p>三项判据：</p>
 * <ul>
 *   <li><b>database</b>——按 replica 路由执行 {@code SELECT 1}，副本不可达即整域不可用（判红）；</li>
 *   <li><b>writeGuard</b>——真发一条 UPDATE 的 prepare，<b>必须</b>被只读守卫拒绝。
 *       这里不是"检查配置"，而是每次健康检查都做一次可失败的拒绝试探：
 *       守卫失效（返回 OK）说明只读副本能被写，属安全回归，整域判红；</li>
 *   <li><b>imageUpstream</b>——影像统计走真 HTTP（fssc.remote.image.enabled=true 时），
 *       只报连通性，不参与整域判定（影像不可用只影响影像统计两张报表，其余报表照常）。</li>
 * </ul>
 *
 * <p>不要本组件只进 readiness 组：副本抖动应摘流量，不应触发 Pod 重启。</p>
 */
@Slf4j
@Component("reportDomain")
@ConditionalOnProperty(prefix = "fssc.report.health", name = "enabled", havingValue = "true")
public class ReportDomainHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Value("${fssc.datasource.replica.enabled:false}")
    private boolean replicaEnabled;

    @Value("${fssc.remote.image.enabled:false}")
    private boolean imageRemote;

    @Value("${fssc.remote.image.base-url:http://127.0.0.1:8090}")
    private String imageBaseUrl;

    @Value("${server.port:8094}")
    private int serverPort;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public ReportDomainHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        boolean up = true;
        Map<String, Object> details = new LinkedHashMap<>();

        Map<String, Object> db = probeReplicaSelect();
        details.put("database", db);
        if (!"UP".equals(db.get("status"))) {
            up = false;
        }

        Map<String, Object> guard = probeWriteGuard();
        details.put("writeGuard", guard);
        if (!"BITES".equals(guard.get("status"))) {
            up = false;
        }

        details.put("replicaMode", replicaEnabled ? "dedicated-replica" : "master-alias(guarded)");
        details.put("imageUpstream", probeImage());

        return (up ? Health.up() : Health.down()).withDetails(details).build();
    }

    /** 在 replica 路由上执行 SELECT 1（路由键只在本线程临时设置，结束即恢复）。 */
    private Map<String, Object> probeReplicaSelect() {
        Map<String, Object> m = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        String previous = DataSourceContextHolder.get();
        DataSourceContextHolder.set(DataSourceConfig.DS_REPLICA);
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select 1")) {
            boolean ok = rs.next() && rs.getInt(1) == 1;
            m.put("status", ok ? "UP" : "DOWN");
        } catch (Throwable t) {
            m.put("status", "DOWN");
            m.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (previous != null) {
                DataSourceContextHolder.set(previous);
            } else {
                DataSourceContextHolder.clear();
            }
            m.put("latencyMs", System.currentTimeMillis() - start);
        }
        return m;
    }

    /**
     * 只读守卫实证：对 replica 路由 prepare 一条 UPDATE，期望在<b>发出前</b>被拒。
     * 用不存在的探针表名，即使守卫失效也不会落在真实业务表上。
     */
    private Map<String, Object> probeWriteGuard() {
        Map<String, Object> m = new LinkedHashMap<>();
        String previous = DataSourceContextHolder.get();
        DataSourceContextHolder.set(DataSourceConfig.DS_REPLICA);
        try (Connection c = dataSource.getConnection()) {
            c.prepareStatement("update __readonly_guard_probe__ set x = 1 where id = -1").close();
            m.put("status", "OPEN");
            m.put("detail", "只读守卫未拦截 UPDATE");
            log.error("reportDomain.writeGuard=OPEN：replica 路由接受了写语句，只读副本可能被写入");
        } catch (BizException be) {
            m.put("status", "BITES");
            m.put("detail", be.getMessage());
        } catch (Throwable t) {
            m.put("status", "OPEN");
            m.put("detail", "非守卫异常，写语句已下发到驱动：" + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (previous != null) {
                DataSourceContextHolder.set(previous);
            } else {
                DataSourceContextHolder.clear();
            }
        }
        return m;
    }

    private Map<String, Object> probeImage() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!imageRemote) {
            m.put("status", "DISABLED");
            return m;
        }
        long start = System.currentTimeMillis();
        try {
            boolean self = URI.create(imageBaseUrl).getPort() == serverPort;
            m.put("self", self);
            HttpRequest req = HttpRequest.newBuilder(URI.create(imageBaseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() == null ? "" : resp.body();
            boolean up = resp.statusCode() == 200 && body.contains("\"status\":\"UP\"");
            m.put("status", up ? "UP" : "DOWN");
            m.put("httpStatus", resp.statusCode());
        } catch (Throwable t) {
            m.put("status", "DOWN");
            m.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        m.put("latencyMs", System.currentTimeMillis() - start);
        m.put("baseUrl", imageBaseUrl);
        return m;
    }
}
