// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.enums.DataScope;
import com.fssc.common.exception.BizException;

import java.util.Set;

/**
 * data_scope 五级过滤工具（安全审计③：让 @DataScope 注解真正生效）。
 *
 * <p>用于在 {@code @DataScope} 标注的列表查询方法内，依据当前登录人的数据范围向
 * LambdaQueryWrapper 追加组织维度条件：</p>
 * <ul>
 *   <li>1 TENANT（本租户全部）→ 不加任何条件；</li>
 *   <li>2 COMPANY（本公司）→ {@code companyCol = 登录人公司}；</li>
 *   <li>3 DEPT_AND_CHILD（本部门及以下）→ {@code deptCol IN(本部门+下级部门)}，无部门信息时降级本公司；</li>
 *   <li>4 DEPT（本部门）→ {@code deptCol = 登录人部门}，无部门信息时降级本公司；</li>
 *   <li>5 SELF（仅本人）→ {@code ownerCol = 登录人}，无 owner 列时降级本公司。</li>
 * </ul>
 *
 * <p>调用前提：{@link PermissionAspect#aroundDataScope} 已在方法入口把 scope/companyId/deptId/
 * deptAndChild 写入 {@link DataScopeHolder}。本工具只读该载体；若载体为空（方法未标注 @DataScope）
 * 则直接跳过，不影响原有查询。</p>
 *
 * <p>设计取舍：采用"各列表方法显式调用"而非全局 SQL 改写拦截器，避免对跨表 join 聚合查询
 * 报表统计 误注入列导致的 SQL 错误，风险更低、行为可预期。</p>
 */
public final class DataScopeKit {

    private DataScopeKit() {
    }

