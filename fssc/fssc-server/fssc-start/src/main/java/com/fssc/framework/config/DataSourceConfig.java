// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import com.fssc.framework.datasource.FsscRoutingDataSource;
import com.fssc.framework.datasource.ReadOnlyGuardDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源配置（分库改造）。
 * <p>
 * master：主库 fssc，承载除影像 6 表外的全部业务表、平台表与 Flowable 流程表；
 * image：影像库 fssc_image，承载 img_batch/img_file/img_setting/image_exception/image_review/ocr_record；
 * replica：报表/追溯只读副本。未配置独立副本时（fssc.datasource.replica.enabled≠true）
 *           别名到 master，但<b>同样</b>套 {@link ReadOnlyGuardDataSource}——先证明"只读"，
 *           等真副本就绪只改连接串即可切换；
 * document：档案域自治库（fssc.datasource.document.enabled≠true 时别名 master）；
 * invoice：发票域自治库（fssc.datasource.invoice.enabled≠true 时别名 master）。
 * <p>
 * 各 Hikari 连接池的属性直接绑定各自前缀（fssc.datasource.*，使用 jdbc-url 等 Hikari 原生键），
 * 不依赖 Boot 的 DataSourceAutoConfiguration —— 本类声明 DataSource bean 后该自动配置退避。
 * <p>
 * 对外只暴露一个 @Primary 路由数据源（{@link FsscRoutingDataSource}），因此：
 * <ul>
 *   <li>DataSourceTransactionManager / JdbcTemplate / Flowable 自动配置均以路由数据源为唯一候选，
 *       事务在开启瞬间按当前路由键绑定对应库连接；</li>
 *   <li>MyBatis-Plus 保持单 SqlSessionFactory，租户拦截器与分页插件对四个库同样生效。</li>
 * </ul>
 */
@Configuration
public class DataSourceConfig {

    public static final String DS_MASTER = "master";
    public static final String DS_IMAGE = "image";
    public static final String DS_REPLICA = "replica";
    public static final String DS_DOCUMENT = "document";
    public static final String DS_INVOICE = "invoice";

    @Bean
    @ConfigurationProperties(prefix = "fssc.datasource.master")
    public HikariDataSource masterDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties(prefix = "fssc.datasource.image")
    public HikariDataSource imageDataSource() {
        return new HikariDataSource();
    }

    /** 只读副本连接池：仅当显式配置了 fssc.datasource.replica.* 时创建 */
    @Bean
    @ConditionalOnProperty(prefix = "fssc.datasource.replica", name = "enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "fssc.datasource.replica")
    public HikariDataSource replicaDataSource() {
        return new HikariDataSource();
    }

    /** 档案域自治库连接池：仅当 fssc.datasource.document.enabled=true 时创建 */
    @Bean
    @ConditionalOnProperty(prefix = "fssc.datasource.document", name = "enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "fssc.datasource.document")
    public HikariDataSource documentDataSource() {
        return new HikariDataSource();
    }

    /** 发票域自治库连接池：仅当 fssc.datasource.invoice.enabled=true 时创建 */
    @Bean
    @ConditionalOnProperty(prefix = "fssc.datasource.invoice", name = "enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "fssc.datasource.invoice")
    public HikariDataSource invoiceDataSource() {
        return new HikariDataSource();
    }

    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("masterDataSource") HikariDataSource master,
                                 @Qualifier("imageDataSource") HikariDataSource image,
                                 @Qualifier("replicaDataSource") ObjectProvider<HikariDataSource> replica,
                                 @Qualifier("documentDataSource") ObjectProvider<HikariDataSource> document,
                                 @Qualifier("invoiceDataSource") ObjectProvider<HikariDataSource> invoice) {
        FsscRoutingDataSource routing = new FsscRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DS_MASTER, master);
        targets.put(DS_IMAGE, image);

        // 只读路由：有独立副本走副本池，否则别名 master；两种情况都套只读守卫，
        // 使"报表/追溯不写库"这件事在单库部署下同样成立且可被自检探针复现。
        HikariDataSource replicaPool = replica.getIfAvailable();
        targets.put(DS_REPLICA, new ReadOnlyGuardDataSource(
                replicaPool != null ? replicaPool : master,
                replicaPool != null ? "replica-pool" : "master-alias"));

        // 档案/发票：独立库就绪前别名 master，业务代码先用 @FsscDataSource 标注归属，
        // 切库时只改配置不改编排。
        targets.put(DS_DOCUMENT, docOrAlias(document.getIfAvailable(), master));
        targets.put(DS_INVOICE, docOrAlias(invoice.getIfAvailable(), master));

        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(master);
        // 未知路由键直接抛异常，快速暴露配置/注解错误，不静默回落主库
        routing.setLenientFallback(false);
        return routing;
    }

    private static DataSource docOrAlias(HikariDataSource dedicated, HikariDataSource master) {
        return dedicated != null ? dedicated : master;
    }
}
