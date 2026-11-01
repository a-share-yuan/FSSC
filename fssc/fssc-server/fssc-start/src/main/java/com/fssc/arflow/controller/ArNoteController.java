// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.controller;

import com.fssc.arflow.entity.ArNote;
import com.fssc.arflow.service.ArNoteService;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 应收票据 OpenAPI（/api/tenant/ar-flow/notes/**，蓝图票据管理 9.5）：
 * 登记（电子/纸质+影像扫描）→ 审单入账 → 移交出纳（纸质）→ 背书转让/汇票贴现 →
 * 到期提示 → 托收到账；票据查询/到期未托收清单。
 */
@RestController
@RequestMapping("/api/tenant/ar-flow/notes")
@RequiredArgsConstructor
public class ArNoteController {

    private final ArNoteService noteService;

    @RequiresPerm("arflow:note:list")
    @GetMapping
    public Result<List<ArNote>> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String noteType,
                                     @RequestParam(required = false) String keyword) {
        return Result.success(noteService.list(status, noteType, keyword));
    }

    /** 到期未托收（票据到期提示工作项） */
    @RequiresPerm("arflow:note:list")
    @GetMapping("/due-list")
    public Result<List<ArNote>> dueList() {
        return Result.success(noteService.dueList());
    }

    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "票据登记")
    @RequiresPerm("arflow:note:register")
    @PostMapping
    public Result<ArNote> register(@RequestBody ArNote note) {
        return Result.success(noteService.register(note));
    }

    /** 审单入账（生成 借1121/贷1122 凭证） */
    @RequiresPerm("arflow:note:register")
    @PostMapping("/{id}/review")
    public Result<ArNote> review(@PathVariable Long id) {
        return Result.success(noteService.review(id));
    }

    /** 移交出纳保管（纸质票据）：body {keeper} */
    @RequiresPerm("arflow:note:cashier")
    @PostMapping("/{id}/handover")
    public Result<ArNote> handover(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(noteService.handover(id, body == null ? null : (String) body.get("keeper")));
    }

    /** 背书转让给银行：body {transferee} */
    @RequiresPerm("arflow:note:cashier")
    @PostMapping("/{id}/endorse")
    public Result<ArNote> endorse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(noteService.endorse(id, (String) body.get("transferee")));
    }

    /** 汇票贴现：body {annualRatePct, discountDate}，贴现款与利息单独记账 */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "汇票贴现")
    @RequiresPerm("arflow:note:cashier")
    @PostMapping("/{id}/discount")
    public Result<ArNote> discount(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal rate = body.get("annualRatePct") == null ? null
                : new BigDecimal(String.valueOf(body.get("annualRatePct")));
        LocalDate date = body.get("discountDate") == null ? null
                : LocalDate.parse(String.valueOf(body.get("discountDate")).substring(0, 10));
        return Result.success(noteService.discount(id, rate, date));
    }

    /** 到期提示（进入待托收） */
    @RequiresPerm("arflow:note:cashier")
    @PostMapping("/{id}/mark-due")
    public Result<ArNote> markDue(@PathVariable Long id) {
        return Result.success(noteService.markDue(id));
    }

    /** 托收到账（生成 借1002/贷1121 凭证） */
    @RequiresPerm("arflow:note:cashier")
    @PostMapping("/{id}/collect")
    public Result<ArNote> collect(@PathVariable Long id) {
        return Result.success(noteService.collect(id));
    }

    /** 作废（仅待审单）：body {reason} */
    @RequiresPerm("arflow:note:register")
    @PostMapping("/{id}/void")
    public Result<ArNote> voidNote(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(noteService.voidNote(id, body == null ? null : (String) body.get("reason")));
    }
}
