// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.enums.DataScope;

import java.util.Set;

/**
 * 数据权限运行时载体（线程级）。由 {@code PermissionAspect} 在 @DataScope 方法入口写入，
 * Mapper 通过它拼装 SQL 条件（如 {@code AND create_by = #{currentUserId}} 当 scope=5）。
 * <p>
 * 仅承载"本请求这次查询"的数据范围，方法结束即清空。
 */
public final class DataScopeHolder {

    private static final ThreadLocal<Scope> TL = new ThreadLocal<>();

    private DataScopeHolder() {
    }

    public static void set(int scope, Long companyId, Long deptId, Long userId, Set<Long> deptAndChild) {
        TL.set(new Scope(scope, companyId, deptId, userId, deptAndChild));
    }

    public static Scope get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }

    public static final class Scope {
        private final int scope;
        private final Long companyId;
        private final Long deptId;
        private final Long userId;
        private final Set<Long> deptAndChild;

        public Scope(int scope, Long companyId, Long deptId, Long userId, Set<Long> deptAndChild) {
            this.scope = scope;
            this.companyId = companyId;
            this.deptId = deptId;
            this.userId = userId;
            this.deptAndChild = deptAndChild;
        }

        public int getScope() {
            return scope;
        }

        public Long getCompanyId() {
            return companyId;
        }

        public Long getDeptId() {
            return deptId;
        }

        public Long getUserId() {
            return userId;
        }

        public Set<Long> getDeptAndChild() {
            return deptAndChild;
        }

        public DataScope dataScope() {
            return DataScope.of(scope);
        }
    }
}
