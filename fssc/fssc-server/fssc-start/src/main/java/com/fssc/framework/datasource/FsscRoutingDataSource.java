package com.fssc.framework.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 路由数据源（@Primary，全工程唯一 DataSource 入口）。
 * <p>
 * 每次取连接时读取 {@link DataSourceContextHolder} 的路由键：
 * null → 默认 master；master/image → 对应 Hikari 连接池；未知键直接报错
 * （lenientFallback=false，宁可快速失败也不静默走错库）。
 * <p>
 * MyBatis-Plus 仍单 SqlSessionFactory，租户拦截器/分页插件对两个库的 SQL 同样生效。
 */
public class FsscRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
