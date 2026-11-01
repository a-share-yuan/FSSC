// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.controller;

import com.fssc.apflow.entity.ApDispatchRule;
import com.fssc.apflow.entity.ApServiceRequest;
import com.fssc.apflow.service.ApPlanExcelService;
import com.fssc.apflow.service.ApRequestService;
import com.fssc.arflow.service.ArReceiptExcelService;
import com.fssc.common.core.Result;
import com.fssc.common.core.PageResult;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.Map;

import java.util.List;
import java.util.Map;

/**
 * AP付款服务申请 OpenAPI（/api/tenant/ap-request/**）：
 * 五类付款服务申请统一状态机（蓝图 SCC_AP_15~19 + 派工规则 R-01~R-05）。
 */
@RestController
@RequestMapping("/api/tenant/ap-request")
@RequiredArgsConstructor
public class ApRequestController {

    private final ApRequestService service;
    private final ApPlanExcelService planExcelService;
    private final ArReceiptExcelService receiptExcelService;

    @RequiresPerm("apflow:request:list")
    @GetMapping("/page")
    public Result<PageResult<ApServiceRequest>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "20") long pageSize,
                                                     @RequestParam(required = false) String reqType,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String vendorName) {
        return Result.success(service.page(pageNum, pageSize, reqType, status, vendorName));
    }

    @RequiresPerm("apflow:request:list")
    @GetMapping("/{id}")
    public Result<ApServiceRequest> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    /** 创建（OA自动触发数据/共享手工录入） */
    @OperLog(module = "AP_REQUEST", operType = "ADD", desc = "创建付款服务申请单")
    @RequiresPerm("apflow:request:create")
    @PostMapping("/create")
    public Result<ApServiceRequest> create(@RequestBody ApServiceRequest r) {
        return Result.success(service.create(r));
    }

    /** OA作废后重拉覆盖（单号不变 R-10） */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "重拉覆盖服务申请单")
    @RequiresPerm("apflow:request:create")
    @PostMapping("/{id}/repull")
    public Result<ApServiceRequest> repull(@PathVariable Long id, @RequestBody ApServiceRequest fresh) {
        return Result.success(service.repull(id, fresh));
    }

    /** 自动派工（按付款方法 R-01/R-02） */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单自动派工")
    @RequiresPerm("apflow:request:dispatch")
    @PostMapping("/{id}/dispatch")
    public Result<ApServiceRequest> dispatch(@PathVariable Long id) {
        return Result.success(service.dispatch(id));
    }

    /** 选单（多负责人池 R-02）：body {user} */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单选单")
    @RequiresPerm("apflow:request:dispatch")
    @PostMapping("/{id}/claim")
    public Result<ApServiceRequest> claim(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.claim(id, String.valueOf(body.get("user"))));
    }

    /** 运营岗手工派工/转单（R-05）：body {user} */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单手工派工/转单")
    @RequiresPerm("apflow:request:dispatch")
    @PostMapping("/{id}/assign")
    public Result<ApServiceRequest> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.assign(id, String.valueOf(body.get("user"))));
    }

    /** 确认服务请求（可修改付款类型/银行账户/付款方法/折扣额/到期日/付款额；修改付款方式转组长 R-03） */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "确认服务请求")
    @RequiresPerm("apflow:request:confirm")
    @PostMapping("/{id}/confirm")
    public Result<ApServiceRequest> confirm(@PathVariable Long id, @RequestBody ApServiceRequest patch) {
        return Result.success(service.confirm(id, patch));
    }

    /** 组长审核（R-04）：body {pass,note}；审核人一律取当前登录人，不接受客户端传入 */
    @OperLog(module = "AP_REQUEST", operType = "APPROVE", desc = "服务申请单组长审核")
    @RequiresPerm("apflow:request:review")
    @PostMapping("/{id}/group-review")
    public Result<ApServiceRequest> groupReview(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean pass = Boolean.parseBoolean(String.valueOf(body.get("pass")));
        return Result.success(service.groupReview(id, pass, (String) body.get("note")));
    }

    /** 过账（ERP付款凭证） */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单过账")
    @RequiresPerm("apflow:request:post")
    @PostMapping("/{id}/post")
    public Result<ApServiceRequest> post(@PathVariable Long id) {
        return Result.success(service.post(id));
    }

    /** 资金付款 */
    @OperLog(module = "AP_REQUEST", operType = "PAY", desc = "服务申请单资金付款")
    @RequiresPerm("apflow:request:pay")
    @PostMapping("/{id}/pay")
    public Result<ApServiceRequest> pay(@PathVariable Long id) {
        return Result.success(service.pay(id));
    }

    /** 作废（终止付款）：body {reason} */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单作废")
    @RequiresPerm("apflow:request:void")
    @PostMapping("/{id}/void")
    public Result<ApServiceRequest> voidRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.voidRequest(id, (String) body.get("reason")));
    }

    /** 完成 */
    @OperLog(module = "AP_REQUEST", operType = "UPDATE", desc = "服务申请单完成")
    @RequiresPerm("apflow:request:void")
    @PostMapping("/{id}/close")
    public Result<ApServiceRequest> close(@PathVariable Long id) {
        return Result.success(service.close(id));
    }

    // 派工规则配置

    @RequiresPerm("apflow:request:list")
    @GetMapping("/dispatch-rules")
    public Result<List<ApDispatchRule>> rules() {
        return Result.success(service.listRules());
    }

    @OperLog(module = "AP_REQUEST", operType = "ADD", desc = "保存付款派工规则")
    @RequiresPerm("apflow:request:dispatch")
    @PostMapping("/dispatch-rules")
    public Result<ApDispatchRule> saveRule(@RequestBody ApDispatchRule rule) {
        return Result.success(service.saveRule(rule));
    }

    /**
     * 付款计划表 Excel 导入（任务3.2，蓝图附录四 A.4.1，真实 POI 解析）：
     * 每行生成一张计划内付款服务申请单；返回 {created, errors, bills}。
     */
    @OperLog(module = "AP_REQUEST", operType = "ADD", desc = "付款计划表Excel导入")
    @PostMapping(value = "/import-plan", consumes = {"multipart/form-data"})
    public Result<Map<String, Object>> importPlan(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false, defaultValue = "PLAN_PAYMENT") String reqType,
                                                  @RequestParam(required = false) Long companyId) {
        ApPlanExcelService.ParseResult parsed = planExcelService.parse(file);
        List<Map<String, Object>> bills = new ArrayList<>();
        for (Map<String, Object> row : parsed.rows()) {
            ApServiceRequest r = new ApServiceRequest();
            r.setReqType(reqType);
            r.setCompanyId(companyId == null ? 1L : companyId);
            r.setInvoiceNo(String.valueOf(row.get("invoiceNo")));
            r.setSbu((String) row.get("sbu"));
            r.setVendorName((String) row.get("vendorName"));
            r.setPaymentWay((String) row.get("paymentWay"));
            r.setBankNo((String) row.get("bankNo"));
            Object pd = row.get("paymentDate");
            r.setPaymentDate(pd == null ? LocalDateTime.now()
                    : LocalDateTime.parse(pd + "T00:00:00"));
            Object rebate = row.get("rebateAmount");
            r.setRebateAmount(rebate == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(rebate)));
            Object exp = row.get("expireDate");
            r.setExpireDate(exp == null ? null : LocalDate.parse(String.valueOf(exp)));
            Object pa = row.get("paymentAmount");
            r.setPaymentAmount(pa == null ? null : new BigDecimal(String.valueOf(pa)));
            r.setPoNo((String) row.get("poNo"));
            r.setAmount(pa == null ? BigDecimal.ONE : new BigDecimal(String.valueOf(pa)));
            r.setRemark((String) row.get("remark"));
            try {
                ApServiceRequest created = service.create(r);
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("billNo", created.getBillNo());
                ok.put("excelRow", row.get("_excelRow"));
                bills.add(ok);
            } catch (Exception e) {
                // 单行建单失败记入 errors 随结果返回，不中断整批导入
                parsed.errors().add("第" + row.get("_excelRow") + "行建单失败：" + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", bills.size());
        out.put("bills", bills);
        out.put("errors", parsed.errors());
        return Result.success(out);
    }
}