    /**
     * 向查询追加数据范围条件。
     *
     * @param qw 查询构造器（会被原地修改）
     * @param companyCol 公司列（如 OpVoucher::getCompanyId）；无则传 null
     * @param deptCol 部门列（如 BgItem::getDeptId）；无则传 null
     * @param ownerCol 本人列（如 ExpBill::getApplyUserId）；无则传 null
     * @param <T> 实体类型
     */
    public static <T> void apply(LambdaQueryWrapper<T> qw,
                                 SFunction<T, ?> companyCol,
                                 SFunction<T, ?> deptCol,
                                 SFunction<T, ?> ownerCol) {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return; // 非 @DataScope 方法：不干预
        }
        int scope = s.getScope();
        if (scope <= DataScope.TENANT.getCode()) {
            return; // 本租户全部：不加条件
        }
        if (scope == DataScope.COMPANY.getCode()) {
            companyEq(qw, companyCol, s.getCompanyId());
            return;
        }
        if (scope == DataScope.DEPT_AND_CHILD.getCode() || scope == DataScope.DEPT.getCode()) {
            Set<Long> child = s.getDeptAndChild();
            if (deptCol != null && s.getDeptId() != null
                    && scope == DataScope.DEPT_AND_CHILD.getCode() && child != null && !child.isEmpty()) {
                qw.in(deptCol, child);
            } else if (deptCol != null && s.getDeptId() != null) {
                qw.eq(deptCol, s.getDeptId());
            } else {
                companyEq(qw, companyCol, s.getCompanyId()); // 无部门信息降级本公司
            }
            return;
        }
        if (scope == DataScope.SELF.getCode()) {
            if (ownerCol != null && s.getUserId() != null) {
                qw.eq(ownerCol, s.getUserId());
            } else {
                companyEq(qw, companyCol, s.getCompanyId()); // 无本人列降级本公司
            }
        }
    }

    /**
     * {@link #apply} 的「租户共用行可见」变体——专用于 {@code company_id IS NULL} 表示
     * "租户级共用主数据"的表（md_subject；见其 {@code uk_tenant_subject} 规则与实体注释）。
     *
     * <p>与 {@link #apply} 唯一的差别：凡落到公司条件处都写成
     * {@code (company_id = 登录人公司 OR company_id IS NULL)}。否则调用方先构造的
     * 「本公司 ∪ 共用」并集会被这里追加的裸 {@code company_id = C} 收回——10-31 实测
     * 会计科目树因此缺掉 2202/2221/6001/6401/6602 等共用父级，子级行挂在树上没有落点。</p>
     *
     * <p>不放开跨公司：仍按登录人公司取等值，只额外放行 company_id 为空的共用行。</p>
     */
    public static <T> void applyWithNullCompany(LambdaQueryWrapper<T> qw,
                                                SFunction<T, ?> companyCol,
                                                SFunction<T, ?> deptCol,
                                                SFunction<T, ?> ownerCol) {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return; // 非 @DataScope 方法：不干预
        }
        int scope = s.getScope();
        if (scope <= DataScope.TENANT.getCode()) {
            return; // 本租户全部：不加条件
        }
        if (scope == DataScope.COMPANY.getCode()) {
            companyOrEmpty(qw, companyCol, s.getCompanyId());
            return;
        }
        if (scope == DataScope.DEPT_AND_CHILD.getCode() || scope == DataScope.DEPT.getCode()) {
            Set<Long> child = s.getDeptAndChild();
            if (deptCol != null && s.getDeptId() != null
                    && scope == DataScope.DEPT_AND_CHILD.getCode() && child != null && !child.isEmpty()) {
                qw.in(deptCol, child);
            } else if (deptCol != null && s.getDeptId() != null) {
                qw.eq(deptCol, s.getDeptId());
            } else {
                companyOrEmpty(qw, companyCol, s.getCompanyId()); // 无部门信息降级本公司
            }
            return;
        }
        if (scope == DataScope.SELF.getCode()) {
            if (ownerCol != null && s.getUserId() != null) {
                qw.eq(ownerCol, s.getUserId());
            } else {
                companyOrEmpty(qw, companyCol, s.getCompanyId()); // 无本人列降级本公司
            }
            return;
        }
    }

    /** 公司谓词写法一：{@code company_id = C}（默认，严格公司隔离）。 */
    private static <T> void companyEq(LambdaQueryWrapper<T> qw, SFunction<T, ?> companyCol, Long companyId) {
        if (companyCol != null && companyId != null) {
            qw.eq(companyCol, companyId);
        }
    }

    /** 公司谓词写法二：{@code (company_id = C OR company_id IS NULL)}——放行租户共用行。 */
    private static <T> void companyOrEmpty(LambdaQueryWrapper<T> qw, SFunction<T, ?> companyCol, Long companyId) {
        if (companyCol != null && companyId != null) {
            qw.and(w -> w.eq(companyCol, companyId).or().isNull(companyCol));
        }
    }

    /**
     * {@link #apply} 的「公司级行可见」变体——专用于预算明细等"按公司下达、
     * dept_id 常为 NULL"的列表页。
     *
     * <p>与 {@link #apply} 唯一的差别：DEPT / DEPT_AND_CHILD 档在部门条件之外追加
     * {@code OR dept_id IS NULL}（dept_id 为 NULL 的行＝公司级下达行，对本公司人员可见），
     * 且在公司归属可用时同步收紧 {@code company_id = 登录人公司}——不放开跨公司。
     * COMPANY / SELF / TENANT 档与 {@link #apply} 完全一致。</p>
     *
     * @param qw 查询构造器（会被原地修改）
     * @param companyCol 公司列（如 BgItem::getCompanyId）；无则传 null
     * @param deptCol 部门列（如 BgItem::getDeptId）；无则传 null
     * @param ownerCol 本人列；无则传 null
     * @param <T> 实体类型
     */
    public static <T> void applyWithNullDept(LambdaQueryWrapper<T> qw,
                                             SFunction<T, ?> companyCol,
                                             SFunction<T, ?> deptCol,
                                             SFunction<T, ?> ownerCol) {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return; // 非 @DataScope 方法：不干预
        }
        int scope = s.getScope();
        if (scope <= DataScope.TENANT.getCode()) {
            return; // 本租户全部：不加条件
        }
        if (scope == DataScope.COMPANY.getCode()) {
            if (companyCol != null && s.getCompanyId() != null) {
                qw.eq(companyCol, s.getCompanyId());
            }
            return;
        }
        if (scope == DataScope.DEPT_AND_CHILD.getCode() || scope == DataScope.DEPT.getCode()) {
            Set<Long> child = s.getDeptAndChild();
            boolean hasDept = deptCol != null && s.getDeptId() != null;
            boolean hasCompany = companyCol != null && s.getCompanyId() != null;
            if (hasDept && scope == DataScope.DEPT_AND_CHILD.getCode() && child != null && !child.isEmpty()) {
                final Set<Long> in = child;
                if (hasCompany) {
                    qw.eq(companyCol, s.getCompanyId());
                    qw.and(w -> w.in(deptCol, in).or().isNull(deptCol)); // 本部门及以下 ∪ 公司级(NULL)行
                } else {
                    qw.in(deptCol, in); // 无公司信息：保持原规则，不放开 NULL 行
                }
            } else if (hasDept) {
                final Long deptId = s.getDeptId();
                if (hasCompany) {
                    qw.eq(companyCol, s.getCompanyId());
                    qw.and(w -> w.eq(deptCol, deptId).or().isNull(deptCol)); // 本部门 ∪ 公司级(NULL)行
                } else {
                    qw.eq(deptCol, deptId); // 无公司信息：保持原规则，不放开 NULL 行
                }
            } else if (hasCompany) {
                qw.eq(companyCol, s.getCompanyId()); // 无部门信息降级本公司
            }
            return;
        }
        if (scope == DataScope.SELF.getCode()) {
            if (ownerCol != null && s.getUserId() != null) {
                qw.eq(ownerCol, s.getUserId());
            } else if (companyCol != null && s.getCompanyId() != null) {
                qw.eq(companyCol, s.getCompanyId()); // 无本人列降级本公司
            }
        }
    }

    /**
     * 返回当前数据范围收窄后的公司 ID（scope=1 本租户全部时返回 null）。
     *
     * <p>供"新增写"方法使用：非本租户全部档时返回值非空，调用方可将其作为新记录的公司归属
     * 记录未带公司时回填 并校验（记录已带公司时须等于该值）。</p>
     */
    public static Long scopedCompanyId() {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return null; // 非 @DataScope 方法：不干预
        }
        if (s.getScope() <= DataScope.TENANT.getCode()) {
            return null; // 本租户全部：不收窄
        }
        return s.getCompanyId();
    }

    /**
     * 单条记录公司归属校验（WS-6 税域办结）。用于 {@code selectById} 类"按主键读单条后写"的方法——
     * 这类方法不走 QueryWrapper，无法用 {@link #apply} 办结，改由本方法显式校验归属。
     *
     * <p>规则与 {@link #apply} 一致：非本租户全部档（scope>1）且登录人有公司归属时，
     * 记录 {@code company_id} 必须等于登录人公司，否则抛权限异常；无部门/本人列时降级公司级，
     * 因此本方法对任意档位一律按公司级判断。登录人无公司归属时不拦（与 apply 降级一致）。</p>
     *
     * @param companyId 被访问记录的公司 ID
     */
    public static void requireCompanyInScope(Long companyId) {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return; // 非 @DataScope 方法：不干预
        }
        if (s.getScope() <= DataScope.TENANT.getCode()) {
            return; // 本租户全部：不限制
        }
        Long mine = s.getCompanyId();
        if (mine == null) {
            return; // 登录人无公司归属：不拦（与 apply 的降级规则一致）
        }
        if (companyId == null || !mine.equals(companyId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    "越权访问：记录不属于本公司（记录公司=" + companyId + "）");
        }
    }
}
