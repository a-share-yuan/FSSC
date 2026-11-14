package com.fssc.framework.datasource;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 只读守卫数据源：包在副本连接池外面，任何非只读 SQL 在<b>发出之前</b>即被拒绝。
 *
 * <p>为什么在 JDBC 层做而不是在 MyBatis 拦截器层：报表/追溯域既有 MyBatis Mapper 也有
 * {@code JdbcTemplate} 与手写 SQL，只有连接层能一次覆盖全部入口。判据是"能不能写"，
 * 与调用方式无关。</p>
 *
 * <p>允许的语句：SELECT / SHOW / DESC / DESCRIBE / EXPLAIN，以及不含写关键字的 WITH 查询。
 * 拒绝：INSERT/UPDATE/DELETE/REPLACE/DDL/TRUNCATE/CALL、SELECT … FOR UPDATE（加锁读同样写）。</p>
 *
 * <p>连接与语句对象用代理包装：prepareStatement/prepareCall 在<b>创建时</b>校验 SQL，
 * createStatement 在 execute 系列方法/addBatch 时校验实参 SQL。</p>
 */
public class ReadOnlyGuardDataSource implements DataSource {

    private static final Pattern FOR_UPDATE = Pattern.compile("\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WITH_WRITE = Pattern.compile(
            "\\b(insert|update|delete|replace|merge|alter|drop|truncate|create)\\b", Pattern.CASE_INSENSITIVE);

    private final DataSource target;
    private final String label;

    public ReadOnlyGuardDataSource(DataSource target, String label) {
        this.target = target;
        this.label = label;
    }

    // 判定

    /** 只读判定；不合规直接抛，不降级为"试一下再说"。 */
    public static void assertReadOnly(String sql) {
        String norm = normalize(sql);
        if (norm.isEmpty()) {
            return;
        }
        String head = norm.toLowerCase(Locale.ROOT);
        boolean allowed = head.startsWith("select") || head.startsWith("show")
                || head.startsWith("desc") || head.startsWith("explain")
                || (head.startsWith("with") && !WITH_WRITE.matcher(head).find());
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "只读副本拒绝写操作：" + head.substring(0, Math.min(40, head.length())));
        }
        if (FOR_UPDATE.matcher(head).find()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只读副本拒绝加锁读（SELECT … FOR UPDATE）");
        }
    }

    private static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.trim();
        // 去掉前导注释（-- 与 /* */），否则注释后的第一条关键字判不出来
        boolean changed = true;
        while (changed && !s.isEmpty()) {
            changed = false;
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                s = nl < 0 ? "" : s.substring(nl + 1).trim();
                changed = true;
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                s = end < 0 ? "" : s.substring(end + 2).trim();
                changed = true;
            }
        }
        return s;
    }

    // 代理

    @Override
    public Connection getConnection() throws SQLException {
        return guard(target.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return guard(target.getConnection(username, password));
    }

    private Connection guard(Connection c) {
        return (Connection) Proxy.newProxyInstance(
                ReadOnlyGuardDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (isPrepare(method) && args != null && args.length > 0 && args[0] instanceof String sql) {
                        assertReadOnly(sql);
                    }
                    Object result = invoke(method, c, args);
                    if (result instanceof CallableStatement cs) {
                        return guardPrepared(cs);
                    }
                    if (result instanceof PreparedStatement ps) {
                        return guardPrepared(ps);
                    }
                    if (result instanceof Statement st) {
                        return guardStatement(st);
                    }
                    return result;
                });
    }

    private static boolean isPrepare(Method m) {
        String n = m.getName();
        return n.startsWith("prepare") || n.startsWith("nativeSQL");
    }

    private Statement guardStatement(Statement st) {
        InvocationHandler h = (proxy, method, args) -> {
            if (args != null && args.length > 0 && args[0] instanceof String sql) {
                assertReadOnly(sql);
            }
            return invoke(method, st, args);
        };
        return (Statement) Proxy.newProxyInstance(
                ReadOnlyGuardDataSource.class.getClassLoader(),
                new Class<?>[]{Statement.class}, h);
    }

    private PreparedStatement guardPrepared(PreparedStatement ps) {
        InvocationHandler h = (proxy, method, args) -> invoke(method, ps, args);
        Class<?>[] ifaces = ps instanceof CallableStatement
                ? new Class<?>[]{CallableStatement.class}
                : new Class<?>[]{PreparedStatement.class};
        return (PreparedStatement) Proxy.newProxyInstance(
                ReadOnlyGuardDataSource.class.getClassLoader(), ifaces, h);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    // 透传

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return target.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        target.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        target.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return target.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return target.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return target.isWrapperFor(iface);
    }

    @Override
    public String toString() {
        return "ReadOnlyGuardDataSource[" + label + "]";
    }
}
