// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdInvoiceRule;
import com.fssc.masterdata.service.InvoiceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 发票智能匹配规则（/api/tenant/masterdata/invoice-rule/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/invoice-rule")
@RequiredArgsConstructor
public class InvoiceRuleController {

    private final InvoiceRuleService service;

    // 分页列表：管理员维护规则用
    // 本类两个读口原先零注解——菜单已登记 md:invoiceRule:list（sys_menu 3395，
    // 现有且只有 CONFIG_ADMIN/IT_ADMIN 持有），但该码在 Java 侧零消费，等于"有码、无"。
    // 现按同包既有写口的授码规则（create/update/delete 各用其码，不新造码）把列表口挂上 list 码。
    // 【match 不加注解，是刻意的】/match 是报销页勾选发票时的费用类型预填助手
    // （fssc-web src/composables/useBill.js、views/expense/ExpenseCreateTab.vue 调用），
    // 面向全体提单人；给它挂 list 码＝把管理员码塞给普通员工，既扩语义又会让预填功能对
    // 除两个管理员角色之外所有人失效。该口只回一条规则的展示字段、不含增删改，保持现状。
    @RequiresPerm("md:invoiceRule:list")
    @GetMapping("/page")
    public Result<PageResult<MdInvoiceRule>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    // 匹配：报销页勾选发票时按商品/服务名称预填费用类型（普通员工可访问，命中返回规则，未命中返回 null）
    @GetMapping("/match")
    public Result<MdInvoiceRule> match(@RequestParam("name") String name) {
        return Result.success(service.match(name));
    }

    @RequiresPerm("md:invoiceRule:create")
    @OperLog(module = "INVOICE_RULE", operType = "INSERT", desc = "新增发票匹配规则")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdInvoiceRule e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:invoiceRule:update")
    @OperLog(module = "INVOICE_RULE", operType = "UPDATE", desc = "更新发票匹配规则")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdInvoiceRule e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:invoiceRule:update")
    @OperLog(module = "INVOICE_RULE", operType = "UPDATE", desc = "发票匹配规则启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:invoiceRule:delete")
    @OperLog(module = "INVOICE_RULE", operType = "DELETE", desc = "删除发票匹配规则")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}