// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.service.FraudDetectionService;
import com.fssc.compliance.vo.FraudDetectionLogVO;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
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
     * 页面固定传 0，记录全记成匿名/管理员，审计链等于没有。
     */
    @RequiresPerm("compliance:fraud:review")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "审核欺诈检测记录")
    @PostMapping("/review/{id}")
    public Result<Void> review(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, Object> req) {
        // @RequestBody 未设 required=false 时，客户端漏发 body 会抛
        // HttpMessageNotReadableException（"Required request body is missing"），被全局兜成 500「系统异常」；
        // 置为可选后 req 可能为 null，若不解判又会 NPE 再兜 500。两者都属客户端请求格式问题，
        // 必须给 400 并讲清期望的请求体，避免排查期误判成"复审/放行功能坏了"。
        requireBody(req, "复审", "{action: RELEASE|BLOCK|REVIEW}");
        String action = req.get("action") != null ? req.get("action").toString() : "";
        fraudDetectionService.review(id, currentReviewerId(), action);
        return Result.success();
    }

    /** 41号审查 申诉登记：body {reason}，原因必填记录，处置状态不变待复审 */
    @RequiresPerm("compliance:fraud:list")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "欺诈风险申诉登记")
    @PostMapping("/{id}/appeal")
    public Result<Void> appeal(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req) {
        requireBody(req, "申诉登记", "{reason: 申诉原因}"); // D-F1R-05 同 review：漏 body 应给 400 而非 500
        fraudDetectionService.appeal(id, (String) req.get("reason"));
        return Result.success();
    }

    /** 41号审查 申诉复审：body {approve:true/false, note}，通过则放行结案 */
    @RequiresPerm("compliance:fraud:review")
    @OperLog(module = "FRAUD_DETECTION", operType = "UPDATE", desc = "欺诈风险申诉复审")
    @PostMapping("/{id}/appeal-review")
    public Result<Void> appealReview(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req) {
        requireBody(req, "申诉复审", "{approve: true|false, note: 复审意见}"); // D-F1R-05 同上
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
    public Result<List<FraudDetectionLog>> enhancedDetect(@RequestBody(required = false) Map<String, Object> req) {
        requireBody(req, "增强检测", "{employeeId: 员工ID, billId: 单据ID, amount: 金额}"); // D-F1R-05 同上
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

    /** 分页查询（风控看板 / 人工待审队列：actionTaken=REVIEW + unreviewed=true） */
    @RequiresPerm("compliance:fraud:list")
    @GetMapping("/page")
    public Result<PageResult<FraudDetectionLogVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String detectionResult,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String detectionType,
            @RequestParam(required = false) String actionTaken,
            @RequestParam(required = false) Boolean unreviewed,
            @RequestParam(required = false) Boolean pendingRelease) {
        return Result.success(fraudDetectionService.page(pageNum, pageSize, detectionResult,
                employeeId, detectionType, actionTaken, unreviewed, pendingRelease));
    }

    /**
     * 请求体缺失的统一兜底。
     * <p>本控制器对 body 一律按「可选接收 + 显式判空」处理，原因见 {@code review} 处注释；
     * 缺失时抛 400 {@code PARAM_INVALID} 并把期望的 body 形态写进提示，
     * 而不是让 {@code null.get()} 一路 NPE 到全局兜底成 500「系统异常」。
     */
    private void requireBody(Map<String, Object> req, String action, String expected) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "缺少请求体：" + action + "需要 JSON body，形如 " + expected);
        }
    }

    private Long currentReviewerId() {
        LoginUser u = UserContext.getRequired();
        if ("admin".equals(u.getUsername()) || u.getUserId() == null || u.getUserId() == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "风控复审不得由管理员办理，请使用合规/风控岗账号");
        }
        return u.getUserId();
    }
}