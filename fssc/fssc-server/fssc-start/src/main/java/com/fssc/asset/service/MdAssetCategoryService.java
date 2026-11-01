// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.asset.entity.MdAssetCategory;
import com.fssc.asset.mapper.MdAssetCategoryMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 资产类别服务：CRUD。（FA 投资到资产-资产类别）
 */
@Service
@RequiredArgsConstructor
public class MdAssetCategoryService {

    private final MdAssetCategoryMapper mapper;

    public PageResult<MdAssetCategory> page(long pn, long ps, String keyword) {
        LambdaQueryWrapper<MdAssetCategory> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdAssetCategory::getCategoryCode, keyword)
                    .or().like(MdAssetCategory::getCategoryName, keyword));
        }
        qw.orderByAsc(MdAssetCategory::getId);
        Page<MdAssetCategory> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    public Long create(MdAssetCategory e) {
        requireManual(e);
        e.setId(null);
        if (e.getParentId() == null) e.setParentId(0L);
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdAssetCategory e) {
        require(e.getId());
        requireManual(e);
        mapper.updateById(e);
    }

    /** 必填守卫：文案与前端 ASSET_CATEGORY 规则一致；类别编码/名称是资产建档的追溯规则，留空即脏数据。 */
    private void requireManual(MdAssetCategory e) {
        if (e == null || !StringUtils.hasText(e.getCategoryCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入类别编码");
        }
        if (!StringUtils.hasText(e.getCategoryName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入类别名称");
        }
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private void require(Long id) {
        if (id == null || mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产类别不存在: " + id);
        }
    }
}