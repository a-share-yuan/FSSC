// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlFxRevaluation;
import com.fssc.glex.service.FxRevaluationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 月末外币汇兑重估（/api/tenant/glex/fx-reval/**）：CAS 19 货币性项目期末重估。 */
@RestController
@RequestMapping("/api/tenant/glex/fx-reval")
@RequiredArgsConstructor
public class FxRevalController {

    private final FxRevaluationService service;

    @RequiresPerm("glex:fxreval:list")
    @GetMapping("/page")
    public Result<PageResult<GlFxRevaluation>> page(PageQuery query, String period, String itemType) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, itemType));
    }

    @RequiresPerm("glex:fxreval:run")
    @OperLog(module = "GL_FX_REVAL", operType = "INSERT", desc = "月末汇兑重估")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam String period) {
        return Result.success(service.revalMonth(period));
    }

    /** 重估试算（只算不落库）：返回各桶期末汇率与预计差异，确认后正式执行 */
    @RequiresPerm("glex:fxreval:list")
    @GetMapping("/trial")
    public Result<Map<String, Object>> trial(@RequestParam String period) {
        return Result.success(service.trial(period));
    }

    /** 反冲：对已生成凭证的重估记录生成借贷对调的反向凭证（期间已结账拦截） */
    @RequiresPerm("glex:fxreval:run")
    @OperLog(module = "GL_FX_REVAL", operType = "UPDATE", desc = "汇兑重估反冲")
    @PostMapping("/{id}/reverse")
    public Result<Map<String, Object>> reverse(@PathVariable Long id) {
        return Result.success(service.reverse(id));
    }
}
