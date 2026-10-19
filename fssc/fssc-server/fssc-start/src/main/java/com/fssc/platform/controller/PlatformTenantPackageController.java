// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfFeature;
import com.fssc.platform.entity.PfPackageFeature;
import com.fssc.platform.mapper.PfFeatureMapper;
import com.fssc.platform.mapper.PfPackageFeatureMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台租户套餐·功能开关（/api/platform/tenant-package/**）。
 * 开关落 pf_package_feature（按 packageId + featureCode 唯一），
 * 登录时经 pf_tenant_package → pf_package_feature(enabled=1) 装载进 JWT "ftr"。
 * 超管专属，操作 @OperLog 落 pf_oper_log。
 */
@RestController
@RequestMapping("/api/platform/tenant-package")
@RequiredArgsConstructor
@PlatformOnly
public class PlatformTenantPackageController {

    private final PfFeatureMapper pfFeatureMapper;
    private final PfPackageFeatureMapper pfPackageFeatureMapper;

    /**
     * 设置某套餐的功能开关矩阵：逐条 upsert pf_package_feature。
     * tenantId 仅用于审计留痕（开关本体作用于 packageId）。
     */
    @RequiresPerm("platform:package:update")
    @OperLog(module = "TENANT_PACKAGE", operType = "UPDATE", desc = "设置套餐功能开关")
    @PostMapping("/set-features")
    public Result<Integer> setFeatures(@RequestBody SetFeaturesReq req) {
        if (req == null || req.getPackageId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "packageId 必填");
        }
        if (req.getFeatures() == null || req.getFeatures().isEmpty()) {
            return Result.success(0);
        }
        int affected = 0;
        for (FeatureSwitch fs : req.getFeatures()) {
            if (fs.getCode() == null || fs.getCode().isBlank()) {
                continue;
            }
            int enabled = (fs.getEnabled() != null && fs.getEnabled() == 1) ? 1 : 0;
            PfPackageFeature exist = pfPackageFeatureMapper.selectOne(new LambdaQueryWrapper<PfPackageFeature>()
                    .eq(PfPackageFeature::getPackageId, req.getPackageId())
                    .eq(PfPackageFeature::getFeatureCode, fs.getCode()));
            if (exist == null) {
                PfPackageFeature row = new PfPackageFeature();
                row.setPackageId(req.getPackageId());
                row.setFeatureCode(fs.getCode());
                row.setEnabled(enabled);
                pfPackageFeatureMapper.insert(row);
            } else {
                PfPackageFeature upd = new PfPackageFeature();
                upd.setId(exist.getId());
                upd.setEnabled(enabled);
                pfPackageFeatureMapper.updateById(upd);
            }
            affected++;
        }
        return Result.success(affected);
    }

    /**
     * 查询某套餐当前功能开关矩阵：全量功能点 + 该套餐的 enabled 标记（缺省 0）。
     */
    @RequiresPerm("platform:package:list")
    @GetMapping("/features")
    public Result<List<Map<String, Object>>> features(@RequestParam Long packageId) {
        List<PfFeature> all = pfFeatureMapper.selectList(new LambdaQueryWrapper<PfFeature>()
                .orderByAsc(PfFeature::getModule).orderByAsc(PfFeature::getSort));
        Map<String, Integer> enabledMap = new HashMap<>();
        for (PfPackageFeature pf : pfPackageFeatureMapper.selectList(new LambdaQueryWrapper<PfPackageFeature>()
                .eq(PfPackageFeature::getPackageId, packageId))) {
            enabledMap.put(pf.getFeatureCode(), pf.getEnabled());
        }
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (PfFeature f : all) {
            Map<String, Object> row = new HashMap<>();
            row.put("code", f.getFeatureCode());
            row.put("name", f.getFeatureName());
            row.put("module", f.getModule());
            row.put("enabled", enabledMap.getOrDefault(f.getFeatureCode(), 0));
            matrix.add(row);
        }
        return Result.success(matrix);
    }

    @Data
    public static class SetFeaturesReq {
        /** 租户ID（业务外键，仅审计留痕） */
        private Long tenantId;
        private Long packageId;
        private List<FeatureSwitch> features;
    }

    @Data
    public static class FeatureSwitch {
        private String code;
        /** 1 开启 0 关闭 */
        private Integer enabled;
    }
}
