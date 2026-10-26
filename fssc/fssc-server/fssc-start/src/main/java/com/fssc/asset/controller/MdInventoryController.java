// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.controller;

import com.fssc.asset.entity.MdInventory;
import com.fssc.asset.entity.StockMove;
import com.fssc.asset.service.MdInventoryService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 存货收发存（/api/tenant/inventory/**）：入库/出库/结存（FA 投资到资产-存货） */
@RestController
@RequestMapping("/api/tenant/inventory")
@RequiredArgsConstructor
public class MdInventoryController {

    private final MdInventoryService service;

    @RequiresPerm("inventory:list")
    @GetMapping("/page")
    public Result<PageResult<MdInventory>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    /** R5-D-189：出入库流水查询（stock_move 只读回放，此前该表只写不读）。 */
    @RequiresPerm("inventory:list")
    @GetMapping("/moves/page")
    public Result<PageResult<StockMove>> movesPage(PageQuery query, String itemCode, Long warehouseId) {
        return Result.success(service.movesPage(query.getPageNum(), query.getPageSize(), itemCode, warehouseId));
    }

    /** 入库（移动平均更新结存），body 携带物料信息，qty/price 为数量与单价 */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货入库")
    @PostMapping("/inStock")
    public Result<Void> inStock(@RequestBody MdInventory item,
                                @RequestParam BigDecimal qty,
                                @RequestParam BigDecimal price) {
        service.inStock(item, qty, price);
        return Result.success();
    }

    /** 出库（移动平均规则计算发出成本），返回发出成本 */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货出库")
    @PostMapping("/outStock")
    public Result<BigDecimal> outStock(@RequestBody MdInventory item, @RequestParam BigDecimal qty) {
        return Result.success(service.outStock(item, qty));
    }

    /** 结存查询：返回结存数量与金额 */
    @RequiresPerm("inventory:list")
    @PostMapping("/balance")
    public Result<MdInventory> balance(@RequestBody MdInventory item) {
        return Result.success(service.balanceQuery(item));
    }

    /** 存货领用出库（发起 INV_ISSUE 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货领用出库")
    @PostMapping("/issue")
    public Result<Void> issue(@RequestBody MdInventory item, @RequestParam BigDecimal qty, String purpose, String projectType) {
        service.issue(item, qty, purpose, projectType);
        return Result.success();
    }

    /** 存货报废（发起 INV_DISPOSE 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货报废")
    @PostMapping("/disposeScrap")
    public Result<Void> disposeScrap(@RequestBody MdInventory item, @RequestParam BigDecimal qty) {
        service.disposeScrap(item, qty);
        return Result.success();
    }

    /** 存货处置（发起 INV_DISPOSAL 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货处置")
    @PostMapping("/disposal")
    public Result<Void> disposal(@RequestBody MdInventory item, @RequestParam BigDecimal qty, String remark) {
        service.disposal(item, qty, remark);
        return Result.success();
    }

    /** 存货处置-物资状态确认（发起 INV_DISPOSAL_CHECK 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货处置-状态确认")
    @PostMapping("/disposalCheck")
    public Result<Void> disposalCheck(@RequestBody MdInventory item, @RequestParam BigDecimal qty, String remark) {
        service.disposalCheck(item, qty, remark);
        return Result.success();
    }

    /** 存货处置-执行与账务（发起 INV_DISPOSAL_EXEC 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货处置-执行账务")
    @PostMapping("/disposalExec")
    public Result<Void> disposalExec(@RequestBody MdInventory item, @RequestParam BigDecimal qty, String remark) {
        service.disposalExec(item, qty, remark);
        return Result.success();
    }

    /** 存货内部销售（发起 INV_INTERNAL_SALE 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货内部销售")
    @PostMapping("/internalSale")
    public Result<Void> internalSale(@RequestBody MdInventory item, @RequestParam BigDecimal qty, Long targetWarehouseId) {
        service.internalSale(item, qty, targetWarehouseId);
        return Result.success();
    }

    /** 存货盘点差异（发起 INV_PHYSICAL_CHECK 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货盘点差异")
    @PostMapping("/physicalCheck")
    public Result<Void> physicalCheck(@RequestBody MdInventory item, @RequestParam BigDecimal checkedQty) {
        service.physicalCheck(item, checkedQty);
        return Result.success();
    }

    /** 存货跌价准备（发起 INV_DEVAL 审批） */
    @RequiresPerm("inventory:update")
    @OperLog(module = "INVENTORY", operType = "UPDATE", desc = "存货跌价准备")
    @PostMapping("/devalue")
    public Result<Void> devalue(@RequestBody MdInventory item, @RequestParam BigDecimal provisionAmount) {
        service.devalue(item, provisionAmount);
        return Result.success();
    }
}