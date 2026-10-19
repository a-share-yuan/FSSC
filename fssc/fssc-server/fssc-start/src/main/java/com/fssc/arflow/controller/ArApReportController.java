package com.fssc.arflow.controller;

import com.fssc.arflow.service.ArApReportService;
import com.fssc.common.core.Result;
import lombok.RequiredArgsConstructor;
import com.fssc.framework.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AR/AP 统计报表 OpenAPI（/api/tenant/ar-flow/reports/**，蓝图报表统计 9.1 序号21）：
 * 开票/核销/票据/坏账/月结 5 张统计报表。
 */
@RestController
@RequestMapping("/api/tenant/ar-flow/reports")
@RequiredArgsConstructor
public class ArApReportController {

    private final ArApReportService reportService;

    @RequiresPerm("arflow:report:view")
    @GetMapping("/invoicing")
    public Result<List<Map<String, Object>>> invoicing(@RequestParam(required = false) String customerName) {
        return Result.success(reportService.invoicing(customerName));
    }

    @RequiresPerm("arflow:report:view")
    @GetMapping("/writeoff")
    public Result<List<Map<String, Object>>> writeoff(@RequestParam(required = false) String customerName) {
        return Result.success(reportService.writeoff(customerName));
    }

    @RequiresPerm("arflow:report:view")
    @GetMapping("/note")
    public Result<List<Map<String, Object>>> note() {
        return Result.success(reportService.note());
    }

    @RequiresPerm("arflow:report:view")
    @GetMapping("/bad-debt")
    public Result<List<Map<String, Object>>> badDebt(@RequestParam(required = false) String period) {
        return Result.success(reportService.badDebt(period));
    }

    @RequiresPerm("arflow:report:view")
    @GetMapping("/month-close")
    public Result<List<Map<String, Object>>> monthClose(@RequestParam(required = false) String period) {
        return Result.success(reportService.monthClose(period));
    }
}
