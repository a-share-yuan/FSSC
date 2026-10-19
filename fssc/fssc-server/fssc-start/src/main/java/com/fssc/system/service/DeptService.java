// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysDept;
import com.fssc.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 部门管理（树形，归属公司） */
@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptMapper sysDeptMapper;

    public List<SysDept> tree() {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getCompanyId).orderByAsc(SysDept::getAncestors).orderByAsc(SysDept::getSort));
    }

    /** 按公司过滤部门树（左树右表联动用） */
    public List<SysDept> treeByCompany(Long companyId) {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getCompanyId, companyId)
                .orderByAsc(SysDept::getAncestors).orderByAsc(SysDept::getSort));
    }

    public Long create(SysDept dept) {
        validate(dept);
        dept.setId(null);
        dept.setAncestors(calcAncestors(dept.getParentId()));
        sysDeptMapper.insert(dept);
        return dept.getId();
    }

    public void update(SysDept dept) {
        if (sysDeptMapper.selectById(dept.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门不存在: " + dept.getId());
        }
        validate(dept);
        sysDeptMapper.updateById(dept);
    }

    /**
     * 必填收口（批次 2C，与前端 Org.vue「部门名称/部门编码/请选择公司」同口径）。
     * create/update 同规：前端整单提交完整对象；enable() 走独立对象，不经过本方法。
     * 前端下拉「未选」传 0 而非 null，故 0 也判为未选。
     */
    private void validate(SysDept dept) {
        AssertUtil.notBlank(dept.getDeptName(), ErrorCode.PARAM_INVALID, "请输入部门名称");
        AssertUtil.notBlank(dept.getDeptCode(), ErrorCode.PARAM_INVALID, "请输入部门编码");
        AssertUtil.isTrue(dept.getCompanyId() != null && dept.getCompanyId() != 0L,
                ErrorCode.PARAM_INVALID, "请选择公司");
    }

    public void enable(Long id, boolean enable) {
        SysDept u = new SysDept();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        sysDeptMapper.updateById(u);
    }

    /** 删除部门（逻辑删；有下级部门时拒绝） */
    public void delete(Long id) {
        long sub = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (sub > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在下级部门，不能删除");
        }
        sysDeptMapper.hardDelete(id);
    }

    private String calcAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        SysDept p = sysDeptMapper.selectById(parentId);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上级部门不存在: " + parentId);
        }
        String a = p.getAncestors() == null || p.getAncestors().isBlank() ? "0" : p.getAncestors();
        return a + "," + parentId;
    }
}
