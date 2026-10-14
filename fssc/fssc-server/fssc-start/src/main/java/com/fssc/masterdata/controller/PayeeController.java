// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdPayee;
import com.fssc.masterdata.service.PayeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 Payee（/api/tenant/masterdata/payee/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/payee")
@RequiredArgsConstructor
public class PayeeController {

    private final PayeeService service;

    // 只读查询放开给所有登录租户用户：单据发起页表单必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<MdPayee>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:payee:create")
    @OperLog(module = "PAYEE", operType = "INSERT", desc = "新增Payee")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdPayee e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:payee:update")
    @OperLog(module = "PAYEE", operType = "UPDATE", desc = "更新Payee")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdPayee e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:payee:update")
    @OperLog(module = "PAYEE", operType = "UPDATE", desc = "Payee启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:payee:delete")
    @OperLog(module = "PAYEE", operType = "DELETE", desc = "删除收款方")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
