// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.controller;

import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.report.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 报表 Excel 导出（/api/tenant/report/export/**）。 */
@RestController
@RequestMapping("/api/tenant/report/export")
@RequiredArgsConstructor
public class ReportExportController {

    private final ExportService exportService;

    @RequiresPerm("report:export")
    @GetMapping("/bills")
    public void exportBills(@RequestParam(required = false) String billType,
                            @RequestParam(required = false) String status,
                            HttpServletResponse response) throws IOException {
        write(response, exportService.exportBills(billType, status), "报销单据.xlsx");
    }

    @RequiresPerm("report:export")
    @GetMapping("/dept-ranking")
    public void exportDeptRanking(HttpServletResponse response) throws IOException {
        write(response, exportService.exportDeptRanking(), "部门报销排行.xlsx");
    }

    @RequiresPerm("report:export")
    @GetMapping("/budget-execution")
    public void exportBudgetExecution(HttpServletResponse response) throws IOException {
        write(response, exportService.exportBudgetExecution(), "预算执行率.xlsx");
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
