// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.service.ExpenseItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 主数据 ExpenseItem（/api/tenant/masterdata/expense-item/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/expense-item")
@RequiredArgsConstructor
public class ExpenseItemController {

    private final ExpenseItemService service;

    // 只读查询放开给所有登录租户用户：单据发起页（报销/借款等）自动生成明细行必用，普通员工角色无 md:* 权限会被提交校验误拦
    @GetMapping("/page")
    public Result<PageResult<MdExpenseItem>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    // 同上：借款/报销表单按名称匹配费用项目生成明细行
    @GetMapping("/tree")
    public Result<List<MdExpenseItem>> tree() {
        // 缓存分层：费用类型树缓存下沉到 ExpenseItemService.tree()（TenantCache），变更在 Service 写入口失效
        return Result.success(service.tree());
    }
    @RequiresPerm("md:expenseitem:create")
    @OperLog(module = "EXPENSE_ITEM", operType = "INSERT", desc = "新增ExpenseItem")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdExpenseItem e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:expenseitem:update")
    @OperLog(module = "EXPENSE_ITEM", operType = "UPDATE", desc = "更新ExpenseItem")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdExpenseItem e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:expenseitem:update")
    @OperLog(module = "EXPENSE_ITEM", operType = "UPDATE", desc = "ExpenseItem启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:item:delete")
    @OperLog(module = "EXPENSE_ITEM", operType = "DELETE", desc = "删除费用类型")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
