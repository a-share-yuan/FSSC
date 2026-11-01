// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdProject;
import com.fssc.masterdata.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 Project（/api/tenant/masterdata/project/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService service;

    // 只读查询放开给所有登录租户用户：单据发起页表单必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<MdProject>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:project:create")
    @OperLog(module = "PROJECT", operType = "INSERT", desc = "新增Project")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdProject e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:project:update")
    @OperLog(module = "PROJECT", operType = "UPDATE", desc = "更新Project")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdProject e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:project:update")
    @OperLog(module = "PROJECT", operType = "UPDATE", desc = "Project启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:project:delete")
    @OperLog(module = "PROJECT", operType = "DELETE", desc = "删除项目")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
