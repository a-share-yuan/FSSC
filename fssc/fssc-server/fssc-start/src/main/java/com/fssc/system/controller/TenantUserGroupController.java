// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysUserGroup;
import com.fssc.system.service.SoDService;
import com.fssc.system.service.UserGroupService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户组管理（/api/tenant/system/group/**）—— 权限治理 v2。
 * 权限码 system:group:*（管理员经 *:*:* 直通；非管理员需菜单/授权下发后可用）。
 * 用户级 SoD 存量巡检另见 /sod/scan-perm（system:sod:scan）。
 */
@RestController
@RequiredArgsConstructor
public class TenantUserGroupController {

    private final UserGroupService groupService;
    private final SoDService soDService;

    @RequiresPerm("system:group:list")
    @GetMapping("/api/tenant/system/group/list")
    public Result<List<SysUserGroup>> list() {
        return Result.success(groupService.list());
    }

    @RequiresPerm("system:group:list")
    @GetMapping("/api/tenant/system/group/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(Map.of(
                "group", groupService.list().stream()
                        .filter(g -> g.getId().equals(id)).findFirst().orElse(null),
                "roleIds", groupService.roleIdsOf(id),
                "memberIds", groupService.memberIds(id)));
    }

    @RequiresPerm("system:group:create")
    @OperLog(module = "SYSTEM_GROUP", operType = "INSERT", desc = "新增用户组")
    @PostMapping("/api/tenant/system/group/create")
    public Result<Long> create(@RequestBody GroupReq req) {
        return Result.success(groupService.create(req.getGroupCode(), req.getGroupName(), req.getRemark()));
    }

    @RequiresPerm("system:group:grant")
    @OperLog(module = "SYSTEM_GROUP", operType = "GRANT", desc = "用户组分配角色（含用户级SoD校验）")
    @PostMapping("/api/tenant/system/group/{id}/assignRoles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        groupService.assignRoles(id, roleIds);
        return Result.success();
    }

    @RequiresPerm("system:group:grant")
    @OperLog(module = "SYSTEM_GROUP", operType = "GRANT", desc = "用户组分配成员（含用户级SoD校验）")
    @PostMapping("/api/tenant/system/group/{id}/assignMembers")
    public Result<Void> assignMembers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        groupService.assignMembers(id, userIds);
        return Result.success();
    }

    @RequiresPerm("system:group:delete")
    @OperLog(module = "SYSTEM_GROUP", operType = "DELETE", desc = "删除用户组")
    @PostMapping("/api/tenant/system/group/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return Result.success();
    }

    /** 用户级 SoD 存量巡检（权限码并集维度），供定期复核页面/脚本消费 */
    @RequiresPerm("system:sod:scan")
    @GetMapping("/api/tenant/system/sod/scan-perm")
    public Result<List<SoDService.SodViolation>> scanPermViolations() {
        return Result.success(soDService.scanPermViolations());
    }

    @Data
    public static class GroupReq {
        private String groupCode;
        private String groupName;
        private String remark;
    }
}
