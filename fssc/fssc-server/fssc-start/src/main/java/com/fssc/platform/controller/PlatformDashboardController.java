package com.fssc.platform.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.service.PlatformHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 平台健康看板（/api/platform/dashboard）。只读跨租户运营聚合，超管专属。
 */
@RestController
@RequestMapping("/api/platform/dashboard")
@RequiredArgsConstructor
@PlatformOnly
public class PlatformDashboardController {

    private final PlatformHealthService healthService;

    @RequiresPerm("platform:dashboard:view")
    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.success(healthService.dashboard());
    }
}
