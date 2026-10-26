// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdCurrency;
import com.fssc.masterdata.service.CurrencyService;
import com.fssc.masterdata.service.CurrencyService.CurrencyOption;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主数据 Currency（/api/tenant/masterdata/currency/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService service;

    @RequiresPerm("md:currency:list")
    @GetMapping("/page")
    public Result<PageResult<MdCurrency>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    /**
     * 【2026-10-18 币种免权限通道】币种**下拉选项**免权限只读通道。
     *
     * 为什么需要它（与 {@code TenantDictController#builtinDataList} 同源问题）：
     *   {@code /page} 带 {@code md:currency:list}，实测只授予 CONFIG_ADMIN/IT_ADMIN，
     *   而 COMMON_EMP 154 人、SCAN_STAFF 5 人全部 4030。可币种下拉散落在
     *   budget / contract / expense / invoice / pay / purchase / receivable / treasury
     *   共 12 个业务页面 40 处，是**业务录入的必填字段**而非管理功能 ——
     *   若必须先授"币种管理权"才能填单，等于把主数据管理权限当成录入前置条件，权限模型不成立。
     *
     * 三道校验（与 builtin 字典通道同规格，缺一不可）：
     *   ① **仍需登录**：本端点不在 JwtAuthFilter 的 WHITE_LIST 内，未携带 token 一律 401；
     *   ② **仅启用且未删**：status=NORMAL 且 del_flag=0，停用/已删币种不外露（与 /page 规则一致，
     *      避免"下拉能选、提交被拒"）；
     *   ③ **只读 + 字段最小化**：无任何写方法；只返回下拉必需的展示字段，
     *      **不返回** remark / 审计列 / createBy 等管理字段，也不返回整行实体
     *      （避免把主数据管理接口的载荷形态原样复制到免权限通道上）。
     *
     * 语义边界：
     *   与 /page 共用 CurrencyService 的 5 分钟缓存（key=tenantId:currency:list），不额外占内存；
     *   币种表仍是**租户级表**，走 tenant_id 行级隔离，不是跨租户共享。
     */
    @GetMapping("/options")
    public Result<List<CurrencyOption>> options() {
        return Result.success(service.listOptions());
    }

    @RequiresPerm("md:currency:create")
    @OperLog(module = "CURRENCY", operType = "INSERT", desc = "新增Currency")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdCurrency e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:currency:update")
    @OperLog(module = "CURRENCY", operType = "UPDATE", desc = "更新Currency")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdCurrency e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:currency:update")
    @OperLog(module = "CURRENCY", operType = "UPDATE", desc = "Currency启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:currency:delete")
    @OperLog(module = "CURRENCY", operType = "DELETE", desc = "删除币种")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
