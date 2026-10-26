// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gl.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.gl.entity.GlEntry;
import com.fssc.gl.service.GlService;
import com.fssc.framework.log.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * P2：总账模块接口。查询 POSTED 凭证落账总账分录、科目余额与试算平衡。
 * 路径遵循租户域约定 /api/tenant/**（前端 axios baseURL=/api + 鉴权按 /api/tenant 判定租户）。
 */
@RestController
@RequestMapping("/api/tenant/op/gl")
@RequiredArgsConstructor
public class GlController {

    private final GlService glService;

    @RequiresPerm("gl:entry:list")
    @GetMapping("/entry")
    public Result<PageResult<GlEntry>> entry(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "20") long pageSize,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month,
                                             @RequestParam(required = false) Long subjectId,
                                             @RequestParam(required = false) String orderBy,
                                             @RequestParam(required = false) String order) {
        // R5-D-149：orderBy/order 支撑列头服务端排序（白名单列见 GlService#page，非法值回退默认序）
        return Result.success(glService.page(pageNum, pageSize, companyId, year, month, subjectId, orderBy, order));
    }

    @RequiresPerm("gl:balance:list")
    @GetMapping("/balance")
    public Result<List<Map<String, Object>>> balance(@RequestParam Long companyId,
                                                     @RequestParam Integer year,
                                                     @RequestParam Integer month) {
        return Result.success(glService.balance(companyId, year, month));
    }

    @RequiresPerm("gl:trial:list")
    @GetMapping("/trial")
    public Result<Map<String, Object>> trial(@RequestParam Long companyId,
                                             @RequestParam Integer year,
                                             @RequestParam Integer month,
                                             @RequestParam(required = false) Boolean includeUnposted) {
        return Result.success(glService.trialBalance(companyId, year, month, Boolean.TRUE.equals(includeUnposted)));
    }

    @RequiresPerm("gl:entry:list")
    @GetMapping("/posted")
    public Result<Boolean> posted(@RequestParam Long voucherId) {
        return Result.success(glService.isPostedToGl(voucherId));
    }

    @OperLog(module = "GL总账", operType = "UPDATE", desc = "手动重新落账指定凭证到总账")
    @RequiresPerm("gl:entry:post")
    @PostMapping("/repost")
    public Result<Integer> repost(@RequestParam Long voucherId) {
        return Result.success(glService.postVoucherToGl(voucherId));
    }
}