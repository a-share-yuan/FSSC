// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.OrgVisibility;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 部门管理（树形，归属公司） */
@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 全租户部门树（无公司参数时的入口）。
     *
     * <p>非"可跨公司"账号只返回本公司部门——原先任何登录用户都能拿到全部 55 个部门、
     * 覆盖 9 家法人主体，组织选择器里因此能看到别家公司。</p>
     */
    public List<SysDept> tree() {
        LoginUser u = UserContext.get();
        LambdaQueryWrapper<SysDept> qw = new LambdaQueryWrapper<>();
        if (!OrgVisibility.canSeeAllCompanies(u)) {
            qw.eq(SysDept::getCompanyId, u.getCompanyId());
        }
        qw.orderByAsc(SysDept::getCompanyId).orderByAsc(SysDept::getAncestors).orderByAsc(SysDept::getSort);
        return sysDeptMapper.selectList(qw);
    }

    /**
     * 按公司过滤部门树（左树右表联动用）。
     *
     * <p>显式指定公司时先做归属校验：普通员工请求其他公司的部门树直接拒绝，
     * 而不是返回一份不属于他的组织名单。</p>
     */
    public List<SysDept> treeByCompany(Long companyId) {
        if (companyId == null) {
            return tree();
        }
        LoginUser u = UserContext.get();
        if (!OrgVisibility.canSeeCompany(u, companyId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    "无权查看该公司的部门（companyId=" + companyId + "）");
        }
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
     * 必填办结（批次 2C，与前端 Org.vue「部门名称/部门编码/请选择公司」同一规则）。
     * create/update 同规：前端整单提交完整对象；enable() 走独立对象，不经过本方法。
     * 前端下拉「未选」传 0 而非 null，故 0 也判为未选。
     */
    private void validate(SysDept dept) {
        AssertUtil.notBlank(dept.getDeptName(), ErrorCode.PARAM_INVALID, "请输入部门名称");
        AssertUtil.notBlank(dept.getDeptCode(), ErrorCode.PARAM_INVALID, "请输入部门编码");
        AssertUtil.isTrue(dept.getCompanyId() != null && dept.getCompanyId() != 0L,
                ErrorCode.PARAM_INVALID, "请选择公司");
    }

    /**
     * 部门启用/停用。
     *
     * <p>停用前置三条，避免把组织树写坏（此前是裸 updateById，且界面上没有入口，停错只能改库）：
     * <ol>
     *   <li>不能停用当前登录账号所在部门——否则本人下一次取数就落在停用部门；</li>
     *   <li>有未停用下级部门时拒绝——避免「父停子不停」；</li>
     *   <li>部门下还有在职用户（NORMAL）时拒绝——先调岗或停用人员，不做隐式连带。</li>
     * </ol>
     */
    public void enable(Long id, boolean enable) {
        SysDept cur = sysDeptMapper.selectById(id);
        if (cur == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门不存在: " + id);
        }
        if (!enable) {
            LoginUser me = UserContext.get();
            if (me != null && id.equals(me.getDeptId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "不能停用当前登录账号所在部门，请先切换部门归属再操作");
            }
            long activeChildren = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getParentId, id)
                    .eq(SysDept::getStatus, "NORMAL")
                    .eq(SysDept::getDelFlag, 0));
            if (activeChildren > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该部门下还有 " + activeChildren + " 个未停用的子部门，请先停用子部门");
            }
            long activeUsers = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getDeptId, id)
                    .eq(SysUser::getStatus, "NORMAL")
                    .eq(SysUser::getDelFlag, 0));
            if (activeUsers > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该部门下还有 " + activeUsers + " 名在职用户，请先调整其部门或停用账号");
            }
        }
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
