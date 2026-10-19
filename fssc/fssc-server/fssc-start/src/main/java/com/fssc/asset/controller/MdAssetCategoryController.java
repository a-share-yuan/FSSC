package com.fssc.asset.controller;

import com.fssc.asset.entity.MdAssetCategory;
import com.fssc.asset.service.MdAssetCategoryService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资产类别（/api/tenant/asset/category/**）：CRUD（FA 投资到资产-资产类别） */
@RestController
@RequestMapping("/api/tenant/asset/category")
@RequiredArgsConstructor
public class MdAssetCategoryController {

    private final MdAssetCategoryService service;

    @RequiresPerm("asset:list")
    @GetMapping("/page")
    public Result<PageResult<MdAssetCategory>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("asset:create")
    @OperLog(module = "ASSET", operType = "INSERT", desc = "新增资产类别")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdAssetCategory e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "更新资产类别")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdAssetCategory e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "DELETE", desc = "删除资产类别")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}