// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.TaxProvisionRule;
import com.fssc.glex.service.TaxProvisionRuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 税金计提规则配置。
 *
 * <p>权限复用既有码：读 {@code gl:taxprov:list}、写 {@code gl:taxprov:provision}
 * ——「谁有权计提，谁有权配计提规则」。不新造权限码，免去一次权限点登记与角色赋权迁移。
 */
@RestController
@RequestMapping("/api/tenant/glex/tax-rule")
@RequiredArgsConstructor
public class TaxProvisionRuleController {

    private final TaxProvisionRuleService service;

    @RequiresPerm("gl:taxprov:list")
    @GetMapping("/list")
    public Result<List<TaxProvisionRule>> list() {
        return Result.success(service.list());
    }

    @RequiresPerm("gl:taxprov:list")
    @GetMapping("/effective")
    public Result<List<TaxProvisionRule>> effective() {
        return Result.success(service.effectiveRules());
    }

    @RequiresPerm("gl:taxprov:provision")
    @OperLog(module = "TAX_PROVISION_RULE", operType = "INSERT", desc = "新增税金计提规则")
    @PostMapping
    public Result<TaxProvisionRule> create(@RequestBody TaxProvisionRule rule) {
        rule.setId(null);
        return Result.success(service.save(rule));
    }

    @RequiresPerm("gl:taxprov:provision")
    @OperLog(module = "TAX_PROVISION_RULE", operType = "UPDATE", desc = "修改税金计提规则")
    @PutMapping("/{id}")
    public Result<TaxProvisionRule> update(@PathVariable Long id, @RequestBody TaxProvisionRule rule) {
        rule.setId(id);
        return Result.success(service.save(rule));
    }

    @RequiresPerm("gl:taxprov:provision")
    @OperLog(module = "TAX_PROVISION_RULE", operType = "DELETE", desc = "删除税金计提规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
