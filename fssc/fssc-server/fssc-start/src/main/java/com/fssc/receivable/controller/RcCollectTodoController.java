package com.fssc.receivable.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.receivable.entity.RcCollectTodo;
import com.fssc.receivable.service.RcCollectTodoService;
import com.fssc.receivable.service.RcReceivableService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 催收待办管理（/api/tenant/collect-todo/**）：逾期扫描生成待办、催收解决 */
@RestController
@RequestMapping("/api/tenant/collect-todo")
@RequiredArgsConstructor
public class RcCollectTodoController {

    private final RcCollectTodoService service;

    @RequiresPerm("receivable:list")
    @GetMapping("/list")
    public Result<List<RcCollectTodo>> list() {
        return Result.success(service.list());
    }

    /**
     * 扫描逾期应收生成催收待办。
     * <p>在逾期天数阈值（days）之外补两个可选账期入参——
     * {@code asOfDate}（yyyy-MM-dd 基准日）或 {@code period}（yyyyMM／yyyy-MM，按期末日）；
     * 都不传＝系统当前日，与原先逐字等价（前端旧调用 {@code ?days=30} 零改动仍可用）。
     */
    @RequiresPerm("receivable:overdue")
    @OperLog(module = "COLLECT_TODO", operType = "INSERT", desc = "扫描逾期应收生成催收待办")
    @PostMapping("/createOverdue")
    public Result<Integer> createOverdueTodos(@RequestParam(defaultValue = "0") int days,
                                              @RequestParam(required = false) String asOfDate,
                                              @RequestParam(required = false) String period) {
        return Result.success(service.createOverdueTodos(days,
                RcReceivableService.resolveAsOfDate(asOfDate, period)));
    }

    /**
     * 解决催收待办。
     * <p>乙档：同一登录人在这一条催收任务上占多岗（发起／起单／领取承办／办结）时，
     * data 里回非阻断复核提示位（{@code multiRole}／{@code roles}／{@code warning}）供界面显示；
     * 办结照常完成，不拦、不改候选取人。
     */
    @RequiresPerm("receivable:update")
    @OperLog(module = "COLLECT_TODO", operType = "UPDATE", desc = "解决催收待办")
    @PostMapping("/{id}/solve")
    public Result<Map<String, Object>> solve(@PathVariable Long id) {
        return Result.success(service.solve(id));
    }
}