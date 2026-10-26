// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.service.RcReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 收款核销管理（/api/tenant/receipt/**）：收款单登记与按先进先出核销应收余额 */
@RestController
@RequestMapping("/api/tenant/receipt")
@RequiredArgsConstructor
public class RcReceiptController {

    private final RcReceiptService service;

    @RequiresPerm("receivable:list")
    @GetMapping("/list")
    public Result<List<RcReceipt>> list() {
        return Result.success(service.list());
    }

    @RequiresPerm("receivable:list")
    @GetMapping("/{id}/lines")
    public Result<List<RcReceiptLine>> lines(@PathVariable Long id) {
        return Result.success(service.lines(id));
    }

    @RequiresPerm("receivable:create")
    @OperLog(module = "RECEIPT", operType = "INSERT", desc = "新增收款单")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody RcReceipt e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("receivable:update")
    @OperLog(module = "RECEIPT", operType = "UPDATE", desc = "先进先出核销应收余额")
    @PostMapping("/{id}/apply")
    public Result<Void> apply(@PathVariable Long id) {
        service.applyReceipt(id);
        return Result.success();
    }
}