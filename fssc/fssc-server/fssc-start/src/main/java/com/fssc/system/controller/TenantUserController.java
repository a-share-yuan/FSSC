// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysUser;
import com.fssc.system.service.SoDService;
import com.fssc.system.service.UserService;
import com.fssc.system.service.UserService.BatchImportResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 租户用户管理（/api/tenant/system/user/**） */
@RestController
@RequestMapping("/api/tenant/system/user")
@RequiredArgsConstructor
public class TenantUserController {

    private final UserService userService;
    private final SoDService soDService;

    // 只读查询放开给所有登录租户用户：报销/还款等单据发起页的申请人、还款人下拉必用（写操作仍需权限）
    // SOX-02：无 system:user:list 权限的调用方裁剪为下拉最小字段集，避免向全员暴露 phone/email
    // companyId 由前端传入，不可信——直接调 API 不传/乱传就能拉全租户人员。
    // 无 system:user:list 的调用方一律以登录人公司为准（跨公司请求直接拒绝，不静默改写）；
    // 只有持用户管理权限的角色可跨公司检索。
    @GetMapping("/page")
    public Result<PageResult<?>> page(PageQuery query, String keyword, String status,
                                      @RequestParam(required = false) String deptIds,
                                      @RequestParam(required = false) Long companyId) {
        // 机构目录树联动：deptIds 为逗号分隔的部门 ID（选中节点及其全部子部门）
        List<Long> deptIdList = null;
        if (deptIds != null && !deptIds.isBlank()) {
            deptIdList = new ArrayList<>();
            for (String s : deptIds.split(",")) {
                try {
                    long v = Long.parseLong(s.trim());
                    if (v > 0) deptIdList.add(v);
                } catch (NumberFormatException ignore) { /* 跳过非法片段 */ }
            }
        }
        boolean canListAll = hasUserListPerm();
        if (!canListAll) {
            LoginUser me = UserContext.getRequired();
            if (me.getCompanyId() != null) {
                if (companyId != null && !companyId.equals(me.getCompanyId())) {
                    throw new BizException(
                            ErrorCode.PERMISSION_DENIED,
                            "只能查看本公司人员（公司 " + me.getCompanyId() + "）");
                }
                companyId = me.getCompanyId();
            }
        }
        PageResult<SysUser> page = userService.page(query.getPageNum(), query.getPageSize(), keyword, status, deptIdList, companyId);
        if (canListAll) {
            return Result.success(page);
        }
        PageResult<Map<String, Object>> trimmed = new PageResult<>();
        trimmed.setPageNum(page.getPageNum());
        trimmed.setPageSize(page.getPageSize());
        trimmed.setTotal(page.getTotal());
        trimmed.setPages(page.getPages());
        trimmed.setList(page.getList().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("userName", u.getUserName());
            m.put("nickName", u.getNickName());
            m.put("empNo", u.getEmpNo());
            m.put("deptId", u.getDeptId());
            m.put("deptName", u.getDeptName());
            m.put("companyName", u.getCompanyName());
            m.put("status", u.getStatus());
            m.put("createTime", u.getCreateTime());
            return m;
        }).collect(Collectors.toList()));
        return Result.success(trimmed);
    }

    private boolean hasUserListPerm() {
        // 走 LoginUser#hasPerm 以覆盖通配 *:*:*（租户管理员只持有通配码，逐字比会把它判成无权，
        // 进而被强制收敛到本公司并裁剪字段——管理页会只剩一家公司的人）
        LoginUser u = UserContext.get();
        return u != null && u.hasPerm("system:user:list");
    }

    @RequiresPerm("system:user:create")
    @OperLog(module = "SYSTEM_USER", operType = "INSERT", desc = "新增用户")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody UserCreateReq req) {
        return Result.success(userService.create(req.getUser(), req.getRoleIds()));
    }

    @RequiresPerm("system:user:update")
    @OperLog(module = "SYSTEM_USER", operType = "UPDATE", desc = "更新用户")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysUser user) {
        userService.update(user);
        return Result.success();
    }

    @RequiresPerm("system:user:update")
    @OperLog(module = "SYSTEM_USER", operType = "UPDATE", desc = "用户启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        userService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:user:resetPwd")
    @OperLog(module = "SYSTEM_USER", operType = "UPDATE", desc = "重置密码")
    @PostMapping("/{id}/resetPwd")
    public Result<Void> resetPwd(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        userService.resetPassword(id, body == null ? null : body.get("newPassword"));
        return Result.success();
    }

    @RequiresPerm("system:user:assignRole")
    @OperLog(module = "SYSTEM_USER", operType = "GRANT", desc = "分配角色")
    @PostMapping("/{id}/assignRoles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return Result.success();
    }

    @RequiresPerm("system:user:list")
    @GetMapping("/{id}/roleIds")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.success(userService.roleIds(id));
    }

    /**
     * 授权查看用户明文联系方式。列表接口对 phone/email 已脱敏，
     * 编辑表单打开前调用本接口回填明文（需 system:user:update）。
     */
    @RequiresPerm("system:user:update")
    @GetMapping("/{id}/full")
    public Result<Map<String, Object>> fullContact(@PathVariable Long id) {
        return Result.success(userService.fullContact(id));
    }

    /** SoD 合规巡检：当前租户内所有持有不相容角色组合的用户（演示全能审批人命中属预期） */
    @RequiresPerm("system:user:list")
    @GetMapping("/sod-violations")
    public Result<List<SoDService.SodViolation>> sodViolations() {
        return Result.success(soDService.scanViolations());
    }

    @RequiresPerm("system:user:delete")
    @OperLog(module = "SYSTEM_USER", operType = "DELETE", desc = "删除用户")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @RequiresPerm("system:user:create")
    @OperLog(module = "SYSTEM_USER", operType = "INSERT", desc = "批量导入用户")
    @PostMapping(value = "/batchImport", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<BatchImportResult> batchImport(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.batchImport(file));
    }

    public static class UserCreateReq {
        private SysUser user;
        private List<Long> roleIds;

        public SysUser getUser() {
            return user;
        }

        public void setUser(SysUser user) {
            this.user = user;
        }

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
        }
    }
}
