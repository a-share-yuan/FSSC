package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.service.ExpensePolicyStandardService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/compliance/policy-standard")
@RequiredArgsConstructor
public class ExpensePolicyStandardController {

    private final ExpensePolicyStandardService expensePolicyStandardService;

    /**
     *
     * <p>同类系统的费用标准<b>没有 Version 表</b>——版本靠标准主表上的
     * {@code effective_date} 表达，查询时按期间取「当期已生效且最新」的一条。
     * 本库的 {@code expense_policy_standard} 已有 {@code effective_date}/{@code std_status} 两列，
     * 但缺「按期间选版本」的入口，导致手册 G7/POL 幕的「政策→标准→版本」第三层走不通。
     *
     * <p>因此本接口加一个<b>可选</b>参数 {@code period}（yyyyMM 或 yyyy-MM）：
     * <ul>
     *   <li>不传 ⇒ <b>行为与原来完全一致</b>（返回该政策全部标准行，兼容既有调用方）；</li>
     *   <li>传了 ⇒ 只返回 {@code effective_date <= 期末日} 且（{@code std_status} 为空或非 DISABLED）
     *       的行；同{@code standard_key} 多版本时取 {@code effective_date} 最大的一条。</li>
     * </ul>
     * 这样既补上了第三层入口，又不改动任何既有语义。
     */
    @RequiresPerm("compliance:policystandard:list")
    @GetMapping("/list")
    public Result<List<ExpensePolicyStandard>> list(@RequestParam Long policyId,
                                                   @RequestParam(required = false) String period) {
        List<ExpensePolicyStandard> all = expensePolicyStandardService.listByPolicy(policyId);
        if (period == null || period.isBlank()) {
            return Result.success(all);   // 兼容：不传 period = 原行为
        }
        java.time.LocalDate end = parsePeriodEnd(period);
        if (end == null) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "period 格式应为 yyyyMM 或 yyyy-MM，收到：" + period);
        }
        // ① 过滤：已生效且未废止
        java.util.List<ExpensePolicyStandard> alive = new java.util.ArrayList<>();
        for (ExpensePolicyStandard s : all) {
            // std_status：1-启用 0-停用（Integer，不是字符串枚举）
            Integer st = s.getStdStatus();
            if (st != null && st == 0) {
                continue;   // 停用版本不参与当期取数
            }
            java.time.LocalDate eff = s.getEffectiveDate();
            if (eff != null && eff.isAfter(end)) {
                continue;   // 该版本尚未生效
            }
            alive.add(s);
        }
        // ② 同 standard_key 取 effective_date 最大者（缺省视为最早，保证老数据不被顶掉）
        java.util.Map<String, ExpensePolicyStandard> newest = new java.util.LinkedHashMap<>();
        for (ExpensePolicyStandard s : alive) {
            String k = s.getStandardKey();
            ExpensePolicyStandard cur = newest.get(k);
            java.time.LocalDate d = s.getEffectiveDate();
            if (cur == null || compareEff(d, cur.getEffectiveDate()) > 0) {
                newest.put(k, s);
            }
        }
        return Result.success(new java.util.ArrayList<>(newest.values()));
    }

    private static int compareEff(java.time.LocalDate a, java.time.LocalDate b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;   // 无生效日视为最早
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /** 解析 yyyyMM / yyyy-MM 为该期间最后一天（通行口径：期间内已生效即适用）。 */
    private static java.time.LocalDate parsePeriodEnd(String period) {
        String p = period.trim().replace("-", "");
        if (p.length() != 6) {
            return null;
        }
        try {
            int y = Integer.parseInt(p.substring(0, 4));
            int m = Integer.parseInt(p.substring(4, 6));
            if (m < 1 || m > 12) {
                return null;
            }
            return java.time.LocalDate.of(y, m, 1).withDayOfMonth(
                    java.time.YearMonth.of(y, m).lengthOfMonth());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @RequiresPerm("compliance:policystandard:list")
    @GetMapping("/{id}")
    public Result<ExpensePolicyStandard> getById(@PathVariable Long id) {
        return Result.success(expensePolicyStandardService.getById(id));
    }

    @RequiresPerm("compliance:policystandard:create")
    @OperLog(module = "POLICY_STANDARD", operType = "INSERT", desc = "新增费用政策标准")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExpensePolicyStandard standard) {
        return Result.success(expensePolicyStandardService.create(standard));
    }

    @RequiresPerm("compliance:policystandard:update")
    @OperLog(module = "POLICY_STANDARD", operType = "UPDATE", desc = "更新费用政策标准")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ExpensePolicyStandard standard) {
        expensePolicyStandardService.update(standard);
        return Result.success();
    }

    @RequiresPerm("compliance:policystandard:delete")
    @OperLog(module = "POLICY_STANDARD", operType = "DELETE", desc = "删除费用政策标准")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expensePolicyStandardService.delete(id);
        return Result.success();
    }
}
