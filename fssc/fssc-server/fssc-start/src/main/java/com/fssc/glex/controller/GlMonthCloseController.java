// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlMonthClose;
import com.fssc.glex.service.GlMonthCloseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GL 月结（/api/tenant/glex/month-close/**）。 */
@RestController
@RequestMapping("/api/tenant/glex/month-close")
@RequiredArgsConstructor
public class GlMonthCloseController {

    private final GlMonthCloseService service;

    @RequiresPerm("gl:monthclose:list")
    @GetMapping("/page")
    public Result<PageResult<GlMonthClose>> page(PageQuery query, String period, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, keyword));
    }

    @RequiresPerm("gl:monthclose:close")
    @OperLog(module = "GL_MONTH_CLOSE", operType = "INSERT", desc = "月结发起审批")
    @PostMapping("/close")
    public Result<GlMonthClose> close(@RequestParam String period,
                                      @RequestParam(required = false) Long companyId) {
        return Result.success(service.close(period, companyId));
    }
}