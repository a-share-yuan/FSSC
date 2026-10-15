package com.fssc.framework.config;

import com.fssc.framework.datasource.FsscRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 双数据源配置（分库改造：影像域独立 fssc_image 库）。
 * <p>
 * master：主库 fssc，承载除影像 6 表外的全部业务表、平台表与 Flowable 流程表；
 * image ：影像库 fssc_image，承载 img_batch/img_file/img_setting/image_exception/image_review/ocr_record。
 * <p>
 * 两个 Hikari 连接池的属性直接绑定各自前缀（fssc.datasource.master / fssc.datasource.image，
 * 使用 jdbc-url 等 Hikari 原生键，不依赖 Boot 的 DataSourceAutoConfiguration —— 本类声明
 * DataSource bean 后该自动配置退避）。
 * <p>
 * 对外只暴露一个 @Primary 路由数据源（{@link FsscRoutingDataSource}），因此：
 * <ul>
 *   <li>DataSourceTransactionManager / JdbcTemplate / Flowable 自动配置均以路由数据源为唯一候选，
 *       事务在开启瞬间按当前路由键绑定对应库连接；</li>
 *   <li>MyBatis-Plus 保持单 SqlSessionFactory，租户拦截器与分页插件对两个库同样生效。</li>
 * </ul>
 */
@Configuration
public class DataSourceConfig {

    public static final String DS_MASTER = "master";
    public static final String DS_IMAGE = "image";

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

    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("masterDataSource") HikariDataSource master,
                                 @Qualifier("imageDataSource") HikariDataSource image) {
        FsscRoutingDataSource routing = new FsscRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DS_MASTER, master);
        targets.put(DS_IMAGE, image);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(master);
        // 未知路由键直接抛异常，快速暴露配置/注解错误，不静默回落主库
        routing.setLenientFallback(false);
        return routing;
    }
}
