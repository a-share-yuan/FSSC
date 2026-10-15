package com.fssc.arflow.controller;

import com.fssc.arflow.entity.ArMonthClose;
import com.fssc.arflow.service.ArMonthCloseService;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AR月末结账 OpenAPI（/api/tenant/ar-flow/month-close/**，蓝图月结管理 9.6）：
 * 应收月结：清接口行→扛数据→差异调整→过总账→数据核对→关闭账期；
 * 成本月结：扛数据→差异调整→过总账→数据核对→关闭账期。
 */
@RestController
@RequestMapping("/api/tenant/ar-flow/month-close")
@RequiredArgsConstructor
public class ArMonthCloseController {

    private final ArMonthCloseService monthCloseService;

    @RequiresPerm("arflow:monthclose:manage")
    @GetMapping("/list")
    public Result<List<ArMonthClose>> list(@RequestParam(required = false) String closeType,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String period) {
        return Result.success(monthCloseService.list(closeType, status, period));
    }

    /** 账期是否已关闭（蓝图规则6.5：关闭账期不允许再录入当月业务） */
    @RequiresPerm("arflow:monthclose:manage")
    @GetMapping("/period-closed")
    public Result<Boolean> periodClosed(@RequestParam(required = false) Long companyId,
                                        @RequestParam String period) {
        return Result.success(monthCloseService.isClosed(companyId, period));
    }

    /** 发起月结：body {companyId, period, closeType:RECEIVABLE|COST, remark} */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "发起AR月结")
    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/create")
    public Result<ArMonthClose> create(@RequestBody Map<String, Object> body) {
        Long companyId = body.get("companyId") == null ? null : Long.valueOf(String.valueOf(body.get("companyId")));
        return Result.success(monthCloseService.init(companyId,
                (String) body.get("period"), (String) body.get("closeType"), (String) body.get("remark")));
    }

    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/clear-interface")
    public Result<ArMonthClose> clearInterface(@PathVariable Long id) {
        return Result.success(monthCloseService.clearInterface(id));
    }

    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/pull-data")
    public Result<ArMonthClose> pullData(@PathVariable Long id) {
        return Result.success(monthCloseService.pullData(id));
    }

    /** 差异调整：body {note} */
    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/adjust")
    public Result<ArMonthClose> adjust(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(monthCloseService.adjust(id, body == null ? null : (String) body.get("note")));
    }

    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/post-gl")
    public Result<ArMonthClose> postGl(@PathVariable Long id) {
        return Result.success(monthCloseService.postGl(id));
    }

    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/reconcile")
    public Result<ArMonthClose> reconcile(@PathVariable Long id) {
        return Result.success(monthCloseService.reconcile(id));
    }

    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "AR月结关闭账期")
    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/close-period")
    public Result<ArMonthClose> closePeriod(@PathVariable Long id) {
        return Result.success(monthCloseService.closePeriod(id));
    }

    /** 反月结（重开账期）：body {reason}，原因必填留痕；解锁账期并恢复会计期间开账 */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "AR月结反月结重开账期")
    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/reopen")
    public Result<ArMonthClose> reopen(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(monthCloseService.unClose(id, (String) body.get("reason")));
    }

    /** R5-D-218 作废（终态 VOID）：body {reason}，原因必填留痕；未关闭账期的月结单可作废 */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "AR月结作废")
    @RequiresPerm("arflow:monthclose:manage")
    @PostMapping("/{id}/void")
    public Result<ArMonthClose> voidBill(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(monthCloseService.voidBill(id, body == null ? null : (String) body.get("reason")));
    }
}
