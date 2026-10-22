package com.fssc.treasury.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.treasury.entity.TrFundPlan;
import com.fssc.treasury.service.TrFundPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 资金计划管理（/api/tenant/treasury/fund-plan/**）
 * <p>D-DEC-422 扩展（台账 §4.5 观察「TrFundPlanController 零 {@code @OperLog}」）：
 * 写端点补操作审计留痕，读端点按全项目惯例不打点。
 */
@RestController
@RequestMapping("/api/tenant/treasury/fund-plan")
@RequiredArgsConstructor
public class TrFundPlanController {

    private final TrFundPlanService service;

    @RequiresPerm("treasury:fundplan:list")
    @GetMapping("/page")
    public Result<PageResult<TrFundPlan>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               String period, String status) {
        return Result.success(service.page(pageNum, pageSize, period, status));
    }

    @RequiresPerm("treasury:fundplan:list")
    @GetMapping("/{id}")
    public Result<TrFundPlan> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @RequiresPerm("treasury:fundplan:create")
    @OperLog(module = "TREASURY", operType = "INSERT", desc = "资金计划-编制计划")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody TrFundPlan plan) {
        return Result.success(service.create(plan));
    }

    @RequiresPerm("treasury:fundplan:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金计划-选定计划")
    @PostMapping("/{id}/select")
    public Result<Void> selectPlan(@PathVariable Long id) {
        service.selectPlan(id);
        return Result.success();
    }

    /** 驳回计划编辑重提：REJECTED → DRAFT 并重新发起 TR_PLAN 审批流 */
    @RequiresPerm("treasury:fundplan:create")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金计划-驳回重提")
    @PostMapping("/{id}/resubmit")
    public Result<Void> resubmit(@PathVariable Long id) {
        service.resubmit(id);
        return Result.success();
    }

    /**
     * D-G5-05：作废资金计划（留痕，不物理删除；联动终止在办流程）。
     *
     * <p>此前没有作废/删除出口，跑测清理重复草稿只能直接删表 ⇒ 留下 RUNNING 僵尸流程实例
     * 与无 assignee 的僵尸待办。作废走标准收尾入口 {@code cancelRunningInstancesByBiz}。</p>
     */
    @RequiresPerm("treasury:fundplan:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金计划-作废")
    @PostMapping("/{id}/void")
    public Result<Void> voidPlan(@PathVariable Long id,
                                 @RequestParam(required = false) String reason) {
        service.voidPlan(id, reason);
        return Result.success();
    }

    @RequiresPerm("treasury:fundplan:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金计划-实际数归集")
    @PostMapping("/{id}/combine-actual")
    public Result<Void> combineActual(@PathVariable Long id,
                                      @RequestParam(required = false) BigDecimal incomeActual,
                                      @RequestParam(required = false) BigDecimal outcomeActual) {
        service.combineActual(id, incomeActual, outcomeActual);
        return Result.success();
    }

    /** 资金计划调整（T1）：追加调整额并发起 TR_PLAN_ADJUST 审批流 */
    @RequiresPerm("treasury:fundplan:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金计划-调整并发起审批")
    @PostMapping("/{id}/adjust")
    public Result<Void> adjust(@PathVariable Long id,
                               @RequestParam(required = false) BigDecimal incomeAdjust,
                               @RequestParam(required = false) BigDecimal outcomeAdjust) {
        service.adjust(id, incomeAdjust, outcomeAdjust);
        return Result.success();
    }
}