package com.fssc.expense.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.service.ColdArchiveService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 历史（冷）单据查询（/api/tenant/expense/bill/cold/**）：
 * 冷表不在租户拦截器管辖，service 层走 JdbcTemplate 手工带 tenant_id。
 * 权限：list 走 exp:bill:list（与热单据列表一致）；dry-run 走系统配置类权限（system:config:list）。
 */
@RestController
@RequestMapping("/api/tenant/expense/bill/cold")
@RequiredArgsConstructor
public class ColdBillController {

    private final ColdArchiveService coldArchiveService;

    @RequiresPerm("exp:bill:list")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        @RequestParam(required = false) String billNo,
                                                        @RequestParam(required = false) Long applyUserId,
                                                        @RequestParam(required = false) String beginDate,
                                                        @RequestParam(required = false) String endDate) {
        return Result.success(coldArchiveService.coldPage(pageNum, pageSize, billNo, applyUserId, beginDate, endDate));
    }

    @RequiresPerm("exp:bill:list")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(coldArchiveService.coldDetail(id));
    }

    /** 归档预演（跨租户 SELECT COUNT，不搬数据）；返回 {total, tenants:[{tenantId,enabled,coldMonths,count}]} */
    @RequiresPerm("system:config:list")
    @OperLog(module = "EXPENSE_BILL", operType = "QUERY", desc = "冷热归档预演")
    @PostMapping("/dry-run")
    public Result<Map<String, Object>> dryRun() {
        return Result.success(coldArchiveService.dryRunCount());
    }
}
