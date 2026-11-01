// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.VoucherTemplate;
import com.fssc.sharing.service.VoucherTemplateService;
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
 * 凭证模板管理 REST API。
 */
@RestController
@RequestMapping("/api/tenant/voucher-template")
@RequiredArgsConstructor
public class VoucherTemplateController {

    private final VoucherTemplateService voucherTemplateService;

    @GetMapping("/page")
    public Result<PageResult<VoucherTemplate>> page(PageQuery q,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String sourceType,
                                                     @RequestParam(required = false) String status) {
        return Result.success(voucherTemplateService.page(q, keyword, sourceType, status));
    }

    @GetMapping("/{id}")
    public Result<VoucherTemplate> getById(@PathVariable Long id) {
        return Result.success(voucherTemplateService.getById(id));
    }

    @GetMapping("/by-source-type")
    public Result<List<VoucherTemplate>> listBySourceType(@RequestParam String sourceType) {
        return Result.success(voucherTemplateService.listBySourceType(sourceType));
    }

    @RequiresPerm("sharing:config")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody VoucherTemplate tpl) {
        return Result.success(voucherTemplateService.create(tpl));
    }

    @RequiresPerm("sharing:config")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody VoucherTemplate tpl) {
        voucherTemplateService.update(tpl);
        return Result.success();
    }

    @RequiresPerm("sharing:config")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        voucherTemplateService.delete(id);
        return Result.success();
    }
}