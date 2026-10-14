// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdCurrency;
import com.fssc.masterdata.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 Currency（/api/tenant/masterdata/currency/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService service;

    @RequiresPerm("md:currency:list")
    @GetMapping("/page")
    public Result<PageResult<MdCurrency>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:currency:create")
    @OperLog(module = "CURRENCY", operType = "INSERT", desc = "新增Currency")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdCurrency e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:currency:update")
    @OperLog(module = "CURRENCY", operType = "UPDATE", desc = "更新Currency")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdCurrency e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:currency:update")
    @OperLog(module = "CURRENCY", operType = "UPDATE", desc = "Currency启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:currency:delete")
    @OperLog(module = "CURRENCY", operType = "DELETE", desc = "删除币种")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
