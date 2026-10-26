// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康探测服务（P0-B1）。
 *
 * <p>供两处复用，保证「探活规则」唯一：
 * <ul>
 *   <li>{@link HealthController} —— 免鉴权 HTTP 探活（/api/health）；</li>
 *   <li>{@link HealthProbeJob} —— 定时探测并在状态翻转时推运维告警。</li>
 * </ul>
 *
 * <p>探测项：数据库连通性（必需）、Redis（仅 {@code fssc.redis.enabled=true} 时纳入，
 * 关闭时标记 DISABLED 而非 DOWN，避免单机部署被误判不健康）。
 * 任一必需项不可用 → 总状态 DOWN，HTTP 层据此返回非 0 code，便于检查判定。
 *
 * <p>本类不做任何需要租户上下文的查询：JdbcTemplate 绕过 MyBatis 拦截器，天然无租户依赖，
 * 可在白名单（无 Token）场景安全调用。
 */
@Slf4j
@Service
public class HealthService {

    /** DB 探测超时（秒）：探活必须快速返回，不能因 DB 卡住拖垮检查 */
    private static final int DB_TIMEOUT_SECONDS = 3;

    /** 磁盘可用空间低于该值（GB）判定 DOWN：写日志/写临时文件都会失败 */
    private static final long DISK_MIN_USABLE_GB = 1;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Value("${fssc.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${info.app.name:fssc-server}")
    private String appName;

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    /** JVM 启动时刻（比 Bean 构造更准确，涵盖更早的类加载阶段） */
    private final long jvmStartMillis = ManagementFactory.getRuntimeMXBean().getStartTime();

    public HealthService(JdbcTemplate jdbcTemplate,
                         ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 执行一次健康探测。
     *
     * @return 结构化的探测结果：status / components / app / version / uptimeSeconds / serverTime / jvm
     */
    public Map<String, Object> check() {
        Map<String, Object> components = new LinkedHashMap<>();
        boolean dbUp = probeDb(components);
        boolean redisUp = probeRedis(components);
        boolean diskUp = probeDisk(components);
        probeJvm(components);
        boolean up = dbUp && redisUp && diskUp;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", up ? "UP" : "DOWN");
        r.put("components", components);
        r.put("app", appName);
        r.put("version", appVersion);
        r.put("serverTime", LocalDateTime.now().format(FMT));
        r.put("uptimeSeconds", Duration.ofMillis(System.currentTimeMillis() - jvmStartMillis).getSeconds());
        r.put("javaVersion", System.getProperty("java.version"));
        return r;
    }

    /** 总状态是否 UP（供检查与告警判定，避免调用方解析 Map） */
    public boolean isUp() {
        return "UP".equals(check().get("status"));
    }

    /** 数据库连通性（必需项）：用 select 1 并设查询超时，异常一律吞掉转 DOWN 并记明细 */
    private boolean probeDb(Map<String, Object> components) {
        try {
            jdbcTemplate.setQueryTimeout(DB_TIMEOUT_SECONDS);
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            boolean ok = one != null && one == 1;
            components.put("db", ok ? "UP" : "DOWN");
            return ok;
        } catch (Exception e) {
            components.put("db", "DOWN");
            components.put("dbError", briefError(e));
            log.warn("[健康探测] 数据库不可用：{}", e.getMessage());
            return false;
        }
    }

    /** Redis（可选项）：未启用时标 DISABLED（不算故障），启用后 ping 失败才算 DOWN */
    private boolean probeRedis(Map<String, Object> components) {
        if (!redisEnabled) {
            components.put("redis", "DISABLED");
            return true;
        }
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            components.put("redis", "DOWN");
            components.put("redisError", "StringRedisTemplate 未装配");
            return false;
        }
        try {
            String pong = template.execute(org.springframework.data.redis.connection.RedisConnection::ping);
            boolean ok = pong != null;
            components.put("redis", ok ? "UP" : "DOWN");
            return ok;
        } catch (Exception e) {
            components.put("redis", "DOWN");
            components.put("redisError", briefError(e));
            log.warn("[健康探测] Redis 不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 磁盘探测（必需项）：取工作目录所在盘的总量/可用量，可用 < 1GB 判 DOWN */
    private boolean probeDisk(Map<String, Object> components) {
        try {
            java.io.File dir = new java.io.File(".").getAbsoluteFile();
            long totalGb = dir.getTotalSpace() / (1024L * 1024L * 1024L);
            long usableGb = dir.getUsableSpace() / (1024L * 1024L * 1024L);
            boolean ok = dir.getUsableSpace() > DISK_MIN_USABLE_GB * 1024L * 1024L * 1024L;
            components.put("disk", ok ? "UP" : "DOWN");
            // R5-D-050：只回盘符根（相对路径 getParent() 会把部署目录结构泄到未鉴权端点）
            java.nio.file.Path diskRoot = dir.toPath().getRoot();
            components.put("diskPath", diskRoot != null ? diskRoot.toString() : "");
            components.put("diskTotalGb", totalGb);
            components.put("diskUsableGb", usableGb);
            if (!ok) {
                log.warn("[健康探测] 磁盘可用空间不足 {}GB：剩余 {}GB", DISK_MIN_USABLE_GB, usableGb);
            }
            return ok;
        } catch (Exception e) {
            components.put("disk", "DOWN");
            components.put("diskError", briefError(e));
            log.warn("[健康探测] 磁盘探测失败：{}", e.getMessage());
            return false;
        }
    }

    /** JVM 堆内存指标（信息项，不参与健康判定）：used/max MB，供「运行状态」页展示 */
    private void probeJvm(Map<String, Object> components) {
        try {
            java.lang.management.MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            long usedMb = mem.getHeapMemoryUsage().getUsed() / (1024L * 1024L);
            long maxMb = mem.getHeapMemoryUsage().getMax() / (1024L * 1024L);
            components.put("jvmHeapUsedMb", usedMb);
            components.put("jvmHeapMaxMb", maxMb);
            components.put("jvmThreads", ManagementFactory.getThreadMXBean().getThreadCount());
        } catch (Exception e) {
            // 堆内存属信息项，探测失败仅记录摘要，不影响整体健康判定
            components.put("jvmError", briefError(e));
        }
    }

    /** 错误摘要：只留异常类名 + 简短原因，避免把堆栈或连接串（含口令）写进健康响应 */
    private String briefError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String one = msg.replaceAll("\\s+", " ").trim();
        return one.length() <= 160 ? one : one.substring(0, 160);
    }
}
