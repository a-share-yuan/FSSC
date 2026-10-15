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

    /** 字典变更 → 失效当前租户字典缓存 */
    private void evictDictCache() {
        com.fssc.framework.cache.CacheUtil.evictPrefix("dict:" + com.fssc.framework.tenant.TenantContext.getTenantId() + ":");
    }
}
