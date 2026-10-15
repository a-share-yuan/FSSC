package com.fssc.framework.datasource;

/**
 * 数据源路由键上下文（ThreadLocal）。
 * <p>
 * 与 {@link TenantContext} 同模式：仅由 {@link DataSourceAspect} 写入/恢复，
 * 业务代码不允许直接调用；{@link FsscRoutingDataSource} 在每次取连接时读取。
 * null 表示未指定，路由到默认数据源 master。
 */
public final class DataSourceContextHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private DataSourceContextHolder() {
    }

    public static void set(String dataSource) {
        HOLDER.set(dataSource);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
