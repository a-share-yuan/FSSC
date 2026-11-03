// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.health;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 影像服务专属健康组件（fssc.image.health.enabled=true 才装配，仅独立部署 profile 打开）。
 *
 * <p>三项判据：</p>
 * <ul>
 *   <li><b>database</b>：直连 {@code imageDataSource}（fssc_image 库）执行
 *       {@code select 1 from img_batch limit 1}——连不上或表缺失即 DOWN（空表算通）；</li>
 *   <li><b>storage</b>：在 {@code fssc.file.local-root} 下真实写入并删除探针文件，
 *       可写才算 UP（只读挂载/磁盘满在这里暴露，而不是等用户上传失败）；</li>
 *   <li><b>ocrChannel / invoicePool</b>：当前识别通道与跨域入池形态，仅作信息项（不参与 UP/DOWN）。</li>
 * </ul>
 *
 * <p>注册进 {@code /actuator/health} 的聚合结果（show-details=always），
 * k8s liveness/readiness 走 {@code /actuator/health/liveness|readiness} 不受本组件影响。</p>
 */
@Slf4j
@Component("imageDomain")
@ConditionalOnProperty(prefix = "fssc.image.health", name = "enabled", havingValue = "true")
public class ImageDomainHealthIndicator implements HealthIndicator {

    private static final String PROBE_FILE = ".health-probe";

    private final HikariDataSource imageDataSource;
    private final String fileRoot;
    private final String ocrChannel;
    private final boolean standalone;

    public ImageDomainHealthIndicator(@Qualifier("imageDataSource") HikariDataSource imageDataSource,
                                      @Value("${fssc.file.local-root:./data/files}") String fileRoot,
                                      @Value("${fssc.ocr.channel:mock}") String ocrChannel,
                                      @Value("${fssc.image.standalone:false}") boolean standalone) {
        this.imageDataSource = imageDataSource;
        this.fileRoot = fileRoot;
        this.ocrChannel = ocrChannel;
        this.standalone = standalone;
    }

    @Override
    public Health health() {
        boolean up = true;

        Map<String, Object> db = new LinkedHashMap<>();
        long startedAt = System.nanoTime();
        try (Connection conn = imageDataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select 1 from img_batch limit 1")) {
            db.put("status", "UP");
            db.put("table", "img_batch");
            rs.next();
        } catch (Exception ex) {
            up = false;
            db.put("status", "DOWN");
            db.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            log.warn("影像库健康检查失败：{}", ex.getMessage());
        }
        db.put("latencyMs", (System.nanoTime() - startedAt) / 1_000_000);

        Map<String, Object> storage = new LinkedHashMap<>();
        try {
            Path root = Paths.get(fileRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path probe = root.resolve(PROBE_FILE);
            Files.write(probe, "ok".getBytes(StandardCharsets.UTF_8));
            Files.deleteIfExists(probe);
            storage.put("status", "UP");
        } catch (Exception ex) {
            up = false;
            storage.put("status", "DOWN");
            storage.put("error", ex.getClass().getSimpleName());
            log.warn("影像存储根不可写（{}）：{}", fileRoot, ex.getMessage());
        }

        return (up ? Health.up() : Health.down())
                .withDetail("database", db)
                .withDetail("storage", storage)
                .withDetail("ocrChannel", ocrChannel)
                .withDetail("invoicePool", standalone ? "REMOTE_NOT_WIRED" : "IN_PROCESS")
                .build();
    }
}
