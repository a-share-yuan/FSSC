// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.service.ExtInterfaceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 外部接口统一配置（/api/tenant/system/ext-interface/**）。
 *
 * <p>集中管理发票验真、发票认证、OCR 影像识别、AI 大模型等外部接口的连接参数。
 * 管理员可在页面增删改查接口配置、启用/停用、设为默认、测试连接。</p>
 *
 * <p>R5-D-038：本控制器是凭据出参的唯一掩码边界——{@link #desensitize} 把
 * apiKey / apiSecret / extraConfig 内的凭据类键值掩码后再出 HTTP；
 * Service 与 Mapper 一律返真值，网关调用链（getEnabledByCategory / selectById）不受影响。
 * 新增返回 ExtInterfaceConfig 的端点必须走 desensitize。</p>
 *
 * <p>R5-D-038 权限码：list/query/create/update/delete/test 六个码已由
 * sql/V1.0.6_ext_interface_config.sql:45-61 建好菜单行并授 Role 1，无需重播。
 * 本批只为「真详情」/{id} 新增 system:extiface:manage（与已有的 update 走 OR，
 * 未播种子行前靠 update 放行，不打断现网）；/by-category 维持 query 口径不提级。
 * 需要的种子行如下：</p>
 * <pre>
 * -- INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `sort`, `path`, `component`, `perms`, `menu_type`, `icon`, `visible`, `status`, `del_flag`)
 * -- SELECT '接口参数维护', id, 6, '', '', 'system:extiface:manage', 'F', '#', 1, 1, 0 FROM sys_menu WHERE perms='system:extiface:list' AND del_flag=0 LIMIT 1;
 * -- INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`)
 * -- SELECT 1, 1, id FROM sys_menu WHERE perms='system:extiface:manage' AND del_flag=0;
 * </pre>
 */
@RestController
@RequestMapping("/api/tenant/system/ext-interface")
@RequiredArgsConstructor
public class ExtInterfaceConfigController {

    private final ExtInterfaceConfigService extService;

    @RequiresPerm("system:extiface:list")
    @GetMapping("/page")
    public Result<PageResult<ExtInterfaceConfig>> page(PageQuery q,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) String keyword) {
        PageResult<ExtInterfaceConfig> r = extService.page(q.getPageNum(), q.getPageSize(), category, keyword);
        if (r.getList() != null) {
            r.getList().forEach(this::desensitize);
        }
        return Result.success(r);
    }

    /** R5-D-039：页签计数来源——按 category 汇总配置条数（含 0 条类别，前端据此展示 (n) 徽标） */
    @RequiresPerm("system:extiface:list")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.success(extService.categoryStats());
    }

    @RequiresPerm({"system:extiface:manage", "system:extiface:update"})
    @GetMapping("/{id}")
    public Result<ExtInterfaceConfig> getById(@PathVariable Long id) {
        return Result.success(desensitize(extService.getById(id)));
    }

    @RequiresPerm("system:extiface:create")
    @OperLog(module = "EXT_INTERFACE", operType = "INSERT", desc = "新增外部接口配置")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ExtInterfaceConfig config) {
        return Result.success(extService.create(config));
    }

    @RequiresPerm("system:extiface:update")
    @OperLog(module = "EXT_INTERFACE", operType = "UPDATE", desc = "更新外部接口配置")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ExtInterfaceConfig config) {
        extService.update(config);
        return Result.success();
    }

    @RequiresPerm("system:extiface:delete")
    @OperLog(module = "EXT_INTERFACE", operType = "DELETE", desc = "删除外部接口配置")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        extService.delete(id);
        return Result.success();
    }

    @RequiresPerm("system:extiface:update")
    @OperLog(module = "EXT_INTERFACE", operType = "UPDATE", desc = "切换接口启用状态")
    @PostMapping("/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        extService.toggleStatus(id, body.get("status"));
        return Result.success();
    }

    @RequiresPerm("system:extiface:update")
    @OperLog(module = "EXT_INTERFACE", operType = "UPDATE", desc = "设为默认接口")
    @PostMapping("/{id}/set-default")
    public Result<Void> setDefault(@PathVariable Long id) {
        extService.setDefault(id);
        return Result.success();
    }

    @RequiresPerm("system:extiface:test")
    @OperLog(module = "EXT_INTERFACE", operType = "OTHER", desc = "测试外部接口连接")
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        return Result.success(extService.testConnection(id));
    }

    /**
     * 查询指定分类下已启用的接口配置（供业务模块调用外部接口时读取连接参数）。
     * R5-D-038：出参凭据一律掩码，业务模块取真值走 Service/Mapper 不走本端点；
     * 权限保留原有 query 口径（对账页用它探测通道是否启用），不提级以免误伤。
     */
    @RequiresPerm({"system:extiface:query", "system:extiface:manage", "system:extiface:update"})
    @GetMapping("/by-category")
    public Result<ExtInterfaceConfig> getByCategory(@RequestParam String category) {
        return Result.success(desensitize(extService.getEnabledByCategory(category)));
    }

    /** R5-D-038 出参脱敏：仅在本控制器（HTTP 边界）就地掩码，Service/Mapper 与网关链路保持真值 */
    private ExtInterfaceConfig desensitize(ExtInterfaceConfig config) {
        if (config == null) {
            return null;
        }
        config.setApiKey(ExtInterfaceConfig.maskCredential(config.getApiKey()));
        config.setApiSecret(ExtInterfaceConfig.maskCredential(config.getApiSecret()));
        config.setExtraConfig(ExtInterfaceConfig.maskExtraConfigSecrets(config.getExtraConfig()));
        return config;
    }
}
