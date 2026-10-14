// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysDictData;
import com.fssc.system.entity.SysDictType;
import com.fssc.system.mapper.SysDictDataMapper;
import com.fssc.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 租户字典管理（sys_dict_type / sys_dict_data，均带 tenant_id） */
@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;

    // ---------- 字典类型 ----------

    public PageResult<SysDictType> typePage(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysDictType> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysDictType::getDictType, keyword)
                    .or().like(SysDictType::getDictName, keyword));
        }
        qw.orderByDesc(SysDictType::getId);
        Page<SysDictType> p = sysDictTypeMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long typeCreate(SysDictType type) {
        // 必填收口（批次 2C，与前端 GenericList 字典类型弹窗同口径）：字典编码是取值键、名称是展示键
        AssertUtil.notBlank(type.getDictName(), ErrorCode.PARAM_INVALID, "请输入字典名");
        AssertUtil.notBlank(type.getDictType(), ErrorCode.PARAM_INVALID, "请输入字典编码");
        long dup = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, type.getDictType()));
        if (dup > 0) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "字典类型已存在: " + type.getDictType());
        }
        type.setId(null);
        sysDictTypeMapper.insert(type);
        return type.getId();
    }

    public void typeUpdate(SysDictType type) {
        // 整单更新（前端提交完整对象），故与 typeCreate 同口径拦空
        AssertUtil.notBlank(type.getDictName(), ErrorCode.PARAM_INVALID, "请输入字典名");
        AssertUtil.notBlank(type.getDictType(), ErrorCode.PARAM_INVALID, "请输入字典编码");
        sysDictTypeMapper.updateById(type);
    }

    /** 删除字典类型（逻辑删；有字典数据时拒绝） */
    public void typeDelete(Long id) {
        SysDictType type = sysDictTypeMapper.selectById(id);
        if (type != null) {
            long cnt = sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, type.getDictType()));
            if (cnt > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该字典类型下存在 " + cnt + " 条字典数据，不能删除");
            }
        }
        sysDictTypeMapper.hardDelete(id);
    }

    // ---------- 字典数据 ----------

    public List<SysDictData> dataList(String dictType) {
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getSort));
    }

    public Long dataCreate(SysDictData data) {
        validateData(data);
        data.setId(null);
        sysDictDataMapper.insert(data);
        return data.getId();
    }

    public void dataUpdate(SysDictData data) {
        validateData(data);
        sysDictDataMapper.updateById(data);
    }

    /** 必填收口（批次 2C，与前端 GenericList 字典数据项弹窗 dataItemRules 同口径） */
    private void validateData(SysDictData data) {
        AssertUtil.notBlank(data.getDictType(), ErrorCode.PARAM_INVALID, "缺少字典编码");
        AssertUtil.notBlank(data.getDictLabel(), ErrorCode.PARAM_INVALID, "请填写标签");
        AssertUtil.notBlank(data.getDictValue(), ErrorCode.PARAM_INVALID, "请填写值");
    }

    public void dataDelete(Long id) {
        // 逻辑删除（@TableLogic）
        sysDictDataMapper.hardDelete(id);
    }
}
