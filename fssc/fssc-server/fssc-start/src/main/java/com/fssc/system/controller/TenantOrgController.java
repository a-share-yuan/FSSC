// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysPost;
import com.fssc.system.service.CompanyService;
import com.fssc.system.service.DeptService;
import com.fssc.system.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户组织架构（/api/tenant/system/org/**）：
 * company 公司（纳税主体）/ dept 部门（树）/ post 岗位。
 */
@RestController
@RequestMapping("/api/tenant/system/org")
@RequiredArgsConstructor
public class TenantOrgController {

    private final CompanyService companyService;
    private final DeptService deptService;
    private final PostService postService;

    // ---------- 公司 ----------

    // 只读查询放开给所有登录租户用户：单据发起页公司信息必用（写操作仍需权限）。
    // 但"放开读"≠"跨公司可见"：出参按登录人数据范围裁剪（普通员工只看本公司），
    // 缓存仍存整租户全量以保持命中率，见 CompanyService#visibleForUser。
    @GetMapping("/company/tree")
    public Result<List<SysCompany>> companyTree(
            @RequestParam(required = false) Long companyId) {
        // P1-3 缓存分层：公司树按租户缓存 5 分钟，变更主动失效
        String key = "companyTree:" + com.fssc.framework.tenant.TenantContext.getTenantId();
        List<SysCompany> all = com.fssc.framework.cache.CacheUtil.get(key, () -> companyService.tree());
        return Result.success(companyService.visibleForUser(all, companyId));
    }

    @RequiresPerm("system:company:create")
    @OperLog(module = "ORG", operType = "INSERT", desc = "新增公司")
    @PostMapping("/company/create")
    public Result<Long> companyCreate(@RequestBody SysCompany company) {
        evictCompanyCache();
        return Result.success(companyService.create(company));
    }

    @RequiresPerm("system:company:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "更新公司")
    @PostMapping("/company/update")
    public Result<Void> companyUpdate(@RequestBody SysCompany company) {
        evictCompanyCache();
        companyService.update(company);
        return Result.success();
    }

    @RequiresPerm("system:company:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "公司启用/停用")
    @PostMapping("/company/{id}/enable")
    public Result<Void> companyEnable(@PathVariable Long id, @RequestParam boolean enable) {
        evictCompanyCache();
        companyService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:company:delete")
    @OperLog(module = "ORG", operType = "DELETE", desc = "删除公司")
    @PostMapping("/company/{id}/delete")
    public Result<Void> companyDelete(@PathVariable Long id) {
        evictCompanyCache();
        companyService.delete(id);
        return Result.success();
    }

    /** 公司变更 → 失效当前租户公司树缓存 */
    private void evictCompanyCache() {
        com.fssc.framework.cache.CacheUtil.evictPrefix("companyTree:" + com.fssc.framework.tenant.TenantContext.getTenantId());
    }

    // ---------- 部门 ----------

    /** 部门树（只读，任何已登录用户可读）：供单据表单的 公司→部门 下拉选择；不套 system:dept:list 管理权限 */
    @GetMapping("/dept/tree")
    public Result<List<SysDept>> deptTree(@RequestParam(required = false) Long companyId) {
        if (companyId != null) {
            return Result.success(deptService.treeByCompany(companyId));
        }
        return Result.success(deptService.tree());
    }

    @RequiresPerm("system:dept:create")
    @OperLog(module = "ORG", operType = "INSERT", desc = "新增部门")
    @PostMapping("/dept/create")
    public Result<Long> deptCreate(@RequestBody SysDept dept) {
        return Result.success(deptService.create(dept));
    }

    @RequiresPerm("system:dept:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "更新部门")
    @PostMapping("/dept/update")
    public Result<Void> deptUpdate(@RequestBody SysDept dept) {
        deptService.update(dept);
        return Result.success();
    }

    @RequiresPerm("system:dept:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "部门启用/停用")
    @PostMapping("/dept/{id}/enable")
    public Result<Void> deptEnable(@PathVariable Long id, @RequestParam boolean enable) {
        deptService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:dept:delete")
    @OperLog(module = "ORG", operType = "DELETE", desc = "删除部门")
    @PostMapping("/dept/{id}/delete")
    public Result<Void> deptDelete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }

    // ---------- 岗位 ----------

    @RequiresPerm("system:post:list")
    @GetMapping("/post/list")
    public Result<List<SysPost>> postList(@RequestParam(required = false) Long companyId,
                                          @RequestParam(required = false) Long deptId) {
        if (deptId != null) {
            return Result.success(postService.listByDept(deptId));
        }
        if (companyId != null) {
            return Result.success(postService.listByCompany(companyId));
        }
        return Result.success(postService.list());
    }

    /** P0-2：岗位分页查询（前端 Org.vue 岗位列表分页用，替代全量 postList） */
    @RequiresPerm("system:post:list")
    @GetMapping("/post/page")
    public Result<PageResult<SysPost>> postPage(PageQuery query,
                                                @RequestParam(required = false) Long companyId,
                                                @RequestParam(required = false) Long deptId) {
        return Result.success(postService.page(query.getPageNum(), query.getPageSize(), companyId, deptId));
    }

    @RequiresPerm("system:post:create")
    @OperLog(module = "ORG", operType = "INSERT", desc = "新增岗位")
    @PostMapping("/post/create")
    public Result<Long> postCreate(@RequestBody SysPost post) {
        return Result.success(postService.create(post));
    }

    @RequiresPerm("system:post:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "更新岗位")
    @PostMapping("/post/update")
    public Result<Void> postUpdate(@RequestBody SysPost post) {
        postService.update(post);
        return Result.success();
    }

    @RequiresPerm("system:post:update")
    @OperLog(module = "ORG", operType = "UPDATE", desc = "岗位启用/停用")
    @PostMapping("/post/{id}/enable")
    public Result<Void> postEnable(@PathVariable Long id, @RequestParam boolean enable) {
        postService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:post:delete")
    @OperLog(module = "ORG", operType = "DELETE", desc = "删除岗位")
    @PostMapping("/post/{id}/delete")
    public Result<Void> postDelete(@PathVariable Long id) {
        postService.delete(id);
        return Result.success();
    }
}
