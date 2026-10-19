// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 BankAccount（/api/tenant/masterdata/bank-account/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/bank-account")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;

    @RequiresPerm("md:bankaccount:list")
    @GetMapping("/page")
    public Result<PageResult<MdBankAccount>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:bankaccount:create")
    @OperLog(module = "BANK_ACCOUNT", operType = "INSERT", desc = "新增BankAccount")
    @PostMapping("/create")
    public Result<Long> create(@Validated @RequestBody MdBankAccount e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:bankaccount:update")
    @OperLog(module = "BANK_ACCOUNT", operType = "UPDATE", desc = "更新BankAccount")
    @PostMapping("/update")
    public Result<Void> update(@Validated @RequestBody MdBankAccount e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:bankaccount:update")
    @OperLog(module = "BANK_ACCOUNT", operType = "UPDATE", desc = "BankAccount启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:bankaccount:update")
    @OperLog(module = "BANK_ACCOUNT", operType = "START", desc = "发起银行印鉴变更审批(TR01-02)")
    @PostMapping("/{id}/start-seal-change")
    public Result<String> startSealChange(@PathVariable Long id) {
        return Result.success(service.startSealChange(id));
    }

    // 权限治理（2026-09-16）：原用 @RequiresPerm("md:bank:delete") 属跨对象串味——该码挂在
    // 菜单「银行信息删除」(#3457) 且 **0 角色持有**，语义上又指向「银行信息」而非「银行账户」，
    // 导致本删除接口对所有角色恒为无权（功能实际不可用）。
    // 现改为对象一致的 md:bankaccount:delete，并由 seed-perm-p1-20260916.sql 将该码授予
    // 数据配置管理员（菜单 #3457 授权 CONFIG_ADMIN）。
    // 因旧码持有者为 0，无需多码 OR 过渡，直接用单一码以避免留下幽灵码。
    // 注意：删除银行账户属 SoD-01 高风险动作，该码只应授予数据配置管理员，不得授予出纳/资金岗。
    @RequiresPerm("md:bankaccount:delete")
    @OperLog(module = "BANK_ACCOUNT", operType = "DELETE", desc = "删除银行账户")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
