// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.service.TenantLifecycleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 平台租户管理（/api/platform/tenant/**）。
 * 仅平台管理员可访问（JwtAuthFilter 两级路由 + @PlatformOnly 双保险），
 * 操作全部落 pf_oper_log 审计。
 */
@RestController
@RequestMapping("/api/platform/tenant")
@RequiredArgsConstructor
@PlatformOnly
public class PlatformTenantController {

    private final TenantLifecycleService lifecycleService;

    @RequiresPerm("platform:tenant:list")
    @GetMapping("/page")
    public Result<PageResult<PfTenant>> page(PageQuery query, String keyword, String status) {
        return Result.success(lifecycleService.page(query.getPageNum(), query.getPageSize(), keyword, status));
    }

    @RequiresPerm("platform:tenant:create")
    @OperLog(module = "TENANT", operType = "INSERT", desc = "新增租户")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody PfTenant tenant) {
        return Result.success(lifecycleService.create(tenant));
    }

    @RequiresPerm("platform:tenant:approve")
    @OperLog(module = "TENANT", operType = "UPDATE", desc = "审核通过租户")
    @PostMapping("/approve/{id}")
    public Result<Void> approve(@PathVariable Long id) {
        lifecycleService.approve(id);
        return Result.success();
    }

    @RequiresPerm("platform:tenant:reject")
    @OperLog(module = "TENANT", operType = "UPDATE", desc = "拒绝租户入驻")
    @PostMapping("/reject/{id}")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        lifecycleService.reject(id, body == null ? null : body.get("reason"));
        return Result.success();
    }

    @RequiresPerm("platform:tenant:freeze")
    @OperLog(module = "TENANT", operType = "UPDATE", desc = "冻结租户")
    @PostMapping("/freeze/{id}")
    public Result<Void> freeze(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        lifecycleService.freeze(id, body == null ? null : body.get("reason"));
        return Result.success();
    }

    @RequiresPerm("platform:tenant:unfreeze")
    @OperLog(module = "TENANT", operType = "UPDATE", desc = "解冻租户")
    @PostMapping("/unfreeze/{id}")
    public Result<Void> unfreeze(@PathVariable Long id) {
        lifecycleService.unfreeze(id);
        return Result.success();
    }

    @RequiresPerm("platform:tenant:cancel")
    @OperLog(module = "TENANT", operType = "DELETE", desc = "注销租户")
    @PostMapping("/cancel/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        lifecycleService.cancel(id);
        return Result.success();
    }

    @RequiresPerm("platform:tenant:list")
    @GetMapping("/{id}")
    public Result<PfTenant> detail(@PathVariable Long id) {
        return Result.success(lifecycleService.detail(id));
    }
}
