// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysDictData;
import com.fssc.system.entity.SysDictType;
import com.fssc.system.service.DictService;
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

/** 租户字典（/api/tenant/system/dict/**） */
@RestController
@RequestMapping("/api/tenant/system/dict")
@RequiredArgsConstructor
public class TenantDictController {

    private final DictService dictService;

    // ---------- 类型 ----------

    @RequiresPerm("system:dict:list")
    @GetMapping("/type/page")
    public Result<PageResult<SysDictType>> typePage(PageQuery query, String keyword) {
        return Result.success(dictService.typePage(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("system:dict:create")
    @OperLog(module = "DICT", operType = "INSERT", desc = "新增字典类型")
    @PostMapping("/type/create")
    public Result<Long> typeCreate(@RequestBody SysDictType type) {
        return Result.success(dictService.typeCreate(type));
    }

    @RequiresPerm("system:dict:update")
    @OperLog(module = "DICT", operType = "UPDATE", desc = "更新字典类型")
    @PostMapping("/type/update")
    public Result<Void> typeUpdate(@RequestBody SysDictType type) {
        dictService.typeUpdate(type);
        return Result.success();
    }

    @RequiresPerm("system:dict:delete")
    @OperLog(module = "DICT", operType = "DELETE", desc = "删除字典类型")
    @PostMapping("/type/{id}/delete")
    public Result<Void> typeDelete(@PathVariable Long id) {
        dictService.typeDelete(id);
        return Result.success();
    }

    // ---------- 数据 ----------

    @RequiresPerm("system:dict:list")
    @GetMapping("/data/list")
    public Result<List<SysDictData>> dataList(@RequestParam String dictType) {
        // P1-3 缓存分层：字典按租户+类型缓存 5 分钟，变更主动失效
        String key = "dict:" + com.fssc.framework.tenant.TenantContext.getTenantId() + ":" + dictType;
        List<SysDictData> list = com.fssc.framework.cache.CacheUtil.get(key, () -> dictService.dataList(dictType));
        return Result.success(list);
    }

    /**
     * 【2026-10-15 枚举办结前置A】内置字典**免权限**只读通道。
     *
     * 为什么需要它：
     *   {@code system:dict:list} 没有独立权限节点，只挂在菜单 3615「系统参数」的复合 perms 里，
     *   实测只授予了 CONFIG_ADMIN(2人)/IT_ADMIN(1人) —— 而 COMMON_EMP 154 人、SCAN_STAFF 5 人
     *   全都读不到。前端枚举办结要覆盖 55 个业务页面（billStatus.js 的 statusLabel），
     *   若走带权限端点，普通用户每开一页都会吃一次 4030 + 一条 warn，办结等于白做。
     *
     * 安全性（三道校验，缺一不可）：
     *   ① **仍需登录**：本端点不在 JwtAuthFilter 的 WHITE_LIST 内，未携带 token 一律 401；
     *   ② **仅暴露 is_builtin=1**：校验在 DictService#builtinDataList，非内置类型返回空列表，
     *      租户自定义字典（可能含业务敏感名称表）**绝不会**从这条通道泄漏；
     *   ③ **只读**：无任何写方法，且不带 @OperLog（读操作不入审计流水，避免刷屏）。
     *
     * 语义边界（易混淆，特此写明）：
     *   is_builtin 标的是「平台下发的内置枚举」，**不是**跨租户共享 —— 本表仍是租户级表，
     *   仍走 tenant_id 行级隔离。跨租户共享应走 sys_dict_*_def（当前 0 行）。
     */
    @GetMapping("/builtin/data/list")
    public Result<List<SysDictData>> builtinDataList(@RequestParam String dictType) {
        // 与 /data/list 共用同一份 5 分钟缓存（key = dict:tenantId:dictType），不额外占内存
        String key = "dict:" + com.fssc.framework.tenant.TenantContext.getTenantId() + ":" + dictType;
        List<SysDictData> list = com.fssc.framework.cache.CacheUtil.get(key, () -> dictService.builtinDataList(dictType));
        return Result.success(list);
    }

    /**
     * 【2026-10-16 第 4 批】内置字典**批量**免权限只读通道（供前端一次性预热 66 个 enum_* 展示字典）。
     *
     * 与 {@link #builtinDataList(String)} 完全同一套校验：需登录、仅 is_builtin=1、只读。
     * 请求里夹带的非内置类型被静默剔除（不报错），返回的 Map 只含通过校验的类型。
     *
     * @param types 逗号分隔的 dict_type，如 {@code enum_biz_type,enum_msg_type}
     */
    @GetMapping("/builtin/data/batch")
    public Result<Map<String, List<SysDictData>>> builtinDataBatch(@RequestParam String types) {
        List<String> list = types == null ? List.of()
                : java.util.Arrays.stream(types.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.success(dictService.builtinDataBatch(list));
    }

    @RequiresPerm("system:dict:create")
    @OperLog(module = "DICT", operType = "INSERT", desc = "新增字典数据")
    @PostMapping("/data/create")
    public Result<Long> dataCreate(@RequestBody SysDictData data) {
        evictDictCache();
        return Result.success(dictService.dataCreate(data));
    }

    @RequiresPerm("system:dict:update")
    @OperLog(module = "DICT", operType = "UPDATE", desc = "更新字典数据")
    @PostMapping("/data/update")
    public Result<Void> dataUpdate(@RequestBody SysDictData data) {
        evictDictCache();
        dictService.dataUpdate(data);
        return Result.success();
    }

    @RequiresPerm("system:dict:update")
    @OperLog(module = "DICT", operType = "DELETE", desc = "删除字典数据")
    @PostMapping("/data/delete/{id}")
    public Result<Void> dataDelete(@PathVariable Long id) {
        evictDictCache();
        dictService.dataDelete(id);
        return Result.success();
    }

    /**
     * 字典变更 → 失效当前租户字典缓存。
     *
     *  必须同时清 {@code dictbuiltin:} 前缀：新增/改 is_builtin 标记后，
     * 若只清 {@code dict:}，内置集合缓存会残留旧值最长 5 分钟，
     * 表现为「刚建的内置字典走免权限通道读不到」。
     */
    private void evictDictCache() {
        String tid = String.valueOf(com.fssc.framework.tenant.TenantContext.getTenantId());
        com.fssc.framework.cache.CacheUtil.evictPrefix("dict:" + tid + ":");
        com.fssc.framework.cache.CacheUtil.evictPrefix("dictbuiltin:" + tid);
    }
}
