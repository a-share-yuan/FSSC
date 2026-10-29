// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.PermDependency;
import com.fssc.framework.security.ReportDomain;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysMenu;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysRoleMenu;
import com.fssc.system.mapper.SysMenuMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 租户角色管理：CRUD + 数据范围 + 菜单授权 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    public PageResult<SysRole> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysRole::getRoleName, keyword)
                    .or().like(SysRole::getRoleCode, keyword));
        }
        qw.orderByDesc(SysRole::getId);
        Page<SysRole> p = sysRoleMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(SysRole role) {
        role.setId(null);
        if (role.getDataScope() == null) {
            role.setDataScope(5);
        }
        sysRoleMapper.insert(role);
        return role.getId();
    }

    public void update(SysRole role) {
        if (sysRoleMapper.selectById(role.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在: " + role.getId());
        }
        sysRoleMapper.updateById(role);
    }

    public void enable(Long id, boolean enable) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setStatus(enable ? "NORMAL" : "DISABLED");
        sysRoleMapper.updateById(r);
    }

    /** 分配菜单：删旧插新（sys_role_menu 带 tenant_id，天然租户内隔离）。
     * 报表权限卡点：/reports/* 报表菜单只能授予「未来权限集仍命中对应业务域判据码」的角色。
     *  A4 第②层：权限项依赖在**保存期**拦，不只运行时兜底。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        assertReportDomainProjection(menuIds);
        assertPermDependencyClosure(menuIds);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            Long tenantId = TenantContext.getTenantId();
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setTenantId(tenantId); // 显式设置，不依赖 MetaObjectHandler
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
    }

    /**
     * 报表域投影卡点：assignMenus 是全量替换，角色未来权限集 = 本次 menuIds 的 perms 并集。
     * 规则（报表权限 = 业务域权限的投影，"报表随模块走"）：
     *  - 菜单 path 以 /reports/ 开头视为报表菜单，按其 perms 中的域码 report:&lt;domain&gt;:view 解析归属域；
     *  - 未来权限集须命中该域任一判据码，或持有审计阅示码 report:audit:all；
     *  - 违反则整单拒绝（列出全部违规项，避免改一次报一次）。
     */
    private void assertReportDomainProjection(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<SysMenu> menus = sysMenuMapper.selectBatchIds(menuIds);
        if (menus.isEmpty()) {
            return;
        }
        Set<String> futurePerms = permsOf(menus);
        if (futurePerms.contains(ReportDomain.AUDIT_ALL)) {
            return; // 审计阅示白名单：全域放行（配合季度复核）
        }
        List<String> violations = new ArrayList<>();
        for (SysMenu m : menus) {
            if (m.getPath() == null || !m.getPath().startsWith("/reports/")) {
                continue;
            }
            ReportDomain domain = domainOf(m);
            if (domain == null || domain.hit(futurePerms)) {
                continue;
            }
            violations.add(String.format("「%s」属 %s 域报表，需任一判据码：%s",
                    m.getMenuName(), domain.getLabel(), String.join("/", domain.getJudgeCodes())));
        }
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    "报表权限必须跟随业务域权限（无该域业务权限不能授该域报表）：" + String.join("；", violations));
        }
    }

    /**
     * 权限项依赖的**保存期**校验，不只运行时兜底。
     * assignMenus 是全量替换 ⇒ 本次 menuIds 的 perms 并集就是该角色未来的权限集，
     * 依赖不闭合（如授了 approve 却没授 list）整单拒绝，与"查看是其余权限的前置权限"同构。
     * 真值在 {@code PermDependency}，运行时兜底走同一份，不两处各写一套。
     */
    private void assertPermDependencyClosure(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        PermDependency
                .assertGrantClosure(permsOf(sysMenuMapper.selectBatchIds(menuIds)));
    }

    /** 菜单集 → 权限码全集（perms 允许逗号分隔多码） */
    private Set<String> permsOf(List<SysMenu> menus) {
        Set<String> perms = new HashSet<>();
        for (SysMenu m : menus) {
            if (StringUtils.hasText(m.getPerms())) {
                for (String p : m.getPerms().split(",")) {
                    if (StringUtils.hasText(p)) {
                        perms.add(p.trim());
                    }
                }
            }
        }
        return perms;
    }

    /** 从菜单 perms 解析报表域码 report:&lt;domain&gt;:view（report:view 基础码除外）；无域码返回 null */
    private ReportDomain domainOf(SysMenu m) {
        if (!StringUtils.hasText(m.getPerms())) {
            return null;
        }
        for (String p : m.getPerms().split(",")) {
            String t = p.trim();
            if (t.startsWith("report:") && t.endsWith(":view") && !"report:view".equals(t)) {
                String key = t.substring("report:".length(), t.length() - ":view".length());
                try {
                    return ReportDomain.of(key);
                } catch (IllegalArgumentException ignore) {
                    // 非法域码按未标注处理
                }
            }
        }
        return null;
    }

    public List<Long> menuIds(Long roleId) {
        return sysRoleMenuMapper.selectMenuIdsByRole(roleId);
    }

    /** 删除角色（逻辑删；同时清理角色-菜单关联，关联表物理删） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        sysRoleMapper.hardDelete(roleId);
    }
}
