// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ServiceCatalog;
import com.fssc.compliance.entity.SatisfactionSurvey;
import com.fssc.compliance.service.ServiceCatalogService;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 服务目录与 SLA 管理（/api/tenant/compliance/service-catalog/**、/sla/**、/satisfaction/**）。 */
@RestController
@RequestMapping("/api/tenant/compliance")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    // 服务目录 CRUD

    @RequiresPerm("compliance:catalog:list")
    @GetMapping("/service-catalog")
    public Result<PageResult<ServiceCatalog>> pageCatalog(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return Result.success(serviceCatalogService.pageCatalog(pageNum, pageSize, category, status));
    }

    @RequiresPerm("compliance:catalog:list")
    @GetMapping("/service-catalog/{id}")
    public Result<ServiceCatalog> getCatalog(@PathVariable Long id) {
        return Result.success(serviceCatalogService.getCatalog(id));
    }

    @RequiresPerm("compliance:catalog:save")
    @PostMapping("/service-catalog")
    public Result<Void> createCatalog(@RequestBody ServiceCatalog c) {
        serviceCatalogService.createCatalog(c);
        return Result.success();
    }

    @RequiresPerm("compliance:catalog:save")
    @PutMapping("/service-catalog")
    public Result<Void> updateCatalog(@RequestBody ServiceCatalog c) {
        serviceCatalogService.updateCatalog(c);
        return Result.success();
    }

    @RequiresPerm("compliance:catalog:delete")
    @DeleteMapping("/service-catalog/{id}")
    public Result<Void> deleteCatalog(@PathVariable Long id) {
        serviceCatalogService.deleteCatalog(id);
        return Result.success();
    }

    @RequiresPerm("compliance:catalog:save")
    @PostMapping("/service-catalog/{id}/status")
    public Result<Void> enableCatalog(@PathVariable Long id, @RequestParam Integer status) {
        serviceCatalogService.enableCatalog(id, status);
        return Result.success();
    }

    // SLA 监控

    @RequiresPerm("compliance:sla:list")
    @GetMapping("/sla/monitor")
    public Result<List<Map<String, Object>>> slaMonitor() {
        return Result.success(serviceCatalogService.slaMonitor());
    }

    @RequiresPerm("compliance:sla:list")
    @GetMapping("/sla/stats")
    public Result<Map<String, Object>> slaStats() {
        return Result.success(serviceCatalogService.slaStats());
    }

    // 满意度调查

    @RequiresPerm("compliance:satisfaction:list")
    @GetMapping("/satisfaction")
    public Result<PageResult<SatisfactionSurvey>> pageSatisfaction(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String serviceCode) {
        return Result.success(serviceCatalogService.pageSatisfaction(pageNum, pageSize, serviceCode));
    }

    @RequiresPerm("compliance:satisfaction:save")
    @PostMapping("/satisfaction")
    public Result<Void> submitSatisfaction(@RequestBody SatisfactionSurvey s) {
        serviceCatalogService.submitSatisfaction(s);
        return Result.success();
    }

    @RequiresPerm("compliance:satisfaction:list")
    @GetMapping("/satisfaction/stats")
    public Result<Map<String, Object>> satisfactionStats() {
        return Result.success(serviceCatalogService.satisfactionStats());
    }
}
