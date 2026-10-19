package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysNumberRule;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 单据编号规则（/api/tenant/system/number/**）。 */
@RestController
@RequestMapping("/api/tenant/system/number")
@RequiredArgsConstructor
public class TenantNumberController {

    private final NumberRuleService numberRuleService;

    @RequiresPerm("system:number:list")
    @GetMapping("/rule/page")
    public Result<PageResult<SysNumberRule>> page(PageQuery query, String keyword) {
        return Result.success(numberRuleService.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("system:number:create")
    @OperLog(module = "SYSTEM_NUMBER", operType = "INSERT", desc = "新增编号规则")
    @PostMapping("/rule/create")
    public Result<Long> create(@RequestBody SysNumberRule rule) {
        return Result.success(numberRuleService.create(rule));
    }

    @RequiresPerm("system:number:update")
    @OperLog(module = "SYSTEM_NUMBER", operType = "UPDATE", desc = "更新编号规则")
    @PostMapping("/rule/update")
    public Result<Void> update(@RequestBody SysNumberRule rule) {
        numberRuleService.update(rule);
        return Result.success();
    }

    @RequiresPerm("system:number:update")
    @OperLog(module = "SYSTEM_NUMBER", operType = "UPDATE", desc = "规则启停")
    @PostMapping("/rule/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        numberRuleService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("system:number:delete")
    @OperLog(module = "SYSTEM_NUMBER", operType = "DELETE", desc = "删除编号规则")
    @PostMapping("/rule/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        numberRuleService.delete(id);
        return Result.success();
    }

    /** 取号（业务链路调用，返回下一个单据号） */
    @RequiresPerm("system:number:next")
    @OperLog(module = "SYSTEM_NUMBER", operType = "OTHER", desc = "取号")
    @PostMapping("/next")
    public Result<String> next(@RequestBody Map<String, String> body) {
        String ruleCode = body.get("ruleCode");
        return Result.success(numberRuleService.next(ruleCode));
    }
}
