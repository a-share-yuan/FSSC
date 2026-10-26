// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 采购三单域：采购订单 + 收货单。 */
@RestController
@RequestMapping("/api/tenant/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    // ---- 采购订单 ----
    /** @param receivable 收货选单专用：true 时只出可收货订单（C-9 / D-RCV-02） */
    @RequiresPerm("po:order:list")
    @GetMapping("/order/page")
    public Result<PageResult<PoOrder>> orderPage(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false, defaultValue = "false") boolean receivable) {
        return Result.success(purchaseService.orderPage(pageNum, pageSize, keyword, status, receivable));
    }

    @RequiresPerm("po:order:list")
    @PostMapping("/order/create")
    public Result<Long> createOrder(@RequestBody PoOrder o) {
        return Result.success(purchaseService.createOrder(o));
    }

    @RequiresPerm("po:order:list")
    @PostMapping("/order/update")
    public Result<Void> updateOrder(@RequestBody PoOrder o) {
        purchaseService.updateOrder(o);
        return Result.success();
    }

    /** 提交采购订单审批：DRAFT → OPEN + 发起 PURCHASE_ORDER_REVIEW */
    @RequiresPerm("po:order:list")
    @OperLog(module = "PURCHASE", operType = "UPDATE", desc = "提交采购订单审批")
    @PostMapping("/order/{id}/submit")
    public Result<Void> submitOrder(@PathVariable Long id) {
        purchaseService.submitOrder(id);
        return Result.success();
    }

    /** 驳回采购订单编辑重提：REJECTED → DRAFT，修改后经 submit 重新起流程 */
    @RequiresPerm("po:order:list")
    @OperLog(module = "PURCHASE", operType = "UPDATE", desc = "驳回采购订单转编辑重提")
    @PostMapping("/order/{id}/edit")
    public Result<Void> editOrderForResubmit(@PathVariable Long id) {
        purchaseService.returnToEditOrder(id);
        return Result.success();
    }

    /**  删除采购订单：仅 DRAFT 且无下游关联（收货单/累计收货）可删，逻辑删除 */
    @RequiresPerm("po:order:list")
    @OperLog(module = "PURCHASE", operType = "DELETE", desc = "删除采购订单(草稿)")
    @PostMapping("/order/{id}/delete")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        purchaseService.deleteOrder(id);
        return Result.success();
    }

    /**
     * 作废采购订单（接口契约与前端约定，路径勿改）：
     * POST /api/tenant/purchase/order/void，body {orderId: long, reason: string}。
     * 已收货/已有付款关联拒绝；通过后 status=VOID（del_flag=0 保留，可追溯），remark 追加原因+时间+操作人。
     */
    @RequiresPerm("po:order:list")
    @OperLog(module = "PURCHASE", operType = "VOID", desc = "作废采购订单")
    @PostMapping("/order/void")
    public Result<Void> voidOrder(@RequestBody java.util.Map<String, Object> req) {
        if (req == null || req.get("orderId") == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "orderId 必填");
        }
        Long orderId;
        try {
            orderId = Long.valueOf(req.get("orderId").toString().trim());
        } catch (NumberFormatException ex) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "orderId 须为数字，收到: " + req.get("orderId"));
        }
        String reason = req.get("reason") == null ? null : req.get("reason").toString();
        purchaseService.voidOrder(orderId, reason);
        return Result.success();
    }

    /**
     * P0-5 最终状态机分治·乙（X-20 裁定）：终止采购订单＝人工决策，故留显式入口。
     * POST /api/tenant/purchase/order/{id}/terminate，body {reason: string}。
     * 与 /order/void 的分工：作废处理「无下游动作」的单，终止处理「已收货/已挂付款但不再履约」的单；
     * 两者同落既有最终状态 VOID。鉴权走写专码 po:order:update（写读分面治理规则，不复用 list 码）。
     * 达标型最终状态（PAID/CLOSED）不在此开口，由 PoOrderFinalStateJob 批处理推进。
     */
    @RequiresPerm("po:order:update")
    @OperLog(module = "PURCHASE", operType = "VOID", desc = "终止采购订单")
    @PostMapping("/order/{id}/terminate")
    public Result<Void> terminateOrder(@PathVariable("id") Long id,
                                       @RequestBody(required = false) java.util.Map<String, Object> req) {
        String reason = req == null || req.get("reason") == null ? null : req.get("reason").toString();
        purchaseService.terminateOrder(id, reason);
        return Result.success();
    }

    /**
     * P0-5 最终状态机分治·乙（X-20 裁定）：归档采购订单（CLOSED → ARCHIVED）＝人工结案判断，
     * 没有累计量判据可给批处理用（要的是「这批单可以结案」的决定），故留显式入口＋前端按钮。
     * POST /api/tenant/purchase/order/{id}/archive；鉴权写专码 po:order:update。
     */
    @RequiresPerm("po:order:update")
    @OperLog(module = "PURCHASE", operType = "UPDATE", desc = "归档采购订单")
    @PostMapping("/order/{id}/archive")
    public Result<Void> archiveOrder(@PathVariable("id") Long id) {
        purchaseService.archiveOrder(id);
        return Result.success();
    }

    // ---- 收货单 ----
    @RequiresPerm("po:receipt:list")
    @GetMapping("/receipt/page")
    public Result<PageResult<PoReceipt>> receiptPage(@RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status) {
        return Result.success(purchaseService.receiptPage(pageNum, pageSize, keyword, status));
    }

    @RequiresPerm("po:receipt:list")
    @PostMapping("/receipt/create")
    public Result<Long> createReceipt(@RequestBody PoReceipt r) {
        return Result.success(purchaseService.createReceipt(r));
    }
}
