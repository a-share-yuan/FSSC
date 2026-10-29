// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.IntegrationCallLog;
import com.fssc.system.gateway.IntegrationGateway;
import com.fssc.system.gateway.GatewayResponse;
import com.fssc.system.mapper.IntegrationCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration Gateway controller (/api/tenant/system/integration/**).
 *
 * <p>Provides:
 * <ul>
 *   <li>Call log viewing (paginated, filterable by category/success)</li>
 *   <li>Call statistics (success rate, avg duration by category)</li>
 *   <li>Test connection endpoint (delegates to IntegrationGateway)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/system/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationCallLogMapper callLogMapper;
    private final IntegrationGateway integrationGateway;

    /** Paginated call logs (filter by category, success/fail) */
    @RequiresPerm("system:extiface:list")
    @GetMapping("/logs/page")
    public Result<PageResult<IntegrationCallLog>> logsPage(
            PageQuery q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer success) {
        LambdaQueryWrapper<IntegrationCallLog> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            qw.eq(IntegrationCallLog::getCategory, category);
        }
        if (success != null) {
            qw.eq(IntegrationCallLog::getSuccess, success);
        }
        qw.orderByDesc(IntegrationCallLog::getCreateTime);
        Page<IntegrationCallLog> p = callLogMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), qw);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    /** Call statistics: total calls, success count, fail count, avg duration, breakdown by category */
    @RequiresPerm("system:extiface:list")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        // Total counts
        Long total = callLogMapper.selectCount(null);
        Long successCount = callLogMapper.selectCount(
                new LambdaQueryWrapper<IntegrationCallLog>().eq(IntegrationCallLog::getSuccess, 1));
        Long failCount = callLogMapper.selectCount(
                new LambdaQueryWrapper<IntegrationCallLog>().eq(IntegrationCallLog::getSuccess, 0));
        result.put("total", total);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("successRate", total != null && total > 0
                ? String.format("%.1f%%", successCount * 100.0 / total) : "—");
        // Recent 24h stats
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Long recentTotal = callLogMapper.selectCount(
                new LambdaQueryWrapper<IntegrationCallLog>().ge(IntegrationCallLog::getCreateTime, since));
        result.put("recent24h", recentTotal);
        // 按接口类别汇总成功/失败数（前端据此红牌「全失败类别」告警）
        QueryWrapper<IntegrationCallLog> cq = new QueryWrapper<>();
        cq.select("category", "count(*) as total",
                "sum(case when success = 0 then 1 else 0 end) as fail")
                .groupBy("category");
        List<Map<String, Object>> byCategory = callLogMapper.selectMaps(cq);
        result.put("byCategory", byCategory);
        return Result.success(result);
    }

    /** Test connection for a specific ext_interface_config record (via IntegrationGateway) */
    @RequiresPerm("system:extiface:test")
    @OperLog(module = "EXT_INTERFACE", operType = "OTHER", desc = "测试外部接口连接(网关)")
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        GatewayResponse resp = integrationGateway.testConnection(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("success", resp.isSuccess());
        result.put("statusCode", resp.getStatusCode());
        result.put("durationMs", resp.getDurationMs());
        result.put("message", resp.isOk()
                ? "HTTP " + resp.getStatusCode() + " (" + resp.getDurationMs() + "ms)"
                : resp.getError() != null ? resp.getError() : "HTTP " + resp.getStatusCode());
        // Include truncated response body for admin inspection
        String body = resp.getBody();
        if (body != null && !body.isBlank()) {
            result.put("responseBody", body.length() > 500 ? body.substring(0, 500) : body);
        }
        return Result.success(result);
    }
}
