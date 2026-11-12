// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysNotifyTemplate;
import com.fssc.system.service.SysNotifyTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息模板维护（/api/tenant/system/notify-template/**）。
 *
 * <p>权限沿用系统参数域既有的 {@code system:config:list} / {@code system:config:update}：
 * 消息模板与全局参数是同一类「系统级配置」、同一批运维角色维护，单开一套权限点只会
 * 出现「菜单可见但没授权」的老问题（预检看板当年就踩过），故不新增权限码。
 *
 * <p>不提供删除接口：触发点是代码契约，关掉用「停用」，保留配置随时可开回来。
 */
@RestController
@RequestMapping("/api/tenant/system/notify-template")
@RequiredArgsConstructor
public class SysNotifyTemplateController {

    private final SysNotifyTemplateService templateService;

    @RequiresPerm("system:config:list")
    @GetMapping("/page")
    public Result<PageResult<SysNotifyTemplate>> page(PageQuery query, String keyword) {
        return Result.success(templateService.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("system:config:update")
    @OperLog(module = "SYSTEM_NOTIFY", operType = "INSERT", desc = "新增消息模板")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody SysNotifyTemplate template) {
        return Result.success(templateService.create(template));
    }

    @RequiresPerm("system:config:update")
    @OperLog(module = "SYSTEM_NOTIFY", operType = "UPDATE", desc = "更新消息模板")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysNotifyTemplate template) {
        templateService.update(template);
        return Result.success();
    }

    @RequiresPerm("system:config:update")
    @OperLog(module = "SYSTEM_NOTIFY", operType = "UPDATE", desc = "消息模板启停")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        templateService.enable(id, enable);
        return Result.success();
    }
}
