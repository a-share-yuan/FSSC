package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.compliance.entity.MdComplianceRule;
import com.fssc.compliance.service.MdComplianceRuleService;
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

/**
 * 合规规则配置（P0-10 md_compliance_rule）。
 *
 * <p>管理四类合规规则：重复发票 DUPLICATE_INVOICE / 费用项目不一致 EXPENSE_MISMATCH /
 * 超标 OVER_STANDARD / 黑名单 BLACKLIST。规则可 online 启停，供智能审单引擎引用。</p>
 */
@RestController
@RequestMapping("/api/tenant/compliance/rule")
@RequiredArgsConstructor
public class MdComplianceRuleController {

    private final MdComplianceRuleService ruleService;

    @RequiresPerm("compliance:rule:list")
    @GetMapping("/list")
    public Result<List<MdComplianceRule>> list(@RequestParam(required = false) String ruleType,
                                               @RequestParam(required = false) String status) {
        return Result.success(ruleService.list(ruleType, status));
    }

    @RequiresPerm("compliance:rule:list")
    @GetMapping("/{id}")
    public Result<MdComplianceRule> get(@PathVariable Long id) {
        return Result.success(ruleService.getById(id));
    }

    @RequiresPerm("compliance:rule:edit")
    @PostMapping("/create")
    @OperLog(module = "COMPLIANCE", operType = "INSERT", desc = "创建合规规则")
    public Result<Long> create(@RequestBody MdComplianceRule rule) {
        return Result.success(ruleService.create(rule));
    }

    @RequiresPerm("compliance:rule:edit")
    @PostMapping("/update")
    @OperLog(module = "COMPLIANCE", operType = "UPDATE", desc = "更新合规规则")
    public Result<Void> update(@RequestBody MdComplianceRule rule) {
        ruleService.update(rule);
        return Result.success();
    }

    @RequiresPerm("compliance:rule:edit")
    @PostMapping("/{id}/toggle")
    @OperLog(module = "COMPLIANCE", operType = "UPDATE", desc = "启停合规规则")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enable) {
        ruleService.toggle(id, enable);
        return Result.success();
    }

    @RequiresPerm("compliance:rule:edit")
    @DeleteMapping("/{id}")
    @OperLog(module = "COMPLIANCE", operType = "DELETE", desc = "删除合规规则")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success();
    }
}