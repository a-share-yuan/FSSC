// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdTaxRate;
import com.fssc.masterdata.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 TaxRate（/api/tenant/masterdata/tax-rate/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/tax-rate")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateService service;

    @RequiresPerm("md:taxrate:list")
    @GetMapping("/page")
    public Result<PageResult<MdTaxRate>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:taxrate:create")
    @OperLog(module = "TAX_RATE", operType = "INSERT", desc = "新增TaxRate")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdTaxRate e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:taxrate:update")
    @OperLog(module = "TAX_RATE", operType = "UPDATE", desc = "更新TaxRate")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdTaxRate e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:taxrate:update")
    @OperLog(module = "TAX_RATE", operType = "UPDATE", desc = "TaxRate启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:tax:delete")
    @OperLog(module = "TAX_RATE", operType = "DELETE", desc = "删除税码税率")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
