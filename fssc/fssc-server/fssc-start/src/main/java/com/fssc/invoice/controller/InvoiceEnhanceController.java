package com.fssc.invoice.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.invoice.entity.InvInvoiceLine;
import com.fssc.invoice.entity.InvVerifyLog;
import com.fssc.invoice.service.InvoiceEnhanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 发票增强（/api/tenant/invoice/enhance/**）：明细行 + 验真日志。 */
@RestController
@RequestMapping("/api/tenant/invoice/enhance")
@RequiredArgsConstructor
public class InvoiceEnhanceController {

    private final InvoiceEnhanceService enhanceService;

    // ---------- 明细行 ----------

    @RequiresPerm("inv:line:list")
    @GetMapping("/line/list")
    public Result<List<InvInvoiceLine>> lines(@RequestParam Long invoiceId) {
        return Result.success(enhanceService.linesByInvoice(invoiceId));
    }

    @RequiresPerm("inv:line:create")
    @OperLog(module = "INV_INVOICE_LINE", operType = "INSERT", desc = "新增发票明细行")
    @PostMapping("/line/create")
    public Result<Long> addLine(@RequestBody InvInvoiceLine line) {
        return Result.success(enhanceService.addLine(line));
    }

    @RequiresPerm("inv:line:update")
    @OperLog(module = "INV_INVOICE_LINE", operType = "UPDATE", desc = "覆盖保存发票明细行")
    @PostMapping("/line/replace")
    public Result<Void> replaceLines(@RequestParam Long invoiceId, @RequestBody List<InvInvoiceLine> lines) {
        enhanceService.replaceLines(invoiceId, lines);
        return Result.success();
    }

    @RequiresPerm("inv:line:delete")
    @OperLog(module = "INV_INVOICE_LINE", operType = "DELETE", desc = "删除发票明细行")
    @PostMapping("/line/{id}/delete")
    public Result<Void> deleteLine(@PathVariable Long id) {
        enhanceService.deleteLine(id);
        return Result.success();
    }

    // ---------- 验真日志（审计表只增） ----------

    @RequiresPerm("inv:verify:list")
    @GetMapping("/verify/page")
    public Result<PageResult<InvVerifyLog>> verifyPage(PageQuery query, @RequestParam(required = false) Long invoiceId) {
        return Result.success(enhanceService.verifyLogPage(query.getPageNum(), query.getPageSize(), invoiceId));
    }

    @RequiresPerm("inv:verify:run")
    @OperLog(module = "INV_VERIFY_LOG", operType = "INSERT", desc = "发票验真")
    @PostMapping("/verify/run")
    public Result<Void> runVerify(@RequestBody Map<String, Object> body) {
        Long invoiceId = ((Number) body.get("invoiceId")).longValue();
        String channel = (String) body.get("channel");
        String reqParam = (String) body.get("reqParam");
        String respContent = (String) body.get("respContent");
        String result = (String) body.get("result");
        String msg = (String) body.get("msg");
        enhanceService.recordVerify(invoiceId, channel, reqParam, respContent, result, msg,
                UserContext.get() == null ? null : UserContext.get().getUserId());
        return Result.success();
    }

    /** 实时/手动验真（需求 7.1 实时验真、7.2 手动验真）：单张发票立即验真并回写状态 */
    @RequiresPerm("inv:verify")
    @OperLog(module = "INV_VERIFY_LOG", operType = "UPDATE", desc = "实时验真")
    @PostMapping("/verify/now")
    public Result<Void> verifyNow(@RequestBody Map<String, Object> body) {
        Long invoiceId = ((Number) body.get("invoiceId")).longValue();
        String result = (String) body.get("result");
        String msg = (String) body.get("msg");
        enhanceService.verifyNow(invoiceId, result, msg,
                UserContext.get() == null ? null : UserContext.get().getUserId());
        return Result.success();
    }

    /** 批量验真（发票列表多选 → 一键验真）：对多张发票执行验真，返回成功条数 */
    @RequiresPerm("inv:verify")
    @OperLog(module = "INV_VERIFY_LOG", operType = "UPDATE", desc = "批量验真")
    @PostMapping("/verify/batch")
    public Result<Integer> verifyBatch(@RequestBody List<Long> invoiceIds) {
        return Result.success(enhanceService.verifyBatch(invoiceIds,
                UserContext.get() == null ? null : UserContext.get().getUserId()));
    }
}
