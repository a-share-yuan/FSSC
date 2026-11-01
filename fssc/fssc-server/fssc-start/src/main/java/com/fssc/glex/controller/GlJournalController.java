// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.common.core.ErrorCode;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlJournalEntry;
import com.fssc.glex.service.GlJournalService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** GL 一般总账记账（/api/tenant/glex/journal/**）：费用预提/待摊/手工凭证/其他收款。 */
@RestController
@RequestMapping("/api/tenant/glex/journal")
@RequiredArgsConstructor
public class GlJournalController {

    private final GlJournalService service;

    @RequiresPerm("gl:journal:list")
    @GetMapping("/page")
    public Result<PageResult<GlJournalEntry>> page(PageQuery query, String period, String entryType, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, entryType, keyword));
    }

    @RequiresPerm("gl:journal:generate")
    @OperLog(module = "GL_JOURNAL", operType = "INSERT", desc = "一般总账记账并发起审批")
    @PostMapping("/generate")
    public Result<GlJournalEntry> generate(@RequestParam String period,
                                           @RequestParam String entryType,
                                           @RequestParam(required = false) String summary,
                                           @RequestParam(required = false) BigDecimal amount,
                                           @RequestParam(required = false) Long debitSubjectId,
                                           @RequestParam(required = false) Long creditSubjectId) {
        if (amount == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额不能为空");
        }
        return Result.success(service.generate(period, entryType, summary, amount, debitSubjectId, creditSubjectId));
    }

    /**
     * 记账域冲销出口（红字反向凭证 / 草稿凭证作废）。
     * 权限暂复用已种子的 {@code gl:journal:generate}——冲销与制单同属记账岗职责，
     * 独立权限码 gl:journal:reverse 需 sys_menu 种子扩码并由权限治理口径授予（报告列为待确认项）。
     */
    @RequiresPerm("gl:journal:generate")
    @OperLog(module = "GL_JOURNAL", operType = "UPDATE", desc = "一般总账记账冲销")
    @PostMapping("/{id}/reverse")
    public Result<Map<String, Object>> reverse(@PathVariable Long id,
                                                         @RequestParam(required = false) String reason) {
        return Result.success(service.reverse(id, reason));
    }
}