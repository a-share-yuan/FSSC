// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.compliance.service.ComplianceExportService;
import com.fssc.framework.security.annotation.RequiresPerm;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 合规模块 Excel 导出（/api/tenant/compliance/export/**）。 */
@RestController
@RequestMapping("/api/tenant/compliance/export")
@RequiredArgsConstructor
public class ComplianceExportController {

    private final ComplianceExportService complianceExportService;

    @RequiresPerm("compliance:export")
    @GetMapping("/precheck-rules")
    public void exportPrecheckRules(HttpServletResponse response) throws IOException {
        write(response, complianceExportService.exportPrecheckRules(), "预检规则.xlsx");
    }

    @RequiresPerm("compliance:export")
    @GetMapping("/travel-standards")
    public void exportTravelStandards(HttpServletResponse response) throws IOException {
        write(response, complianceExportService.exportTravelStandards(), "差旅标准.xlsx");
    }

    @RequiresPerm("compliance:export")
    @GetMapping("/expense-policies")
    public void exportExpensePolicies(HttpServletResponse response) throws IOException {
        write(response, complianceExportService.exportExpensePolicies(), "费用政策.xlsx");
    }

    @RequiresPerm("compliance:export")
    @GetMapping("/archives")
    public void exportArchives(HttpServletResponse response) throws IOException {
        write(response, complianceExportService.exportArchives(), "档案列表.xlsx");
    }

    @RequiresPerm("compliance:export")
    @GetMapping("/fraud-logs")
    public void exportFraudLogs(HttpServletResponse response) throws IOException {
        write(response, complianceExportService.exportFraudLogs(), "欺诈检测记录.xlsx");
    }

    private void write(HttpServletResponse response, byte[] data, String fileName) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String name = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + name);
        response.setContentLength(data.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
        }
    }
}