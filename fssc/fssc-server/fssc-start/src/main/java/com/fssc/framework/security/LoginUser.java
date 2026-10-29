// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.enums.SubjectType;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户（认证主体）。由 JwtAuthFilter 从【已验签】的 Token 还原，
 * 全程不依赖任何请求头注入的租户信息。
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主体类型：PLATFORM / TENANT */
    private SubjectType subjectType;

    /** 平台端为 null；租户端有值 */
    private Long tenantId;

    /** 用户ID（sys_user.id / pf_user.id） */
    private Long userId;

    private String username;

    private String realName;

    /** 菜单+按钮权限码集合（如 exp:claim:submit） */
    private Set<String> perms;

    /** 套餐功能开关码集合（如 F_EXPENSE_CLAIM），与 perms 取交集得最终权限 */
    private Set<String> features;

    /** 数据权限范围 1-5（仅租户用户） */
    private Integer dataScope;

    /** 当前纳税主体（集团型租户切换公司用，X-Company-Id 经后端校验归属当前租户） */
    private Long companyId;

    /** 当前部门（data_scope 部门/本部门及以下两级过滤用，新增） */
    private Long deptId;

    /** 登录时间戳（毫秒） */
    private Long loginAt;

    public boolean isPlatform() {
        return subjectType == SubjectType.PLATFORM;
    }

    public boolean isTenant() {
        return subjectType == SubjectType.TENANT;
    }

    /** 管理员：perms 含通配 *:*:*（平台超管 / 租户管理员） */
    public boolean isAdmin() {
        return perms != null && perms.contains("*:*:*");
    }

    /**
     * 单码权限判定：通配 {@code *:*:*} 视同持有全部码。
     * 规则与 {@code PermissionAspect.aroundPerm} 完全一致，业务侧需要"代码里再判一次"时走这里，
     * 不要再手写 contains 分支（手写会漏通配，把超管判成无权）。
     */
    public boolean hasPerm(String code) {
        return isAdmin() || (perms != null && code != null && perms.contains(code));
    }

    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    /*
     * 兼容别名（与 SysUser/前端历史契约对齐，避免前端 store.user.id / userName / nickName 读取为 undefined）。
     * id 与 userId、userName 与 username、nickName 与 realName 共享同一底层值，序列化时两套字段名同时输出。
     */

    public Long getId() { return userId; }
    public void setId(Long id) { this.userId = id; }

    public String getUserName() { return username; }
    public void setUserName(String userName) { this.username = userName; }

    public String getNickName() { return realName; }
    public void setNickName(String nickName) { this.realName = nickName; }

    public Set<String> getPerms() { return perms; }
    public void setPerms(Set<String> perms) { this.perms = perms; }

    public Set<String> getFeatures() { return features; }
    public void setFeatures(Set<String> features) { this.features = features; }

    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public Long getLoginAt() { return loginAt; }
    public void setLoginAt(Long loginAt) { this.loginAt = loginAt; }
}
