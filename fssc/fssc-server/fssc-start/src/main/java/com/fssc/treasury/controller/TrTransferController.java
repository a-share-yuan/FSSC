// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.treasury.entity.TrTransfer;
import com.fssc.treasury.service.TrTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资金调拨管理（/api/tenant/treasury/transfer/**）
 * <p>扩展：资金域写端点补操作审计记录（原全类零 {@code @OperLog}＝库有痕、日志无行）。
 */
@RestController
@RequestMapping("/api/tenant/treasury/transfer")
@RequiredArgsConstructor
public class TrTransferController {

    private final TrTransferService service;

    /**
     * 调拨列表（乙：出参带三态明示 execStateCode/execStateText/journalText——
     * 「已审批·未执行·账户余额未变」与「已执行」在列表上就能分开，且明示未登账）
     */
    @RequiresPerm("treasury:transfer:list")
    @GetMapping("/page")
    public Result<PageResult<TrTransfer>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               String status) {
        return Result.success(service.page(pageNum, pageSize, status));
    }

    /** 调拨详情（乙：同列表，另带账证侧 voucherNo/voucherStatus 供对照） */
    @RequiresPerm("treasury:transfer:list")
    @GetMapping("/{id}")
    public Result<TrTransfer> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @RequiresPerm("treasury:transfer:create")
    @OperLog(module = "TREASURY", operType = "INSERT", desc = "资金调拨-建单")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody TrTransfer transfer) {
        return Result.success(service.create(transfer));
    }

    /** 资金上存（T1）：上划集团资金池并发起 TR_UPLOAD 审批流 */
    @RequiresPerm("treasury:transfer:create")
    @OperLog(module = "TREASURY", operType = "INSERT", desc = "资金上存-建单")
    @PostMapping("/upload")
    public Result<Long> upload(@RequestBody TrTransfer transfer) {
        return Result.success(service.upload(transfer));
    }

    /** 提交/重提调拨审批（驳回保持 DRAFT，此端点即正规重提通道）；flow=TR_UPLOAD 重提上存单 */
    @RequiresPerm("treasury:transfer:create")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金调拨-提交/重提审批")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestParam(required = false, defaultValue = "TR_TRANSFER") String flow) {
        service.submit(id, flow);
        return Result.success();
    }

    @RequiresPerm("treasury:transfer:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金调拨-核准放行")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }

    /** 调拨执行，落银行账户余额 */
    @RequiresPerm("treasury:transfer:execute")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金调拨-执行动账")
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        service.execute(id);
        return Result.success();
    }
}