package com.fssc.compliance.controller;

import com.fssc.common.core.Result;
import com.fssc.workflow.entity.WfFlowConfig;
import com.fssc.compliance.service.ApprovalRuleService;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/compliance/approval-rule")
@RequiredArgsConstructor
public class ApprovalRuleController {

    private final ApprovalRuleService approvalRuleService;

    @RequiresPerm("compliance:approvalrule:list")
    @GetMapping("/list")
    public Result<List<WfFlowConfig>> list(@RequestParam(required = false) Long policyId) {
        Long effectivePolicyId = policyId;
        if (effectivePolicyId == null) {
            return Result.success(List.of());
        }
        return Result.success(approvalRuleService.listByPolicy(effectivePolicyId));
    }

    @RequiresPerm("compliance:approvalrule:create")
    @OperLog(module = "APPROVAL_RULE", operType = "INSERT", desc = "新增审批规则")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody WfFlowConfig rule) {
        rule.setCfgType("APPROVAL_RULE");
        return Result.success(approvalRuleService.createRule(rule));
    }

    @RequiresPerm("compliance:approvalrule:update")
    @OperLog(module = "APPROVAL_RULE", operType = "UPDATE", desc = "更新审批规则")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody WfFlowConfig rule) {
        approvalRuleService.updateRule(rule);
        return Result.success();
    }

    @RequiresPerm("compliance:approvalrule:delete")
    @OperLog(module = "APPROVAL_RULE", operType = "DELETE", desc = "删除审批规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        approvalRuleService.deleteRule(id);
        return Result.success();
    }

    @RequiresPerm("compliance:approvalrule:list")
    @GetMapping("/match")
    public Result<WfFlowConfig> match(@RequestParam(required = false) String expenseType,
                                            @RequestParam(required = false) BigDecimal amount,
                                            @RequestParam(required = false) Long orgId) {
        return Result.success(approvalRuleService.getMatchedRule(expenseType, amount, orgId));
    }
}