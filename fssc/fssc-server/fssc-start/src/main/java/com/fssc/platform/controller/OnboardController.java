// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.platform.service.OnboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首次登录选档引导。
 *
 * <p>路径在 /api/tenant/** 下，必须携带有效的企业 Token；权限与租户隔离在
 * OnboardService 内二次校验（仅企业超管可执行 apply），不依赖菜单权限码 ——
 * 引导页只在企业还没选择使用方式时出现，此时不应要求先有菜单授权。</p>
 */
@RestController
@RequestMapping("/api/tenant/onboard")
@RequiredArgsConstructor
public class OnboardController {

    private final OnboardService onboardService;

    /** 引导页状态与可选档位清单 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(onboardService.status());
    }

    /** 执行装配；幂等，重复调用用于补齐缺项或切换档位 */
    @OperLog(module = "TENANT", operType = "UPDATE", desc = "选择装配档并完成初始化")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Object preset = body == null ? null : body.get("preset");
        return Result.success(onboardService.apply(preset == null ? null : String.valueOf(preset)));
    }
}
