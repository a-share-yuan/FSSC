package com.fssc.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.entity.BgAdjust;
import com.fssc.budget.entity.BgAdjustLine;
import com.fssc.budget.mapper.BgAdjustMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 预算调整单（/api/tenant/budget/adjust/**）：增拨/调减/调剂 */
@RestController
@RequestMapping("/api/tenant/budget/adjust")
@RequiredArgsConstructor
public class BudgetAdjustController {

    private final BudgetService budgetService;
    private final BgAdjustMapper bgAdjustMapper;

    @RequiresPerm("budget:adjust:list")
    @GetMapping("/page")
    public Result<PageResult<BgAdjust>> page(PageQuery query, String keyword) {
        LambdaQueryWrapper<BgAdjust> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(BgAdjust::getAdjustNo, keyword);
        }
        qw.orderByDesc(BgAdjust::getId);
        Page<BgAdjust> p = bgAdjustMapper.selectPage(Page.of(query.getPageNum(), query.getPageSize()), qw);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "新建预算调整单")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody AdjustReq req) {
        return Result.success(budgetService.adjustCreate(req.getAdjust(), req.getLines()));
    }

    @RequiresPerm("budget:adjust:list")
    @GetMapping("/lines/{adjustId}")
    public Result<List<BgAdjustLine>> lines(@PathVariable Long adjustId) {
        return Result.success(budgetService.adjustLines(adjustId));
    }

    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "编辑预算调整单（草稿）")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody AdjustReq req) {
        budgetService.adjustUpdate(req.getAdjust(), req.getLines());
        return Result.success();
    }

    @RequiresPerm("budget:adjust:approve")
    @OperLog(module = "BUDGET", operType = "APPROVE", desc = "审批预算调整单")
    @PostMapping("/approve/{id}")
    public Result<Void> approve(@PathVariable Long id) {
        budgetService.adjustApprove(id);
        return Result.success();
    }

    /**
     * 提交审批：DRAFT → APPROVING，发起 BG_ADJUST 流。
     * 路由行 wf_process_config 早已在盘（BG_ADJUST → BG_SCHEME），此前代码从不发起。
     */
    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "提交预算调整审批")
    @PostMapping("/submit/{id}")
    public Result<Void> submit(@PathVariable Long id) {
        budgetService.adjustSubmit(id);
        return Result.success();
    }

    /** 撤回审批中的调整单：流程撤回 + 回退草稿 */
    @RequiresPerm("budget:adjust:create")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "撤回预算调整审批")
    @PostMapping("/withdraw/{id}")
    public Result<Void> withdraw(@PathVariable Long id) {
        budgetService.adjustWithdraw(id);
        return Result.success();
    }

    @Data
    public static class AdjustReq {
        private BgAdjust adjust;
        private List<BgAdjustLine> lines;
    }
}
