// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.UserContext;
import com.fssc.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录菜单树（/api/tenant/system/menu/tree）。
 * 任何已登录租户用户可调（动态路由基础）；菜单内容已按 角色权限 ∩ 套餐功能 裁剪，
 * 无需额外权限 —— 越权内容根本不会出现在树里。
 */
@RestController
@RequestMapping("/api/tenant/system/menu")
@RequiredArgsConstructor
public class TenantMenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    public Result<List<MenuService.MenuNode>> tree() {
        return Result.success(menuService.myTree(UserContext.getRequired()));
    }
}
