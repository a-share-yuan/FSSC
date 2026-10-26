// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.match.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.match.entity.MatchTolerance;
import com.fssc.match.service.MatchToleranceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 三单匹配容差参数端点（公司级）。
 * <ul>
 *  <li>GET  /api/tenant/match-tolerance?companyId=1 —— 查询容差参数（companyId 缺省取当前登录用户所属公司；
 *      无配置时返回默认参数 amountPct=0.5 / amountAbs=100）；</li>
 *  <li>POST /api/tenant/match-tolerance，body = 参数 JSON
 *      {"companyId":1,"amountPct":0.5,"amountAbs":100,"status":"NORMAL"} —— 按租户+公司 upsert。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/match-tolerance")
@RequiredArgsConstructor
public class MatchToleranceController {

    private final MatchToleranceService matchToleranceService;

    @RequiresPerm("pay:match:list")
    @GetMapping
    public Result<MatchTolerance> get(@RequestParam(required = false) Long companyId) {
        return Result.success(matchToleranceService.getTolerance(companyId));
    }

    // D-N32（人工裁决）：容差阈值是"三单匹配"这道付款前合规校验的可调参数 ⇒ 保存动作与查询动作
    // 分权：读仍是 pay:match:list，写改为 pay:match:config（写规则＝配置面，与 §6 红线 6 同界）。
    // 注意：上线前需保证 pay:match:config 已绑定到原本持 pay:match:list 的角色，否则真人会失去保存能力。
    @RequiresPerm("pay:match:config")
    @OperLog(module = "PTP", operType = "UPDATE", desc = "保存三单匹配容差参数")
    @PostMapping
    public Result<MatchTolerance> save(@RequestBody MatchTolerance body) {
        return Result.success(matchToleranceService.upsert(body));
    }
}
