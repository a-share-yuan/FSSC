package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.service.SmartFillService;
import com.fssc.compliance.service.SmartFillService.SmartFillResult;
import com.fssc.expense.entity.ExpBill;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 智能填单助手（/api/tenant/compliance/smart-fill/**）。OCR 识别后自动填充报销单草稿。 */
@RestController
@RequestMapping("/api/tenant/compliance/smart-fill")
@RequiredArgsConstructor
public class SmartFillController {

    private final SmartFillService smartFillService;

    @RequiresPerm("compliance:smartfill:use")
    @GetMapping("/fill/{invoiceId}")
    public Result<SmartFillResult> smartFill(@PathVariable Long invoiceId) {
        return Result.success(smartFillService.smartFill(invoiceId));
    }

    @RequiresPerm("compliance:smartfill:use")
    @PostMapping("/batch-fill")
    public Result<SmartFillResult> batchSmartFill(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Long> invoiceIds = (List<Long>) req.get("invoiceIds");
        return Result.success(smartFillService.batchSmartFill(invoiceIds));
    }

    @RequiresPerm("compliance:smartfill:use")
    @GetMapping("/pre-applications/{applyUserId}")
    public Result<List<ExpBill>> listPreApplications(@PathVariable Long applyUserId) {
        return Result.success(smartFillService.listPreApplications(applyUserId));
    }
}