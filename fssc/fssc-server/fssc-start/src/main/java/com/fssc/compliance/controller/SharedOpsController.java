package com.fssc.compliance.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.compliance.entity.EmployeeCredit;
import com.fssc.compliance.entity.OpSlaBreach;
import com.fssc.compliance.entity.OpSlaConfig;
import com.fssc.compliance.service.EmployeeCreditService;
import com.fssc.compliance.service.QualityCheckService;
import com.fssc.compliance.service.SatisfactionService;
import com.fssc.compliance.service.SlaMonitorService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.compliance.entity.SatisfactionSurvey;
import com.fssc.sharing.entity.OpQuality;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 共享运营管理统一入口（P1-5 共享运营实质化）。
 * <p>
 * 四大模块：
 * 1. 员工信用（/api/shared-ops/credit/*）
 * 2. SLA 监控（/api/shared-ops/sla/*）
 * 3. 质检管理（/api/shared-ops/quality/*）
 * 4. 满意度管理（/api/shared-ops/satisfaction/*）
 */
@RestController
@RequestMapping("/api/shared-ops")
@RequiredArgsConstructor
public class SharedOpsController {

    private final EmployeeCreditService creditService;
    private final SlaMonitorService slaMonitorService;
    private final QualityCheckService qualityCheckService;
    private final SatisfactionService satisfactionService;

    // ================ 员工信用 ================

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:credit:view")
    @GetMapping("/credit/{userId}")
    public Result<EmployeeCredit> getCredit(@PathVariable Long userId) {
        return Result.success(creditService.getOrCreate(userId, null));
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:credit:adjust")
    @OperLog(module = "EMPLOYEE_CREDIT", operType = "UPDATE", desc = "手动调整信用分")
    @PutMapping("/credit/{userId}/adjust")
    public Result<Void> adjustCredit(@PathVariable Long userId,
                                     @RequestParam int delta,
                                     @RequestParam String reason) {
        creditService.adjustManually(userId, delta, reason);
        return Result.success();
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:credit:view")
    @GetMapping("/credit/{userId}/can-auto-approve")
    public Result<Boolean> canAutoApprove(@PathVariable Long userId,
                                           @RequestParam java.math.BigDecimal amount) {
        return Result.success(creditService.canAutoApprove(userId, amount));
    }

    // ================ SLA 监控 ================

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:sla:list")
    @GetMapping("/sla/config/list")
    public Result<java.util.List<OpSlaConfig>> slaConfigList() {
        return Result.success(slaMonitorService.listAllConfigs());
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:sla:check")
    @OperLog(module = "SLA_MONITOR", operType = "OTHER", desc = "SLA巡检")
    @PostMapping("/sla/check")
    public Result<Void> slaCheck() {
        slaMonitorService.slaCheck();
        return Result.success();
    }

    // ================ 质检管理 ================

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:quality:list")
    @GetMapping("/quality/page")
    public Result<PageResult<OpQuality>> qualityPage(
            PageQuery query,
            @RequestParam(required = false) Long billId,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String result) {
        return Result.success(qualityCheckService.page(query.getPageNum(), query.getPageSize(), billId, checkType, result));
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:quality:create")
    @OperLog(module = "QUALITY_CHECK", operType = "INSERT", desc = "创建质检记录")
    @PostMapping("/quality")
    public Result<Long> createQuality(@RequestBody OpQuality record) {
        return Result.success(qualityCheckService.create(record));
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:quality:list")
    @GetMapping("/quality/summary")
    public Result<QualityCheckService.QualitySummary> qualitySummary() {
        return Result.success(qualityCheckService.summary());
    }

    // ================ 满意度管理 ================

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:satisfaction:list")
    @GetMapping("/satisfaction/page")
    public Result<PageResult<SatisfactionSurvey>> satisfactionPage(
            PageQuery query,
            @RequestParam(required = false) Long billId,
            @RequestParam(required = false) String serviceCode) {
        return Result.success(satisfactionService.page(query.getPageNum(), query.getPageSize(), billId, serviceCode));
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:satisfaction:create")
    @OperLog(module = "SATISFACTION", operType = "INSERT", desc = "创建满意度评价")
    @PostMapping("/satisfaction")
    public Result<Long> createSatisfaction(@RequestBody SatisfactionSurvey record) {
        return Result.success(satisfactionService.create(record));
    }

    @RequiresFeature("FSSC_SHARED")
    @RequiresPerm("gl:satisfaction:list")
    @GetMapping("/satisfaction/summary")
    public Result<Map<String, Object>> satisfactionSummary() {
        return Result.success(satisfactionService.summary());
    }
}
