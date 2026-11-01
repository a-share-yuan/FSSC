// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.controller;

import com.fssc.common.core.Result;
import com.fssc.report.entity.RptReportDef;
import com.fssc.report.service.ReportCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报表目录控制器：预置报表注册表 + 用户收藏管理。
 * 报表中心与报表导航。
 */
@RestController
@RequestMapping("/api/tenant/report-catalog")
@RequiredArgsConstructor
public class ReportCatalogController {

    private final ReportCatalogService reportCatalogService;

    /** 按分类列出启用报表 */
    @GetMapping("/list")
    public Result<List<RptReportDef>> list(@RequestParam(required = false) String category) {
        return Result.success(reportCatalogService.listByCategory(category));
    }

    /** 按编码查报表定义 */
    @GetMapping("/{reportCode}")
    public Result<RptReportDef> getByCode(@PathVariable String reportCode) {
        return Result.success(reportCatalogService.getByCode(reportCode));
    }

    /** 我的收藏报表 */
    @GetMapping("/my-favorites")
    public Result<List<RptReportDef>> myFavorites() {
        return Result.success(reportCatalogService.myFavorites());
    }

    /** 收藏报表 */
    @PostMapping("/favorite/{reportCode}")
    public Result<Void> addFavorite(@PathVariable String reportCode) {
        reportCatalogService.addFavorite(reportCode);
        return Result.success();
    }

    /** 取消收藏 */
    @DeleteMapping("/favorite/{reportCode}")
    public Result<Void> removeFavorite(@PathVariable String reportCode) {
        reportCatalogService.removeFavorite(reportCode);
        return Result.success();
    }
}
