package com.fssc.framework.tenant;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;

/**
 * 租户上下文（线程级）。整个多租户隔离的"真相源"：
 * <ul>
 *   <li>tenantId：当前请求归属的租户；平台端恒为 null。</li>
 *   <li>ignore：是否忽略租户过滤（仅 {@link #runIgnoreTenant} 显式开启，用于跨租户聚合）。</li>
 * </ul>
 *
 * <h3>fail-closed 铁律</h3>
 * 业务 SQL 走到租户插件时，若 {@code ignore=false} 且 {@code tenantId=null}，
 * 说明"没有租户条件的业务 SQL 即将放行" —— 直接抛异常，宁可报错绝不串户。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   // 认证链路：tenantId 来自已验签的 Token（非客户端输入）
 *   TenantContext.callAs(tenantId, () -> service.bizQuery());
 *
 *   // 平台端跨租户统计（只允许聚合，禁止查明细）
 *   TenantContext.runIgnoreTenant(() -> statMapper.countTenants());
 * </pre>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> TL = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 建立租户上下文并执行（自动 restore 旧值） */
    public static void callAs(Long tenantId, Runnable action) {
        TenantInfo prev = TL.get();
        try {
            TL.set(new TenantInfo(tenantId, false));
            action.run();
        } finally {
            TL.set(prev); // prev 可能为 null
        }
    }

    /** 忽略租户过滤执行（跨租户聚合专用） */
    public static void runIgnoreTenant(Runnable action) {
        TenantInfo prev = TL.get();
        try {
            TL.set(new TenantInfo(null, true));
            action.run();
        } finally {
            TL.set(prev);
        }
    }

    /** 直接设置（JwtAuthFilter 在建立 LoginUser 后调用） */
    public static void set(Long tenantId) {
        TL.set(new TenantInfo(tenantId, false));
    }

    /** 平台端调用：tenantId 置 null，且不做 ignore（pf_* 表靠忽略表白名单放行） */
    public static void setPlatform() {
        TL.set(new TenantInfo(null, false));
    }

    /**
     * 读取当前租户，<b>无上下文时返回 null 而不抛异常</b>。
     *
     * <p>只给「有上下文就收窄、没有就跨租户扫描」的调用点用（如
     * {@code EngineTenantScope}）：调度线程本来就没有租户上下文，用
     * {@link #getTenantId()} 会直接抛「TenantContext 未初始化」，把整个定时任务打死。
     * <b>业务 SQL 一律用 {@link #getTenantId()}</b>，那里的 fail-closed 不能放松。
     */
    public static Long peekTenantId() {
        TenantInfo info = TL.get();
        return info == null ? null : info.tenantId;
    }

    public static Long getTenantId() {
        TenantInfo info = TL.get();
        if (info == null) {
            throw new BizException(ErrorCode.TENANT_CONTEXT_MISSING, "TenantContext 未初始化");
        }
        if (!info.ignore && info.tenantId == null) {
            // 真正的 fail-closed：有业务表需要过滤却拿不到租户ID
            throw new BizException(ErrorCode.TENANT_CONTEXT_MISSING, "业务SQL缺少租户条件");
        }
        return info.tenantId;
    }

    public static boolean isIgnoreTenant() {
        TenantInfo info = TL.get();
        return info != null && info.ignore;
    }

    /** 是否处于平台上下文（tenantId 为 null 且未忽略） */
    public static boolean isPlatform() {
        TenantInfo info = TL.get();
        return info != null && info.tenantId == null && !info.ignore;
    }

    public static void clear() {
        TL.remove();
    }

    /** 仅供测试/内部断言：是否已有上下文 */
    public static boolean hasContext() {
        return TL.get() != null;
    }

    private static final class TenantInfo {
        private final Long tenantId;
        private final boolean ignore;

        private TenantInfo(Long tenantId, boolean ignore) {
            this.tenantId = tenantId;
            this.ignore = ignore;
        }
    }
}
