package com.fssc.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfFeature;
import com.fssc.platform.mapper.PfFeatureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台功能点（/api/platform/feature/**）。套餐开关的基础单元。 */
@RestController
@RequestMapping("/api/platform/feature")
@RequiredArgsConstructor
@PlatformOnly
public class PlatformFeatureController {

    private final PfFeatureMapper pfFeatureMapper;

    @RequiresPerm("platform:feature:list")
    @GetMapping("/list")
    public Result<List<PfFeature>> list(String module) {
        LambdaQueryWrapper<PfFeature> qw = new LambdaQueryWrapper<PfFeature>()
                .eq(module != null && !module.isBlank(), PfFeature::getModule, module)
                .orderByAsc(PfFeature::getModule).orderByAsc(PfFeature::getSort);
        return Result.success(pfFeatureMapper.selectList(qw));
    }
}
