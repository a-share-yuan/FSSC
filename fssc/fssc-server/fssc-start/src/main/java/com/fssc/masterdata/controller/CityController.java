// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdCity;
import com.fssc.masterdata.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 City（/api/tenant/masterdata/city/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/city")
@RequiredArgsConstructor
public class CityController {

    private final CityService service;

    // 只读查询放开给所有登录租户用户：单据发起页表单必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<MdCity>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:city:create")
    @OperLog(module = "CITY", operType = "INSERT", desc = "新增City")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdCity e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:city:update")
    @OperLog(module = "CITY", operType = "UPDATE", desc = "更新City")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdCity e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:city:update")
    @OperLog(module = "CITY", operType = "UPDATE", desc = "City启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:city:delete")
    @OperLog(module = "CITY", operType = "DELETE", desc = "删除城市等级")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
