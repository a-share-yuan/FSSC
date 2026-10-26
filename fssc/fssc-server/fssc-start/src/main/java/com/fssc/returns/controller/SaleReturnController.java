// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.returns.entity.SaleReturn;
import com.fssc.returns.service.SaleReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售退货单 OpenAPI（/api/tenant/sale-returns/**）。
 * 状态机：DRAFT→PENDING→APPROVED→VOIDED；REJECTED 可改后重新提交。
 */
@RestController
@RequestMapping("/api/tenant/sale-returns")
@RequiredArgsConstructor
public class SaleReturnController {

    private final SaleReturnService service;

    /**
     * 导出上限（D-N22）：本端点返回的是 JSON 列表而非文件流，原先传 {@code Integer.MAX_VALUE}
     * 等于"一次请求把整张 sale_return 读进内存再吐出"，既无行数上限也无数据范围。
     * 现在此封顶，数据范围由 {@code SaleReturnService.page} 上的 {@code @DataScope} 办结。
     * 超出即截断（取前 N 行），与分页端点同语义。
     */
    private static final long EXPORT_MAX_ROWS = 10000;

    @RequiresPerm("returns:sale:list")
    @GetMapping
    public Result<PageResult<SaleReturn>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "20") long pageSize,
                                                @RequestParam(required = false) Long companyId,
                                                @RequestParam(required = false) String customerCode,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String billDateStart,
                                                @RequestParam(required = false) String billDateEnd,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(service.page(pageNum, pageSize, companyId, customerCode, status, billDateStart, billDateEnd, keyword));
    }

    @RequiresPerm("returns:sale:list")
    @GetMapping("/{id}")
    public Result<SaleReturn> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @OperLog(module = "SALE_RETURN", operType = "ADD", desc = "新建销售退货单")
    @RequiresPerm("returns:sale:create")
    @PostMapping
    public Result<SaleReturn> create(@RequestBody SaleReturn r) {
        return Result.success(service.create(r));
    }

    @OperLog(module = "SALE_RETURN", operType = "UPDATE", desc = "修改销售退货单")
    @RequiresPerm("returns:sale:create")
    @PutMapping("/{id}")
    public Result<SaleReturn> update(@PathVariable Long id, @RequestBody SaleReturn r) {
        r.setId(id);
        return Result.success(service.update(r));
    }

    @RequiresPerm("returns:sale:submit")
    @PostMapping("/{id}/submit")
    public Result<SaleReturn> submit(@PathVariable Long id) {
        return Result.success(service.submit(id));
    }

    @RequiresPerm("returns:sale:approve")
    @PostMapping("/{id}/approve")
    public Result<SaleReturn> approve(@PathVariable Long id) {
        return Result.success(service.approve(id));
    }

    @RequiresPerm("returns:sale:approve")
    @PostMapping("/{id}/reject")
    public Result<SaleReturn> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.reject(id, String.valueOf(body.get("reason"))));
    }

    @OperLog(module = "SALE_RETURN", operType = "VOID", desc = "作废销售退货单")
    @RequiresPerm("returns:sale:void")
    @PostMapping("/{id}/void")
    public Result<SaleReturn> voidBill(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.voidBill(id, String.valueOf(body.get("reason"))));
    }

    @RequiresPerm("returns:sale:create")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("returns:sale:export")
    @GetMapping("/export")
    public Result<List<SaleReturn>> export(@RequestParam(required = false) Long companyId,
                                           @RequestParam(required = false) String customerCode,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String billDateStart,
                                           @RequestParam(required = false) String billDateEnd,
                                           @RequestParam(required = false) String keyword) {
        PageResult<SaleReturn> p = service.page(1, EXPORT_MAX_ROWS, companyId, customerCode, status, billDateStart, billDateEnd, keyword);
        return Result.success(p.getList());
    }
}
