// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdCostCenter;
import com.fssc.masterdata.service.CostCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 CostCenter（/api/tenant/masterdata/cost-center/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/cost-center")
@RequiredArgsConstructor
public class CostCenterController {

    private final CostCenterService service;

    // 只读查询放开给所有登录租户用户：单据发起页表单必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<MdCostCenter>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    /** 树形数据（前端按 parentId 组树） */
    @GetMapping("/tree")
    public Result<java.util.List<MdCostCenter>> tree() {
        return Result.success(service.tree());
    }

    @RequiresPerm("md:costcenter:create")
    @OperLog(module = "COST_CENTER", operType = "INSERT", desc = "新增CostCenter")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdCostCenter e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:costcenter:update")
    @OperLog(module = "COST_CENTER", operType = "UPDATE", desc = "更新CostCenter")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdCostCenter e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:costcenter:update")
    @OperLog(module = "COST_CENTER", operType = "UPDATE", desc = "CostCenter启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:costCenter:delete")
    @OperLog(module = "COST_CENTER", operType = "DELETE", desc = "删除成本中心")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
