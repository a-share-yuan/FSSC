// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.returns.entity.PurReturn;
import com.fssc.returns.service.PurReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 采购退货单 OpenAPI（/api/tenant/pur-returns/**）。
 * 状态机：DRAFT→PENDING→APPROVED→VOIDED；REJECTED 可改后重新提交。
 */
@RestController
@RequestMapping("/api/tenant/pur-returns")
@RequiredArgsConstructor
public class PurReturnController {

    private final PurReturnService service;

    /**
     * 导出上限（D-N22）：本端点返回的是 JSON 列表而非文件流，原先传 {@code Integer.MAX_VALUE}
     * 等于"一次请求把整张 pur_return 读进内存再吐出"，既无行数上限也无数据范围。
     * 现在此封顶，数据范围由 {@code PurReturnService.page} 上的 {@code @DataScope} 收口。
     * 超出即截断（取前 N 行），与分页端点同语义。
     */
    private static final long EXPORT_MAX_ROWS = 10000;

    @RequiresPerm("returns:pur:list")
    @GetMapping
    public Result<PageResult<PurReturn>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "20") long pageSize,
                                               @RequestParam(required = false) Long companyId,
                                               @RequestParam(required = false) String vendorCode,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String billDateStart,
                                               @RequestParam(required = false) String billDateEnd,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(service.page(pageNum, pageSize, companyId, vendorCode, status, billDateStart, billDateEnd, keyword));
    }

    @RequiresPerm("returns:pur:list")
    @GetMapping("/{id}")
    public Result<PurReturn> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @OperLog(module = "PUR_RETURN", operType = "ADD", desc = "新建采购退货单")
    @RequiresPerm("returns:pur:create")
    @PostMapping
    public Result<PurReturn> create(@RequestBody PurReturn r) {
        return Result.success(service.create(r));
    }

    @OperLog(module = "PUR_RETURN", operType = "UPDATE", desc = "修改采购退货单")
    @RequiresPerm("returns:pur:create")
    @PutMapping("/{id}")
    public Result<PurReturn> update(@PathVariable Long id, @RequestBody PurReturn r) {
        r.setId(id);
        return Result.success(service.update(r));
    }

    @RequiresPerm("returns:pur:submit")
    @PostMapping("/{id}/submit")
    public Result<PurReturn> submit(@PathVariable Long id) {
        return Result.success(service.submit(id));
    }

    @RequiresPerm("returns:pur:approve")
    @PostMapping("/{id}/approve")
    public Result<PurReturn> approve(@PathVariable Long id) {
        return Result.success(service.approve(id));
    }

    @RequiresPerm("returns:pur:approve")
    @PostMapping("/{id}/reject")
    public Result<PurReturn> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.reject(id, String.valueOf(body.get("reason"))));
    }

    @OperLog(module = "PUR_RETURN", operType = "VOID", desc = "作废采购退货单")
    @RequiresPerm("returns:pur:void")
    @PostMapping("/{id}/void")
    public Result<PurReturn> voidBill(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.voidBill(id, String.valueOf(body.get("reason"))));
    }

    @RequiresPerm("returns:pur:create")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("returns:pur:export")
    @GetMapping("/export")
    public Result<List<PurReturn>> export(@RequestParam(required = false) Long companyId,
                                          @RequestParam(required = false) String vendorCode,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String billDateStart,
                                          @RequestParam(required = false) String billDateEnd,
                                          @RequestParam(required = false) String keyword) {
        PageResult<PurReturn> p = service.page(1, EXPORT_MAX_ROWS, companyId, vendorCode, status, billDateStart, billDateEnd, keyword);
        return Result.success(p.getList());
    }
}
