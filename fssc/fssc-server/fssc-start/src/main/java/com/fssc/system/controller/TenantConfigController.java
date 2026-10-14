// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 租户参数配置（/api/tenant/system/config/**） */
@RestController
@RequestMapping("/api/tenant/system/config")
@RequiredArgsConstructor
public class TenantConfigController {

    private final ConfigService configService;
    private final com.fssc.image.channel.OcrConfigService ocrConfigService;

    @RequiresPerm("system:config:list")
    @GetMapping("/page")
    public Result<PageResult<SysConfig>> page(PageQuery query, String keyword) {
        return Result.success(configService.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("system:config:create")
    @OperLog(module = "CONFIG", operType = "INSERT", desc = "新增参数")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody SysConfig config) {
        return Result.success(configService.create(config));
    }

    @RequiresPerm("system:config:update")
    @OperLog(module = "CONFIG", operType = "UPDATE", desc = "更新参数")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysConfig config) {
        configService.update(config);
        return Result.success();
    }

    // ---------------- OCR 通道配置（管理员可视化，改后即时生效） ----------------

    @RequiresPerm("system:config:list")
    @GetMapping("/ocr")
    public Result<java.util.Map<String, Object>> getOcr() {
        return Result.success(ocrConfigService.get());
    }

    @RequiresPerm("system:config:update")
    @OperLog(module = "CONFIG", operType = "UPDATE", desc = "保存OCR通道配置")
    @PostMapping("/ocr")
    public Result<Void> saveOcr(@RequestBody java.util.Map<String, String> req) {
        ocrConfigService.save(req);
        return Result.success();
    }
}
