// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.service.GlTaxProvisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GL 税金计提（/api/tenant/glex/tax-provision/**） */
@RestController
@RequestMapping("/api/tenant/glex/tax-provision")
@RequiredArgsConstructor
public class GlTaxProvisionController {

    private final GlTaxProvisionService service;

    @RequiresPerm("gl:taxprov:list")
    @GetMapping("/page")
    public Result<PageResult<GlTaxProvision>> page(PageQuery query, String period, String taxType, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, taxType, keyword));
    }

    @RequiresPerm("gl:taxprov:provision")
    @OperLog(module = "GL_TAX_PROVISION", operType = "INSERT", desc = "税金计提")
    @PostMapping("/provision")
    public Result<List<GlTaxProvision>> provision(@RequestParam String period) {
        return Result.success(service.provision(period));
    }

    // D-DEC-440（X-42 甲）：原 POST /pay（发起 GL_TAX_PAY 缴款审批）已作废删除——独立缴款流不出付款动作、
    // 不出缴纳凭证。税金缴款走对公付款通道（付款单／付款批次），本控制器只保留计提环节。
    // 历史 GL_TAX_PAY 实例仍可由下方挂接核查 /link-check 读出。

    /**
     * 计提挂接核查（D-DEC-135）：列出该期间每条计提记录是否挂了审批流程/是否出了凭证/是否挂了缴款流程，
     * 并给出未挂面条数与金额。整改前"一次计提 3 条只挂第 1 条"是静默的，本接口使其可见、可查。
     */
    @RequiresPerm("gl:taxprov:list")
    @GetMapping("/link-check")
    public Result<java.util.Map<String, Object>> linkCheck(@RequestParam String period) {
        return Result.success(service.linkCheck(period));
    }
}