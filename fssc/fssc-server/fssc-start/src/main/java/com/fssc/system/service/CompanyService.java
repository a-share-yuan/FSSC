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
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysCompanyMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 公司（纳税主体）管理。集团型租户多法人公司，X-Company-Id 切换受租户归属校验约束。 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final SysCompanyMapper sysCompanyMapper;

    /**
     * 公司树（供单据发起页/X-Company-Id 切换下拉）。
     *
     * <p><b>只列启用公司</b>：{@code status='DISABLED'} 表示主体已建但尚未启用（无部门、无人员），
     * 出现在下拉里会让用户选到一个必然无法发起任何单据的公司（无组织 ⇒ 组织健康 R1/R2必然红）。
     * 管理端要看全部公司请走部门/公司管理页，不要依赖本方法。
     *
     * <p>与 {@code OrgHealthChecker} 的扫描规则一致（同样只取 NORMAL），
     * 避免「体检说没问题、下拉却选得到」的两种规则打架。
     */
    public List<SysCompany> tree() {
        return sysCompanyMapper.selectList(new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getStatus, "NORMAL")
                .orderByAsc(SysCompany::getAncestors).orderByAsc(SysCompany::getSort));
    }

    /**
     * 按登录人可见范围裁剪公司列表（出参侧过滤，不改缓存）。
     *
     * <p>缓存里保留"整租户全量"才能被所有用户共用；裁剪放在每次出参，既保住缓存命中，
     * 又保证普通员工的下拉里不会出现其他法人主体（原先 9 家公司全员可见）。</p>
     *
     * @param all 已按租户缓存的全量启用公司
     * @param overrideCompanyId 显式指定的公司（跨公司查看用），null＝按登录人范围
     */
    public List<SysCompany> visibleForUser(List<SysCompany> all, Long overrideCompanyId) {
        if (all == null || all.isEmpty()) {
            return all == null ? List.of() : all;
        }
        LoginUser u = UserContext.get();
        if (overrideCompanyId != null) {
            if (!OrgVisibility.canSeeCompany(u, overrideCompanyId)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED,
                        "无权查看该公司（companyId=" + overrideCompanyId + "）");
            }
            return all.stream().filter(c -> overrideCompanyId.equals(c.getId())).toList();
        }
        if (OrgVisibility.canSeeAllCompanies(u)) {
            return all;
        }
        Long mine = u.getCompanyId();
        return all.stream().filter(c -> mine.equals(c.getId())).toList();
    }

    public Long create(SysCompany company) {
        // 必填办结（批次 2C，与前端 Org.vue「公司名称/公司编码」同一规则）：名称与编码是人工维护的业务键
        AssertUtil.notBlank(company.getCompanyName(), ErrorCode.PARAM_INVALID, "请输入公司名称");
        AssertUtil.notBlank(company.getCompanyCode(), ErrorCode.PARAM_INVALID, "请输入公司编码");
        Long dup = countByCode(company.getCompanyCode());
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公司编码已存在: " + company.getCompanyCode());
        }
        company.setId(null);
        company.setAncestors(calcAncestors(company.getParentId()));
        // 默认集中模式：前端会显式提交 CENTRAL，这里兜住直调 API 的空值/非法值，
        // 避免落成空串（空串同样被 DeliveryService 当集中处理，但数据页显示为空）
        String mode = normalizeArchiveMode(company.getArchiveMode(), "CENTRAL");
        company.setArchiveMode(mode);
        sysCompanyMapper.insert(company);
        return company.getId();
    }

    public void update(SysCompany company) {
        SysCompany exist = sysCompanyMapper.selectById(company.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公司不存在: " + company.getId());
        }
        // 整单更新（前端提交完整对象），故与 create 同一规则拦空；enable() 走独立对象不受影响
        AssertUtil.notBlank(company.getCompanyName(), ErrorCode.PARAM_INVALID, "请输入公司名称");
        AssertUtil.notBlank(company.getCompanyCode(), ErrorCode.PARAM_INVALID, "请输入公司编码");
        // 更新时「未提交」不得被当成「改回集中」：留 null 让 MP 跳过该列，避免把非集中公司静默改回集中
        company.setArchiveMode(normalizeArchiveMode(company.getArchiveMode(), null));
        sysCompanyMapper.updateById(company);
    }

    /**
     * 档案管理模式取值办结：空值按 defaultMode 处理（create 传 CENTRAL、update 传 null＝不改）；
     * 非空必须是 CENTRAL/NON_CENTRAL，防止脏值绕过 DeliveryService 的等值判定。
     */
    private String normalizeArchiveMode(String mode, String defaultMode) {
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (m.isEmpty()) {
            return defaultMode;
        }
        if (!"CENTRAL".equals(m) && !"NON_CENTRAL".equals(m)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案管理模式取值非法: " + mode);
        }
        return m;
    }

    public void enable(Long id, boolean enable) {
        SysCompany u = new SysCompany();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        sysCompanyMapper.updateById(u);
    }

    /** 删除公司（逻辑删；有子公司时拒绝） */
    public void delete(Long id) {
        long sub = sysCompanyMapper.selectCount(new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getParentId, id));
        if (sub > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在下级公司，不能删除");
        }
        sysCompanyMapper.hardDelete(id);
    }

    private long countByCode(String code) {
        return sysCompanyMapper.selectCount(new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getCompanyCode, code));
    }

    /** 祖级路径：父为 0 → "0"；否则 父.ancestors + "," + 父.id */
    private String calcAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        SysCompany p = sysCompanyMapper.selectById(parentId);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上级公司不存在: " + parentId);
        }
        String a = p.getAncestors() == null || p.getAncestors().isBlank() ? "0" : p.getAncestors();
        return a + "," + parentId;
    }
}
