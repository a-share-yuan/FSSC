// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysQueryVariant;
import com.fssc.system.mapper.SysQueryVariantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 查询方案（Variant）服务。
 * <p>保存个人常用筛选组合，支持 CRUD 与默认方案设置。</p>
 */
@Service
@RequiredArgsConstructor
public class QueryVariantService {

    private final SysQueryVariantMapper mapper;

    /** 当前用户ID */
    private Long currentUserId() {
        try {
            return UserContext.getRequired().getUserId();
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    /**
     * 查询当前用户的全部方案（按页面 key 过滤）。
     */
    public List<SysQueryVariant> listByPage(String pageKey) {
        return mapper.selectList(new LambdaQueryWrapper<SysQueryVariant>()
                .eq(SysQueryVariant::getUserId, currentUserId())
                .eq(pageKey != null && !pageKey.isBlank(), SysQueryVariant::getPageKey, pageKey)
                .orderByDesc(SysQueryVariant::getIsDefault).orderByDesc(SysQueryVariant::getUpdateTime));
    }

    /**
     * 保存方案（新增）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysQueryVariant variant) {
        // 必填办结（批次 2C，与前端 GenericList「保存查询方案」弹窗同一规则）
        AssertUtil.notBlank(variant.getVariantName(), ErrorCode.PARAM_INVALID, "请输入方案名称");
        variant.setId(null);
        variant.setUserId(currentUserId());
        // 同页同名防重
        long dup = mapper.selectCount(new LambdaQueryWrapper<SysQueryVariant>()
                .eq(SysQueryVariant::getUserId, currentUserId())
                .eq(SysQueryVariant::getPageKey, variant.getPageKey())
                .eq(SysQueryVariant::getVariantName, variant.getVariantName()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该页面下已存在同名方案: " + variant.getVariantName());
        }
        // 如果设为默认，先清除旧的默认
        if (Boolean.TRUE.equals(variant.getIsDefault()) || Integer.valueOf(1).equals(variant.getIsDefault())) {
            clearDefault(variant.getPageKey());
            variant.setIsDefault(1);
        } else {
            variant.setIsDefault(0);
        }
        mapper.insert(variant);
        return variant.getId();
    }

    /**
     * 更新方案（名称、筛选条件、排序）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SysQueryVariant variant) {
        SysQueryVariant exist = mapper.selectById(variant.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案不存在: " + variant.getId());
        }
        if (!exist.getUserId().equals(currentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的方案");
        }
        // 整单更新（前端提交完整对象），故与 create 同一规则拦空
        AssertUtil.notBlank(variant.getVariantName(), ErrorCode.PARAM_INVALID, "请输入方案名称");
        // 如果设为默认，先清除旧的默认
        if (Boolean.TRUE.equals(variant.getIsDefault()) || Integer.valueOf(1).equals(variant.getIsDefault())) {
            clearDefault(exist.getPageKey());
            variant.setIsDefault(1);
        } else {
            variant.setIsDefault(0);
        }
        mapper.updateById(variant);
    }

    /**
     * 删除方案。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysQueryVariant exist = mapper.selectById(id);
        if (exist == null) return;
        if (!exist.getUserId().equals(currentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的方案");
        }
        mapper.deleteById(id);
    }

    /**
     * 设为默认方案（同一页面只能有一个默认）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        SysQueryVariant exist = mapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案不存在: " + id);
        }
        if (!exist.getUserId().equals(currentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的方案");
        }
        clearDefault(exist.getPageKey());
        SysQueryVariant upd = new SysQueryVariant();
        upd.setId(id);
        upd.setIsDefault(1);
        mapper.updateById(upd);
    }

    /** 获取当前用户指定页面的默认方案 */
    public SysQueryVariant getDefault(String pageKey) {
        return mapper.selectOne(new LambdaQueryWrapper<SysQueryVariant>()
                .eq(SysQueryVariant::getUserId, currentUserId())
                .eq(SysQueryVariant::getPageKey, pageKey)
                .eq(SysQueryVariant::getIsDefault, 1)
                .last("LIMIT 1"));
    }

    /** 清除当前用户指定页面的所有默认标记 */
    private void clearDefault(String pageKey) {
        List<SysQueryVariant> defaults = mapper.selectList(new LambdaQueryWrapper<SysQueryVariant>()
                .eq(SysQueryVariant::getUserId, currentUserId())
                .eq(SysQueryVariant::getPageKey, pageKey)
                .eq(SysQueryVariant::getIsDefault, 1));
        for (SysQueryVariant d : defaults) {
            SysQueryVariant upd = new SysQueryVariant();
            upd.setId(d.getId());
            upd.setIsDefault(0);
            mapper.updateById(upd);
        }
    }
}