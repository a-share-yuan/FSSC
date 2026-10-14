// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlSharedMaster;
import com.fssc.glex.service.GlSharedMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GL 主数据维护（/api/tenant/glex/shared-master/**）。 */
@RestController
@RequestMapping("/api/tenant/glex/shared-master")
@RequiredArgsConstructor
public class GlSharedMasterController {

    private final GlSharedMasterService service;

    @RequiresPerm("gl:sharedmaster:list")
    @GetMapping("/page")
    public Result<PageResult<GlSharedMaster>> page(PageQuery query, String period, String type, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, type, keyword));
    }

    @RequiresPerm("gl:sharedmaster:submit")
    @OperLog(module = "GL_SHARED_MASTER", operType = "INSERT", desc = "GL主数据维护发起审批")
    @PostMapping("/submit")
    public Result<GlSharedMaster> submit(@RequestParam String period,
                                         @RequestParam String type,
                                         @RequestParam(defaultValue = "ADD") String action,
                                         @RequestParam(required = false) String subjectCode,
                                         @RequestParam(required = false) String subjectName,
                                         @RequestParam(required = false) String description) {
        return Result.success(service.submit(period, type, action, subjectCode, subjectName, description));
    }
}