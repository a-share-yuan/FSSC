// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysLoginLog;
import com.fssc.system.entity.SysOperLog;
import com.fssc.system.mapper.SysLoginLogMapper;
import com.fssc.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统日志（需求 8 系统监控-系统日志）：查询租户操作日志。
 * 审计表只读，不提供增删改。
 */
@RestController
@RequestMapping("/api/tenant/system/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysOperLogMapper sysOperLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;

    @RequiresPerm("system:log:list")
    @GetMapping("/page")
    public Result<PageResult<SysOperLog>> page(PageQuery query,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String businessType,
                                               @RequestParam(required = false) Integer status) {
        IPage<SysOperLog> p = sysOperLogMapper.searchPage(
                Page.of(query.getPageNum(), query.getPageSize()),
                keyword, businessType, status);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }

    /**
     * 登录日志查看面——审计表只读查询（sys_login_log），分页返回。
     * 与操作日志同页签组（操作日志 / 登录日志），失败登录同样可查，便于异常排查。
     */
    @RequiresPerm("system:log:list")
    @GetMapping("/login/page")
    public Result<PageResult<SysLoginLog>> loginPage(PageQuery query,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status) {
        IPage<SysLoginLog> p = sysLoginLogMapper.searchPage(
                Page.of(query.getPageNum(), query.getPageSize()),
                keyword, status);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }
}
