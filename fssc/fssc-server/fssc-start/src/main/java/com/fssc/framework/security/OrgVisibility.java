// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import java.util.Objects;

/**
 * 组织可见范围判定（公司维度）。
 *
 * <p>复用登录态里已有的 {@code dataScope}（{@code com.fssc.common.enums.DataScope}，1-5 档）
 * 与权限码，不另立枚举。用途：公司树 / 部门树 / 人员下拉等"跨公司可选"的读接口，
 * 在出参侧裁到登录人的可见范围，避免普通员工从下拉里看到其他法人主体。</p>
 *
 * <p><b>放行规则（fail-open 仅限明确的管理身份）</b>：
 * <ul>
 *   <li>无登录态（内部调用/测试）：放行——这些路径不经 HTTP，不受本判定约束；</li>
 *   <li>平台端、通配权限 {@code *:*:*}（租户管理员）：放行；</li>
 *   <li>持组织管理码 {@code system:company:list} 或 {@code system:org:view-all}：放行；</li>
 *   <li>{@code data_scope=1}（本租户全部）：放行；</li>
 *   <li>登录人无公司维度（集团级账号）：放行——与 {@code OrgGuard} 的既有规则一致。</li>
 * </ul>
 * 其余一律只看本公司。</p>
 */
public final class OrgVisibility {

    /** 组织管理的明示权限码（公司管理菜单实际授予的码） */
    public static final String PERM_ORG_MANAGE = "system:company:list";
    /** 跨公司查看的扩展码（本仓暂无授予，留作管理员显式放行位） */
    public static final String PERM_ORG_VIEW_ALL = "system:org:view-all";

    private OrgVisibility() {
    }

    /** 是否可跨公司看到全部组织 */
    public static boolean canSeeAllCompanies(LoginUser u) {
        if (u == null) {
            return true;
        }
        if (u.isPlatform() || u.isAdmin()) {
            return true;
        }
        if (u.hasPerm(PERM_ORG_MANAGE) || u.hasPerm(PERM_ORG_VIEW_ALL)) {
            return true;
        }
        if (u.getDataScope() != null && u.getDataScope() == 1) {
            return true;
        }
        // 无公司维度＝集团级账号：既有 OrgGuard/WfReadGuard 均按"放宽"处理
        return u.getCompanyId() == null;
    }

    /** 是否可查看指定公司 */
    public static boolean canSeeCompany(LoginUser u, Long companyId) {
        if (canSeeAllCompanies(u)) {
            return true;
        }
        return companyId != null && Objects.equals(u.getCompanyId(), companyId);
    }
}
