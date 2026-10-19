package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysRole;
import com.fssc.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 租户角色管理（/api/tenant/system/role/**）：角色 CRUD + 数据范围 + 菜单授权 */
@RestController
@RequestMapping("/api/tenant/system/role")
@RequiredArgsConstructor
public class TenantRoleController {

    private final RoleService roleService;

    @RequiresPerm("system:role:list")
    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(PageQuery query, String keyword) {
        return Result.success(roleService.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("system:role:create")
    @OperLog(module = "SYSTEM_ROLE", operType = "INSERT", desc = "新增角色")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody SysRole role) {
        return Result.success(roleService.create(role));
    }

    @RequiresPerm("system:role:update")
    @OperLog(module = "SYSTEM_ROLE", operType = "UPDATE", desc = "更新角色")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysRole role) {
        roleService.update(role);
        return Result.success();
    }

    @RequiresPerm("system:role:update")
    @OperLog(module = "SYSTEM_ROLE", operType = "UPDATE", desc = "角色启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        roleService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:role:assignMenu")
    @OperLog(module = "SYSTEM_ROLE", operType = "GRANT", desc = "分配菜单权限")
    @PostMapping("/{id}/assignMenus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return Result.success();
    }

    @RequiresPerm("system:role:list")
    @GetMapping("/{id}/menuIds")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.success(roleService.menuIds(id));
    }

    @RequiresPerm("system:role:delete")
    @OperLog(module = "SYSTEM_ROLE", operType = "DELETE", desc = "删除角色")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }
}
