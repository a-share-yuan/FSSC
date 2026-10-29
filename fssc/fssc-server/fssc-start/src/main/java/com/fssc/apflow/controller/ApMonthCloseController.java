// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.controller;

import com.fssc.apflow.entity.ApMonthClose;
import com.fssc.apflow.service.ApMonthCloseService;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.log.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** AP月末关账 OpenAPI（/api/tenant/ap-month-close/**）：SCC_AP_20，含协同冻结（打通点7）。 */
@RestController
@RequestMapping("/api/tenant/ap-month-close")
@RequiredArgsConstructor
public class ApMonthCloseController {

    private final ApMonthCloseService service;

    @RequiresPerm("apflow:close:list")
    @GetMapping("/list")
    public Result<List<ApMonthClose>> list(@RequestParam(required = false) Long companyId,
                                           @RequestParam(required = false) String status) {
        return Result.success(service.list(companyId, status));
    }

    @RequiresPerm("apflow:close:list")
    @GetMapping("/{id}")
    public Result<ApMonthClose> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    /** 发起关账（协同冻结） */
    @OperLog(module = "AP_MONTH_CLOSE", operType = "ADD", desc = "发起月末关账并冻结协同")
    @RequiresPerm("apflow:close:create")
    @PostMapping("/create")
    public Result<ApMonthClose> create(@RequestBody Map<String, Object> body) {
        return Result.success(service.init(body.get("companyId") == null ? null
                        : Long.valueOf(String.valueOf(body.get("companyId"))),
                (String) body.get("period"), (String) body.get("groupLeader"), (String) body.get("remark")));
    }

    /** 组长派工：body {members} */
    @RequiresPerm("apflow:close:dispatch")
    @PostMapping("/{id}/dispatch")
    public Result<ApMonthClose> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.dispatch(id, (String) body.get("members")));
    }

    /** 维护检查项：body {code,itemStatus(OK/REJECTED/SUSPENDED/PENDING),note,handler} */
    @RequiresPerm("apflow:close:item")
    @OperLog(module = "AP_MONTH_CLOSE", operType = "UPDATE", desc = "维护关账检查项")
    @PostMapping("/{id}/complete-item")
    public Result<ApMonthClose> completeItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.completeItem(id, (String) body.get("code"),
                (String) body.get("itemStatus"),
                (String) body.get("note"), (String) body.get("handler")));
    }

    /** 前置自动检查：自动跑 未审凭证/未匹配三单/未达账项 数，人工仅确认异常项 */
    @RequiresPerm("apflow:close:item")
    @OperLog(module = "AP_MONTH_CLOSE", operType = "UPDATE", desc = "关账前置自动检查")
    @PostMapping("/{id}/auto-check")
    public Result<Map<String, Object>> autoCheck(@PathVariable Long id) {
        return Result.success(service.autoCheck(id));
    }

    /** ERP关账（恢复协同 + 联动会计期间结账） */
    @RequiresPerm("apflow:close:close")
    @OperLog(module = "AP_MONTH_CLOSE", operType = "UPDATE", desc = "总账关账并联动结账期间")
    @PostMapping("/{id}/close-period")
    public Result<ApMonthClose> closePeriod(@PathVariable Long id) {
        return Result.success(service.closePeriod(id));
    }

    /** 反关账（重开期间）：body {reason}，原因必填记录；联动 md_period 恢复开账 */
    @RequiresPerm("apflow:close:reopen")
    @OperLog(module = "AP_MONTH_CLOSE", operType = "UPDATE", desc = "反关账重开期间")
    @PostMapping("/{id}/reopen")
    public Result<ApMonthClose> reopen(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.unClose(id, (String) body.get("reason")));
    }

    /** 作废（最终状态 VOID）：body {reason}，原因必填记录；解冻协同、行止不删 */
    @RequiresPerm("apflow:close:reopen")
    @OperLog(module = "AP_MONTH_CLOSE", operType = "UPDATE", desc = "作废关账单")
    @PostMapping("/{id}/void")
    public Result<ApMonthClose> voidBill(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.voidBill(id, body == null ? null : (String) body.get("reason")));
    }

    /** 协同冻结判定（供通道方拦截）：params companyId,period */
    @RequiresPerm("apflow:close:list")
    @GetMapping("/frozen")
    public Result<Boolean> frozen(@RequestParam(required = false) Long companyId,
                                  @RequestParam(required = false) String period) {
        return Result.success(service.isFrozen(companyId, period));
    }
}
