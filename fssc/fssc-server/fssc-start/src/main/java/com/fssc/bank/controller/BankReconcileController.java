// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.controller;

import com.fssc.bank.entity.BankBalance;
import com.fssc.bank.entity.BankReconcile;
import com.fssc.bank.entity.BankReconItem;
import com.fssc.bank.service.BankReconcileService;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpBankStatement;
import com.fssc.receivable.entity.RcReceivable;
import java.time.format.DateTimeFormatter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 银企直连对账。 */
@RestController
@RequestMapping("/api/tenant/bank/reconcile")
@RequiredArgsConstructor
public class BankReconcileController {

    private final BankReconcileService reconcileService;

    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/page")
    public Result<PageResult<BankReconcile>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  String matchStatus) {
        return Result.success(reconcileService.page(pageNum, pageSize, matchStatus));
    }

    /** 未达账项调节表：按账户+月份生成（银行余额 vs 企业账面 + 三类未达项） */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/sheet")
    public Result<BankReconcileService.SheetResult> sheet(@RequestParam(required = false) Long bankAccountId,
                                                          @RequestParam(required = false) String month) {
        String m = (month == null || month.isBlank())
                ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                : month;
        return Result.success(reconcileService.sheet(bankAccountId, m));
    }

    /** 未达账差异明细：按账户+月份+类型查询（sheet 生成时写入数据库记录，可追溯） */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/items")
    public Result<List<BankReconItem>> items(@RequestParam(required = false) Long bankAccountId,
                                              @RequestParam(required = false) String month,
                                              @RequestParam(required = false) String itemType) {
        return Result.success(reconcileService.reconItems(bankAccountId, month, itemType));
    }

    /** 自动勾对：返回本轮结果（勾对/未勾对条数与未勾对流水号） */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:execute")  // D-G5-03 读写分离：写操作需执行码
    @OperLog(module = "BANK_RECON", operType = "UPDATE", desc = "自动勾对")
    @PostMapping("/run")
    public Result<Map<String, Object>> run() {
        return Result.success(reconcileService.run());
    }

    /** 人工勾对：手动将银行流水与付款记录关联 */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:execute")  // D-G5-03 读写分离：写操作需执行码
    @OperLog(module = "BANK_RECON", operType = "UPDATE", desc = "人工勾对")
    @PostMapping("/manual-match")
    public Result<Void> manualMatch(@RequestBody ManualMatchReq req) {
        reconcileService.manualMatch(req.getBankIds(), req.getPayIds(), req.getDiff());
        return Result.success(null);
    }

    // 银企直连闭环

    /** 银行流水分页查询 */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/statement/page")
    public Result<PageResult<OpBankStatement>> statementPage(@RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "10") long pageSize,
                                                              String reconStatus) {
        return Result.success(reconcileService.statementPage(pageNum, pageSize, reconStatus));
    }

    /** 收款认领核销：银行贷方流水 → 关联应收单（含冲减余额/置DONE/勾对记录） */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:execute")  // D-G5-03 读写分离：写操作需执行码
    @OperLog(module = "BANK_RECON", operType = "UPDATE", desc = "收款认领核销")
    @PostMapping("/claim")
    public Result<BankReconcileService.ClaimResult> claim(@RequestBody ClaimReq req) {
        return Result.success(reconcileService.claimReceipt(req.getStatementId(), req.getReceivableId()));
    }

    /** 回单归档：关联回单影像文件并置回单状态 */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:execute")  // D-G5-03 读写分离：写操作需执行码
    @OperLog(module = "BANK_RECON", operType = "UPDATE", desc = "回单归档")
    @PostMapping("/slip/archive")
    public Result<Void> archiveSlip(@RequestBody SlipReq req) {
        reconcileService.archiveSlip(req.getStatementId(), req.getFileId());
        return Result.success(null);
    }

    /** 待认领池：未勾对银行流水供人工认领核销应收 */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/pending-claim")
    public Result<PageResult<OpBankStatement>> pendingClaim(@RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(reconcileService.pendingClaim(pageNum, pageSize));
    }

    /**
     * 可认领应收单清单（<b>银行域专用</b>）。
     *
     * <p>认领弹层原先借应收域的 {@code /api/tenant/receivable/page} 取清单，
     * 该端点要求 {@code receivable:list}（应收域）⇒ 纯银企对账岗
     * （只有 {@code bank:reconcile:list}）打开认领弹层时清单恒空、动作做不了。
     * 本端点用<b>本域</b>权限码放行，查询权限随域走。
     */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/claimable-receivables")
    public Result<PageResult<RcReceivable>> claimableReceivables(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(reconcileService.claimableReceivables(pageNum, pageSize, keyword));
    }

    // 余额快照（衔接未达账项调节表 / TR05）

    /** 保存账户余额快照（同一账户同一日期去重覆盖） */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:execute")  // D-G5-03 读写分离：写操作需执行码
    @OperLog(module = "BANK_RECON", operType = "INSERT", desc = "保存银行账户余额快照")
    @PostMapping("/balance/save")
    public Result<Long> saveBalance(@RequestBody BalanceReq req) {
        return Result.success(reconcileService.saveBalance(req.getAccountId(), req.getBalanceDate(),
                req.getBalance(), req.getRemark()));
    }

    /** 账户余额快照列表 */
    @RequiresFeature("FSSC_BANK")
    @RequiresPerm("bank:reconcile:list")
    @GetMapping("/balance/list")
    public Result<List<BankBalance>> balanceList(@RequestParam(required = false) Long accountId) {
        return Result.success(reconcileService.balanceList(accountId));
    }

    @Data
    public static class BalanceReq {
        private Long accountId;
        private LocalDate balanceDate;
        private BigDecimal balance;
        private String remark;
    }

    @Data
    public static class ClaimReq {
        private Long statementId;
        private Long receivableId;
    }

    @Data
    public static class SlipReq {
        private Long statementId;
        private Long fileId;
    }

    @Data
    public static class ManualMatchReq {
        private List<Long> bankIds;
        private List<Long> payIds;
        private BigDecimal diff;
    }
}
