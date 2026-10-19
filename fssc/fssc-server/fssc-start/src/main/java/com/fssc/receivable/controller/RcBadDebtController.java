package com.fssc.receivable.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.service.RcBadDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 坏账计提管理（/api/tenant/bad-debt/**）：按逾期账龄计提坏账准备 */
@RestController
@RequestMapping("/api/tenant/bad-debt")
@RequiredArgsConstructor
public class RcBadDebtController {

    private final RcBadDebtService service;
    private final com.fssc.receivable.service.RcBadDebtExcelService badDebtExcelService;

    @RequiresPerm("receivable:list")
    @GetMapping("/list")
    public Result<List<RcBadDebt>> list(@RequestParam(required = false) String period) {
        return Result.success(service.listByPeriod(period));
    }

    @RequiresPerm("receivable:overdue")
    @OperLog(module = "BAD_DEBT", operType = "INSERT", desc = "按逾期账龄计提坏账")
    @PostMapping("/provision")
    public Result<Integer> provision(@RequestParam String period) {
        return Result.success(service.provisionScan(period));
    }

    /** #19 坏账转回：置 REVERSED 并生成反向（红字）凭证对冲计提 */
    @RequiresPerm("receivable:overdue")
    @OperLog(module = "BAD_DEBT", operType = "UPDATE", desc = "坏账转回并生成红字凭证")
    @PostMapping("/{id}/reverse")
    public Result<Map<String, Object>> reverse(@PathVariable Long id) {
        return Result.success(service.reverse(id));
    }

    /** 坏账确认（BAD_DEBT_CONFIRM）：坏账申请报告经批准后确认核销，发起坏账确认审批流 */
    @RequiresPerm("receivable:overdue")
    @OperLog(module = "BAD_DEBT", operType = "UPDATE", desc = "坏账确认")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.confirm(id, reason);
        return Result.success();
    }

    /**
     * 坏账核销（蓝图流程22，AR-16-2）：body {amount, reason, basisNo}。
     * 核销金额不能超过该客户已计提坏账准备余额；生成 借1231/贷1122 凭证并冲减应收余额。
     */
    /** 坏账核销模板 Excel 导入（附件11.4.2，9列；EKP 仅推申请人，明细由模板导入——蓝图流程22） */
    @RequiresPerm("receivable:overdue")
    @OperLog(module = "BAD_DEBT", operType = "INSERT", desc = "坏账核销模板Excel导入")
    @PostMapping(value = "/writeoff-import-excel", consumes = {"multipart/form-data"})
    public Result<Map<String, Object>> writeoffImportExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(badDebtExcelService.importAndWriteOff(file, service));
    }

    @RequiresPerm("receivable:overdue")
    @OperLog(module = "BAD_DEBT", operType = "UPDATE", desc = "坏账核销")
    @PostMapping("/{id}/writeoff")
    public Result<Map<String, Object>> writeOff(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal amount = body.get("amount") == null ? null
                : new java.math.BigDecimal(String.valueOf(body.get("amount")));
        return Result.success(service.writeOff(id, amount,
                (String) body.get("reason"), (String) body.get("basisNo")));
    }
}