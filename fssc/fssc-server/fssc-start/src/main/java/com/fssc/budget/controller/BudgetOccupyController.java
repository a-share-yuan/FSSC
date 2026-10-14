// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.entity.BgOccupy;
import com.fssc.budget.mapper.BgOccupyMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预算占用操作（/api/tenant/budget/occupy/**）。
 * 报销单提交时占用、撤回时释放、审批通过后核销。流水只增不改。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/budget/occupy")
@RequiredArgsConstructor
@Validated
public class BudgetOccupyController {

    private final BudgetService budgetService;
    private final BgOccupyMapper bgOccupyMapper;
    private final SysUserMapper sysUserMapper;

    /** 占用流水查询（需求 3.10 查看占用明细，真分页）。2026-09-17：支持 budgetItemId 穿透过滤 */
    @RequiresPerm("budget:occupy:list")
    @GetMapping("/page")
    public Result<PageResult<BgOccupy>> page(PageQuery query,
                                             @RequestParam(required = false) Long budgetItemId,
                                             @RequestParam(required = false) String operType,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) String keyword) {
        com.baomidou.mybatisplus.core.metadata.IPage<BgOccupy> p = bgOccupyMapper.searchPage(
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(query.getPageNum(), query.getPageSize()),
                budgetItemId, operType, bizType, keyword);
        fillOperatorNames(p.getRecords());
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }

    /**
     * R5-D-269：分页结果由后端一次批查 sys_user，把裸 user_id 补成「姓名/工号」可读口径（与审计轨迹同法）。
     * 查不到或异常时留空——前端回退显示裸 id，绝不臆造姓名。
     */
    private void fillOperatorNames(List<BgOccupy> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (BgOccupy r : rows) {
            if (r.getOperUserId() != null) {
                ids.add(r.getOperUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            Map<Long, SysUser> userMap = new HashMap<>();
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                userMap.put(u.getId(), u);
            }
            for (BgOccupy r : rows) {
                SysUser u = userMap.get(r.getOperUserId());
                if (u == null) {
                    continue;
                }
                r.setOperUserName(u.getNickName() != null ? u.getNickName() : u.getUserName());
                r.setOperUserNo(u.getEmpNo());
            }
        } catch (Exception e) {
            log.warn("预算占用操作人姓名回填失败，降级显示用户ID", e);
        }
    }

    /**
     * 预算准入判定（D-DEC-424 乙）：不占用、不打挂提交——超出只回结论与金额（available/over/overAmount），
     * 提交继续走 {@code /occupy} 与单据占用腿，那里才按 bg_scheme.control_type 档位执行扣减闸。
     */
    @RequiresPerm("budget:occupy:check")
    @PostMapping("/check")
    public Result<BudgetService.OccupyCheckResult> check(@Validated @RequestBody OccupyReq req) {
        return Result.success(budgetService.checkOccupyResult(req.getBudgetItemId(), req.getAmount()));
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "占用预算")
    @PostMapping("/occupy")
    public Result<Void> occupy(@Validated @RequestBody OccupyReq req) {
        budgetService.occupy(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "释放预算")
    @PostMapping("/release")
    public Result<Void> release(@Validated @RequestBody OccupyReq req) {
        budgetService.release(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "核销预算")
    @PostMapping("/write-off")
    public Result<Void> writeOff(@Validated @RequestBody OccupyReq req) {
        budgetService.writeOff(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    @Data
    public static class OccupyReq {
        @NotNull(message = "budgetItemId 必填")
        private Long budgetItemId;
        @NotNull(message = "amount 必填")
        private BigDecimal amount;
        @NotBlank(message = "bizType 必填")
        private String bizType;
        private Long bizId;
        private String bizNo;
    }
}
