// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.report.entity.RptSummary;
import com.fssc.report.service.RptSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 报表预聚合（P2-3）。 */
@RestController
@RequestMapping("/api/tenant/report/summary")
@RequiredArgsConstructor
public class RptSummaryController {

    private final RptSummaryService summaryService;

    @RequiresPerm("report:summary:list")
    @GetMapping("/query")
    public Result<List<RptSummary>> query(@RequestParam(required = false) String period,
                                          @RequestParam(required = false) String dimType) {
        return Result.success(summaryService.query(period, dimType));
    }

    @RequiresPerm("report:summary:list")
    @GetMapping("/periods")
    public Result<List<String>> periods() {
        return Result.success(summaryService.periods());
    }

    @RequiresPerm("report:summary:list")
    @PostMapping("/refresh")
    public Result<Integer> refresh(@RequestParam Integer year, @RequestParam Integer month) {
        return Result.success(summaryService.refresh(year, month));
    }
}
