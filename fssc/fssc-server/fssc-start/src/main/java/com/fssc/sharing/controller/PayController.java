// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.service.PayBatchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 出纳付款（/api/tenant/pay/**）：付款记录 → 批次 → 确认 → 执行付款 */
@RestController
@RequestMapping("/api/tenant/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayBatchService payBatchService;

    // 付款记录

    @RequiresPerm("pay:payment:create")
    @OperLog(module = "PAY", operType = "INSERT", desc = "生成付款记录")
    @PostMapping("/payment/create/{billId}")
    public Result<Long> createPayment(@PathVariable Long billId, @RequestBody ExpBillPayment req) {
        return Result.success(payBatchService.createPayment(billId, req));
    }

    @RequiresPerm("pay:payment:list")
    @GetMapping("/payment/list/{billId}")
    public Result<List<ExpBillPayment>> payments(@PathVariable Long billId) {
        return Result.success(payBatchService.payments(billId));
    }

    // 付款批次

    @RequiresPerm("pay:batch:create")
    @OperLog(module = "PAY", operType = "INSERT", desc = "新建付款批次")
    @PostMapping("/batch/create")
    public Result<Long> createBatch(@RequestBody OpPayBatch batch) {
        return Result.success(payBatchService.createBatch(batch));
    }

    /** 自动组批：WAITING 付款记录按收款方+出账账户归组为 DRAFT 批次；返回建批数与跳过原因（/C2②′） */
    @RequiresPerm("pay:batch:create")
    @PostMapping("/batch/auto-group")
    public Result<PayBatchService.AutoGroupResult> autoGroup() {
        return Result.success(payBatchService.autoGroup());
    }

    /** C1：待付记录清单（WAITING 且未入批），供"选待付记录成批"勾选 */
    @RequiresPerm("pay:batch:list")
    @GetMapping("/payment/waiting-page")
    public Result<PageResult<Map<String, Object>>> waitingPayments(
            PageQuery query, @RequestParam(required = false) String keyword) {
        return Result.success(payBatchService.waitingPayments(
                query.getPageNum(), query.getPageSize(), keyword));
    }

    /** C1：勾选待付记录一步成批（同公司/同币别/同出账账户校验在service） */
    @RequiresPerm("pay:batch:create")
    @OperLog(module = "PAY", operType = "INSERT", desc = "选待付记录成批")
    @PostMapping("/batch/create-from-payments")
    public Result<Long> createBatchFromPayments(@RequestBody BatchFromPaymentsReq req) {
        return Result.success(payBatchService.createBatchFromPayments(
                req.getBillPaymentIds(), req.getBankAccountId(), req.getCompanyId()));
    }

    @RequiresPerm("pay:batch:create")
    @OperLog(module = "PAY", operType = "INSERT", desc = "批次加明细")
    @PostMapping("/batch/{id}/detail")
    public Result<Long> addDetail(@PathVariable Long id, @RequestBody DetailReq req) {
        return Result.success(payBatchService.addDetail(id, req.getBillPaymentId()));
    }

    @RequiresPerm("pay:batch:void")
    @OperLog(module = "PAY", operType = "UPDATE", desc = "作废付款批次")
    @PostMapping("/batch/{id}/void")
    public Result<Void> voidBatch(@PathVariable Long id) {
        payBatchService.voidBatch(id);
        return Result.success();
    }

    /** 驳回批次编辑重提：REJECTED → DRAFT，可增删明细后重新 confirm 发起 TR_PAY */
    @RequiresPerm("pay:batch:create")
    @OperLog(module = "PAY", operType = "UPDATE", desc = "驳回付款批次转编辑重提")
    @PostMapping("/batch/{id}/edit")
    public Result<Void> editBatchForResubmit(@PathVariable Long id) {
        payBatchService.returnToEdit(id);
        return Result.success();
    }

    @RequiresPerm("pay:batch:confirm")
    @OperLog(module = "PAY", operType = "UPDATE", desc = "确认付款批次")
    @PostMapping("/batch/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        payBatchService.confirm(id);
        return Result.success();
    }

    @RequiresPerm("pay:batch:execute")
    @OperLog(module = "PAY", operType = "UPDATE", desc = "执行付款")
    @PostMapping("/batch/{id}/execute")
    public Result<String> execute(@PathVariable Long id) {
        return Result.success(payBatchService.execute(id));
    }

    /**
     * 执行期拦口逐条透出（只读）。
     *
     * <p>「执行付款」被强制校验拦下时，{@link #execute} 抛的是拼好的字符串，前端只能弹一条 toast，
     * 出纳看不出是哪条明细、缺哪一环节、去哪儿补，页面无解拦出口 ⇒ 批次锁死在已审批态。
     * 本端点复用 {@code execute()} 的同一条判据（{@code ThreeWayMatchService.check}），把每个拦口
     * 结构化返回：明细坐标 + 金额链 + 类型化差异码 + 缺环节清单 + 处理出口。
     *
     * <p>读权限挂 {@code pay:batch:list}（sys_menu id=3261）而非 execute——本接口不产生任何付款动作，
     * 也不放宽那道校验；校验本身对不对、要不要给"财务主管解拦放行"的出口，属校验策略／权限待定范围，未在此实现。
     */
    @RequiresPerm("pay:batch:list")
    @GetMapping("/batch/{id}/block-report")
    public Result<List<Map<String, Object>>> blockReport(@PathVariable Long id) {
        return Result.success(payBatchService.executeBlockReport(id));
    }

    @RequiresPerm("pay:batch:list")
    @GetMapping("/batch/page")
    public Result<PageResult<OpPayBatch>> batchPage(PageQuery query) {
        return Result.success(payBatchService.batchPage(query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("pay:batch:list")
    @GetMapping("/batch/{id}/details")
    public Result<List<OpPayBatchDetail>> details(@PathVariable Long id) {
        return Result.success(payBatchService.details(id));
    }

    @Data
    public static class DetailReq {
        private Long billPaymentId;
    }

    /** C1：选待付记录成批的入参 */
    @Data
    public static class BatchFromPaymentsReq {
        private List<Long> billPaymentIds;
        private Long bankAccountId;
        private Long companyId;
    }
}
