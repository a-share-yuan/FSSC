// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfConfig;
import com.fssc.platform.entity.PfNoticeTemplate;
import com.fssc.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台全局配置与消息模板（/api/platform/config/**）。平台端 tenantId 恒为 null。 */
@RestController
@RequestMapping("/api/platform/config")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService configService;

    // 平台全局配置

    @RequiresPerm("platform:config:list")
    @GetMapping("/page")
    public Result<PageResult<PfConfig>> page(PageQuery query,
                                             @RequestParam(required = false) String group,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(configService.configPage(query.getPageNum(), query.getPageSize(), group, keyword));
    }

    @RequiresPerm("platform:config:create")
    @OperLog(module = "PF_CONFIG", operType = "INSERT", desc = "新增平台配置")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody PfConfig cfg) {
        return Result.success(configService.createConfig(cfg));
    }

    @RequiresPerm("platform:config:update")
    @OperLog(module = "PF_CONFIG", operType = "UPDATE", desc = "更新平台配置")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody PfConfig cfg) {
        configService.updateConfig(cfg);
        return Result.success();
    }

    @RequiresPerm("platform:config:delete")
    @OperLog(module = "PF_CONFIG", operType = "DELETE", desc = "删除平台配置")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        configService.deleteConfig(id);
        return Result.success();
    }

    // 消息模板

    @RequiresPerm("platform:template:list")
    @GetMapping("/template/page")
    public Result<PageResult<PfNoticeTemplate>> templatePage(PageQuery query,
                                                             @RequestParam(required = false) String channel,
                                                             @RequestParam(required = false) String keyword) {
        return Result.success(configService.templatePage(query.getPageNum(), query.getPageSize(), channel, keyword));
    }

    @RequiresPerm("platform:template:create")
    @OperLog(module = "PF_NOTICE_TEMPLATE", operType = "INSERT", desc = "新增消息模板")
    @PostMapping("/template/create")
    public Result<Long> templateCreate(@RequestBody PfNoticeTemplate t) {
        return Result.success(configService.createTemplate(t));
    }

    @RequiresPerm("platform:template:update")
    @OperLog(module = "PF_NOTICE_TEMPLATE", operType = "UPDATE", desc = "更新消息模板")
    @PostMapping("/template/update")
    public Result<Void> templateUpdate(@RequestBody PfNoticeTemplate t) {
        configService.updateTemplate(t);
        return Result.success();
    }

    @RequiresPerm("platform:template:delete")
    @OperLog(module = "PF_NOTICE_TEMPLATE", operType = "DELETE", desc = "删除消息模板")
    @PostMapping("/template/{id}/delete")
    public Result<Void> templateDelete(@PathVariable Long id) {
        configService.deleteTemplate(id);
        return Result.success();
    }
}
