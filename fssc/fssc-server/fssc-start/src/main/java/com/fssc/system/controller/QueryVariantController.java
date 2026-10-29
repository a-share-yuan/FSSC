// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.Result;
import com.fssc.system.entity.SysQueryVariant;
import com.fssc.system.service.QueryVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查询方案（Variant）控制器。
 * <p>保存个人常用筛选组合。</p>
 */
@RestController
@RequestMapping("/api/tenant/system/query-variant")
@RequiredArgsConstructor
public class QueryVariantController {

    private final QueryVariantService queryVariantService;

    /** 查询当前用户的方案列表（按页面 key 过滤） */
    @GetMapping("/list")
    public Result<List<SysQueryVariant>> list(@RequestParam String pageKey) {
        return Result.success(queryVariantService.listByPage(pageKey));
    }

    /** 获取当前用户指定页面的默认方案 */
    @GetMapping("/default")
    public Result<SysQueryVariant> getDefault(@RequestParam String pageKey) {
        return Result.success(queryVariantService.getDefault(pageKey));
    }

    /** 新增方案 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody SysQueryVariant variant) {
        return Result.success(queryVariantService.create(variant));
    }

    /** 更新方案 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysQueryVariant variant) {
        queryVariantService.update(variant);
        return Result.success();
    }

    /** 删除方案 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        queryVariantService.delete(id);
        return Result.success();
    }

    /** 设为默认方案 */
    @PostMapping("/setDefault")
    public Result<Void> setDefault(@RequestParam Long id) {
        queryVariantService.setDefault(id);
        return Result.success();
    }
}