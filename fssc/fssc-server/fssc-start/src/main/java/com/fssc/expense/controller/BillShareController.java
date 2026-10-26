// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBillShare;
import com.fssc.expense.service.BillShareService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 费用分摊（/api/tenant/expense/share/**）。 */
@RestController
@RequestMapping("/api/tenant/expense/share")
@RequiredArgsConstructor
public class BillShareController {

    private final BillShareService billShareService;

    @RequiresPerm("exp:share:list")
    @GetMapping("/page")
    public Result<PageResult<ExpBillShare>> page(PageQuery query, @RequestParam(required = false) Long billId) {
        return Result.success(billShareService.page(query.getPageNum(), query.getPageSize(), billId));
    }

    // 按单回读＝"读自己这张单的一个字段"，守卫须与单据母表 /expense/bill/detail/{id} 同一规则（exp:bill:list）。
    // 原先挂 exp:share:list（分摊维护岗权限）⇒ 员工看得懂这张单却读不到它的分摊行，
    // 编辑载入必吃一发 4030，而新建态不发这跳所以看着像"只有编辑坏"。跨单的分摊查询面（/page）仍留维护权限。
    @RequiresPerm("exp:bill:list")
    @GetMapping("/byBill")
    public Result<List<ExpBillShare>> byBill(@RequestParam Long billId) {
        return Result.success(billShareService.listByBill(billId));
    }

    @RequiresPerm("exp:share:create")
    @OperLog(module = "EXP_BILL_SHARE", operType = "INSERT", desc = "新增分摊")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExpBillShare share) {
        return Result.success(billShareService.create(share));
    }

    @RequiresPerm("exp:share:update")
    @OperLog(module = "EXP_BILL_SHARE", operType = "UPDATE", desc = "覆盖保存整单分摊")
    @PostMapping("/replace")
    public Result<Void> replace(@RequestParam Long billId, @RequestBody List<ExpBillShare> shares) {
        billShareService.replaceForBill(billId, shares);
        return Result.success();
    }

    @RequiresPerm("exp:share:delete")
    @OperLog(module = "EXP_BILL_SHARE", operType = "DELETE", desc = "删除分摊")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        billShareService.delete(id);
        return Result.success();
    }
}
