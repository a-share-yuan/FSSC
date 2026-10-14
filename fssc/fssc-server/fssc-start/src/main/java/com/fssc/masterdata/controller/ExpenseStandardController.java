// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdExpenseStandard;
import com.fssc.masterdata.service.ExpenseStandardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 ExpenseStandard（/api/tenant/masterdata/expense-standard/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/expense-standard")
@RequiredArgsConstructor
public class ExpenseStandardController {

    private final ExpenseStandardService service;

    // 只读查询放开给所有登录租户用户：出差申请按差旅标准自动计算、报销价税分离带系统价必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<MdExpenseStandard>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:expensestd:create")
    @OperLog(module = "EXPENSE_STANDARD", operType = "INSERT", desc = "新增ExpenseStandard")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdExpenseStandard e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:expensestd:update")
    @OperLog(module = "EXPENSE_STANDARD", operType = "UPDATE", desc = "更新ExpenseStandard")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdExpenseStandard e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:std:delete")
    @OperLog(module = "EXPENSE_STANDARD", operType = "DELETE", desc = "删除报销标准")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
