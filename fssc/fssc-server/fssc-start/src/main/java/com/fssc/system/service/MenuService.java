// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.system.entity.SysMenu;
import com.fssc.system.mapper.SysMenuMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单树服务。登录后返回当前用户的菜单树：
 * <pre>
 *   最终可见菜单 = 角色关联菜单（sys_role_menu，租户内） ∩ 套餐功能（feature_code 过滤）
 * </pre>
 * 租户管理员（is_admin=1 / *:*:*）不过滤角色，只看套餐功能。
 * 菜单定义在全局表 sys_menu（无 tenant_id），授权关系在租户表。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper sysMenuMapper;

    public List<MenuNode> myTree(LoginUser user) {
        List<SysMenu> menus;
        if (user.isAdmin() || user.getPerms().contains("*:*:*")) {
            // 管理员：全部启用菜单
            menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSort));
        } else {
            List<Long> ids = sysMenuMapper.selectMenuIdsByUser(user.getUserId());
            if (ids.isEmpty()) {
                return List.of();
            }
            // ★导航完整性：只补祖先目录；页面(C)祖先仅在被授权的是页面时补，避免只授按钮(F)的用户
            //   看到"点进去就 403"的幽灵菜单（仅用于成树，不追加 perms）
            List<SysMenu> all = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSort));
            Map<Long, SysMenu> allById = new LinkedHashMap<>();
            for (SysMenu m : all) {
                allById.put(m.getId(), m);
            }
            Set<Long> visible = new java.util.LinkedHashSet<>(ids);
            for (Long id : ids) {
                SysMenu granted = allById.get(id);
                if (granted == null) {
                    continue;
                }
                boolean pageGrant = "C".equals(granted.getMenuType())
                        && StringUtils.hasText(granted.getPath());
                SysMenu cur = granted;
                Set<Long> guard = new java.util.HashSet<>();
                while (cur.getParentId() != null && cur.getParentId() != 0
                        && guard.add(cur.getParentId())) {
                    cur = allById.get(cur.getParentId());
                    if (cur == null) {
                        break;
                    }
                    if ("M".equals(cur.getMenuType()) || pageGrant) {
                        visible.add(cur.getId());
                    }
                }
            }
            menus = all.stream().filter(m -> visible.contains(m.getId())).toList();
        }
        return buildTree(filterByFeature(menus, user.getFeatures()));
    }

    /** 套餐功能过滤：菜单声明了 feature_code 但套餐未开通 → 剔除（权限 ∩ 功能码） */
    private List<SysMenu> filterByFeature(List<SysMenu> menus, java.util.Set<String> features) {
        return menus.stream()
                .filter(m -> !StringUtils.hasText(m.getFeatureCode()) || features.contains(m.getFeatureCode()))
                .toList();
    }

    /** 构建树（按 parentId 分组，0 为根） */
    private List<MenuNode> buildTree(List<SysMenu> menus) {
        Map<Long, MenuNode> nodeMap = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            nodeMap.put(m.getId(), MenuNode.of(m));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode n : nodeMap.values()) {
            if (n.getParentId() == null || n.getParentId() == 0) {
                roots.add(n);
            } else {
                MenuNode parent = nodeMap.get(n.getParentId());
                if (parent != null) {
                    parent.getChildren().add(n);
                } else {
                    // 父菜单不在可见集合（如父被功能过滤）→ 提升为根
                    roots.add(n);
                }
            }
        }
        return roots;
    }

    @Data
    public static class MenuNode {
        private Long id;
        private Long parentId;
        private String menuName;
        private String menuType;
        private String path;
        private String component;
        private String perms;
        private String icon;
        private Integer sort;
        private List<MenuNode> children = new ArrayList<>();

        static MenuNode of(SysMenu m) {
            MenuNode n = new MenuNode();
            n.setId(m.getId());
            n.setParentId(m.getParentId());
            n.setMenuName(m.getMenuName());
            n.setMenuType(m.getMenuType());
            n.setPath(m.getPath());
            n.setComponent(m.getComponent());
            n.setPerms(m.getPerms());
            n.setIcon(m.getIcon());
            n.setSort(m.getSort());
            return n;
        }
    }
}
