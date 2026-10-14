// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.common.core.PageResult;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.service.FraudDetectionService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/compliance/fraud")
@RequiredArgsConstructor
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;

    @RequiresPerm("compliance:fraud:detect")
    @OperLog(module = "FRAUD_DETECTION", operType = "INSERT", desc = "执行反欺诈检测")
    @PostMapping("/detect")
    public Result<List<FraudDetectionLog>> detectFraud(@RequestBody(required = false) Map<String, Object> req) {
        Long employeeId = req != null && req.get("employeeId") != null ? Long.valueOf(req.get("employeeId").toString()) : null;
        Long billId = req != null && req.get("billId") != null ? Long.valueOf(req.get("billId").toString()) : null;
        return Result.success(fraudDetectionService.detectFraud(employeeId, billId));
    }

    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/by-employee")
    public Result<List<FraudDetectionLog>> listByEmployee(@RequestParam(required = false) Long employeeId) {
        if (employeeId == null) {
            return Result.success(List.of());
        }
        return Result.success(fraudDetectionService.listByEmployee(employeeId));
    }

    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/by-bill")
    public Result<List<FraudDetectionLog>> listByBill(@RequestParam(required = false) Long billId) {
        if (billId == null) {
            return Result.success(List.of());
        }
        return Result.success(fraudDetectionService.listByBill(billId));
    }

    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/list")
    public Result<List<FraudDetectionLog>> list(@RequestParam(required = false) String detectionResult,
                                                 @RequestParam(required = false) Long employeeId,
                                                 @RequestParam(required = false) String actionTaken) {
        return Result.success(fraudDetectionService.list(detectionResult, employeeId, actionTaken));
    }

    /**
     * 人工复审：复审人以登录态为准，不接受前端传值 —— 旧实现由前端传 reviewerId，
     * 页面固定传 0，留痕全记成匿名/管理员，审计链等于没有。
     */
    @RequiresPerm("compliance:fraud:review")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "审核欺诈检测记录")
    @PostMapping("/review/{id}")
    public Result<Void> review(@PathVariable Long id,
                               @RequestBody Map<String, Object> req) {
        String action = req.get("action") != null ? req.get("action").toString() : "";
        fraudDetectionService.review(id, currentReviewerId(), action);
        return Result.success();
    }

    /** 41号审查 P2-12 申诉登记：body {reason}，原因必填留痕，处置状态不变待复审 */
    @RequiresPerm("compliance:fraud:list")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "欺诈风险申诉登记")
    @PostMapping("/{id}/appeal")
    public Result<Void> appeal(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        fraudDetectionService.appeal(id, (String) req.get("reason"));
        return Result.success();
    }

    /** 41号审查 P2-12 申诉复审：body {approve:true/false, note}，通过则放行结案 */
    @RequiresPerm("compliance:fraud:review")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "欺诈风险申诉复审")
    @PostMapping("/{id}/appeal-review")
    public Result<Void> appealReview(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        boolean approve = Boolean.parseBoolean(String.valueOf(req.get("approve")));
        fraudDetectionService.appealReview(id, approve, (String) req.get("note"), currentReviewerId());
        return Result.success();
    }

    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/high-risk-count")
    public Result<Long> getHighRiskCount(@RequestParam Long employeeId) {
        return Result.success(fraudDetectionService.getHighRiskCount(employeeId));
    }

    @RequiresPerm("compliance:fraud:detect")
    @OperLog(module = "FRAUD_DETECTION", operType = "INSERT", desc = "执行增强反欺诈检测")
    @PostMapping("/enhanced-detect")
    public Result<List<FraudDetectionLog>> enhancedDetect(@RequestBody Map<String, Object> req) {
        Long employeeId = req.get("employeeId") != null ? Long.valueOf(req.get("employeeId").toString()) : null;
        Long billId = req.get("billId") != null ? Long.valueOf(req.get("billId").toString()) : null;
        BigDecimal amount = req.get("amount") != null ? new BigDecimal(req.get("amount").toString()) : null;
        String supplierCode = (String) req.get("supplierCode");
        String expenseType = (String) req.get("expenseType");
        String expenseDateStr = (String) req.get("expenseDate");
        LocalDate expenseDate = expenseDateStr != null ? LocalDate.parse(expenseDateStr) : null;
        return Result.success(fraudDetectionService.enhancedDetect(employeeId, billId, amount, supplierCode, expenseType, expenseDate));
    }

    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getFraudSummary() {
        return Result.success(fraudDetectionService.getFraudSummary());
    }

    /** P2-5: 分页查询（风控看板 / 人工待审队列：actionTaken=REVIEW + unreviewed=true） */
    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/page")
    public Result<PageResult<FraudDetectionLog>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String detectionResult,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String detectionType,
            @RequestParam(required = false) String actionTaken,
            @RequestParam(required = false) Boolean unreviewed) {
        return Result.success(fraudDetectionService.page(pageNum, pageSize, detectionResult,
                employeeId, detectionType, actionTaken, unreviewed));
    }

    private Long currentReviewerId() {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.getRequired();
        if ("admin".equals(u.getUsername()) || u.getUserId() == null || u.getUserId() == 0L) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "风控复审不得由管理员办理，请使用合规/风控岗账号");
        }
        return u.getUserId();
    }
}