// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfPackage;
import com.fssc.platform.mapper.PfPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台套餐管理（/api/platform/package/**）。超管专属，操作落 pf_oper_log。
 */
@RestController
@RequestMapping("/api/platform/package")
@RequiredArgsConstructor
@PlatformOnly
public class PlatformPackageController {

    private final PfPackageMapper pfPackageMapper;

    @RequiresPerm("platform:package:list")
    @GetMapping("/list")
    public Result<List<PfPackage>> list() {
        return Result.success(pfPackageMapper.selectList(
                new LambdaQueryWrapper<PfPackage>().eq(PfPackage::getStatus, 1).orderByAsc(PfPackage::getSort)));
    }

    @RequiresPerm("platform:package:create")
    @OperLog(module = "PACKAGE", operType = "INSERT", desc = "新增套餐")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody PfPackage pkg) {
        pkg.setId(null);
        pfPackageMapper.insert(pkg);
        return Result.success(pkg.getId());
    }
}
