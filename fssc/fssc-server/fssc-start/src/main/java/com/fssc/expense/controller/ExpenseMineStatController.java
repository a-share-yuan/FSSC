// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.controller;

import com.fssc.common.core.Result;
import com.fssc.expense.service.ExpenseMineStatService;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端（H5）「我的」页统计（/api/tenant/expense/mine/**）：只读聚合，单文件、单接口，不改既有单据口。
 *
 * <p>口径与人范围与 {@code ExpenseBillService#myPage} 一致（报销人 ∪ 代理人、del_flag=0、同租户），
 * 数字全部由 SQL 聚合得出，不受前端分页条数截断；登录人只从 {@code UserContext} 取，
 * 本接口<b>不接任何入参</b>，因此不存在"前端传 userId 查别人余额"的越权面。
 * 权限码与 myPage 同码 {@code exp:bill:list}——能看自己单据列表的人就能看自己的汇总数，
 * 不新增授权项、不需要为移动端另发权限。
 */
@RestController
@RequestMapping("/api/tenant/expense/mine")
@RequiredArgsConstructor
public class ExpenseMineStatController {

    private final ExpenseMineStatService expenseMineStatService;

    /**
     * 「我的」页四个数：借款余额 loanBalance／借款笔数 loanCount／在途单数 inTransitCount／被打回单数 rejectedCount。
     * 无参数、无分页、无写操作。
     */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/stat")
    public Result<ExpenseMineStatService.MineStat> stat() {
        return Result.success(expenseMineStatService.stat());
    }
}
