package com.fssc.platform.controller;

import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.service.TenantExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 平台租户数据导出（/api/platform/tenant/{tenantId}/export）。
 * 数据主权/迁出：超管按租户打包核心业务表 ZIP（每表一个 CSV），操作落 pf_oper_log。
 * 与 PlatformTenantController 共用 /api/platform/tenant 前缀（子路径不冲突）。
 */
@RestController
@RequestMapping("/api/platform/tenant")
@RequiredArgsConstructor
@PlatformOnly
public class TenantExportController {

    private final TenantExportService tenantExportService;

    @RequiresPerm("platform:tenant:export")
    @OperLog(module = "TENANT", operType = "EXPORT", desc = "导出租户数据")
    @GetMapping("/{tenantId}/export")
    public void export(@PathVariable Long tenantId,
                       @RequestParam(defaultValue = "all") String scope,
                       HttpServletResponse response) throws IOException {
        byte[] zip = tenantExportService.export(tenantId);
        String name = "tenant-" + tenantId + "-export-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".zip";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"; filename*=UTF-8''" + encoded);
        response.setContentLength(zip.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(zip);
        }
    }
}
