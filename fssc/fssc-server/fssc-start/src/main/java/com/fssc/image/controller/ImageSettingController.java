// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.image.entity.ImgSetting;
import com.fssc.image.mapper.ImgSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 影像系统设置（需求 6.3）：业务类型设置 / 文件种类识别设置 CRUD。
 * 分库改造：直连 ImgSettingMapper，类级路由 fssc_image 库。
 */
@RestController
@RequestMapping("/api/tenant/image/setting")
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageSettingController {

    private final ImgSettingMapper settingMapper;

    @RequiresPerm("image:setting:list")
    @GetMapping("/page")
    public Result<PageResult<ImgSetting>> page(PageQuery q, String settingType, String keyword) {
        LambdaQueryWrapper<ImgSetting> w = new LambdaQueryWrapper<>();
        if (settingType != null && !settingType.isBlank()) {
            w.eq(ImgSetting::getSettingType, settingType);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(ImgSetting::getSettingName, keyword).or().like(ImgSetting::getSettingCode, keyword));
        }
        w.orderByAsc(ImgSetting::getSettingType).orderByAsc(ImgSetting::getSettingCode);
        Page<ImgSetting> p = settingMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("image:setting:update")
    @OperLog(module = "IMAGE_SETTING", operType = "INSERT", desc = "新增影像设置")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ImgSetting setting) {
        settingMapper.insert(setting);
        return Result.success(setting.getId());
    }

    @RequiresPerm("image:setting:update")
    @OperLog(module = "IMAGE_SETTING", operType = "UPDATE", desc = "更新影像设置")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ImgSetting setting) {
        settingMapper.updateById(setting);
        return Result.success();
    }

    @RequiresPerm("image:setting:update")
    @OperLog(module = "IMAGE_SETTING", operType = "DELETE", desc = "删除影像设置")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        settingMapper.deleteById(id);
        return Result.success();
    }

    @RequiresPerm("image:setting:list")
    @GetMapping("/types")
    public Result<java.util.List<ImgSetting>> types(@RequestParam String settingType) {
        return Result.success(settingMapper.selectList(new LambdaQueryWrapper<ImgSetting>()
                .eq(ImgSetting::getSettingType, settingType)
                .eq(ImgSetting::getStatus, "NORMAL")
                .orderByAsc(ImgSetting::getSettingCode)));
    }
}
