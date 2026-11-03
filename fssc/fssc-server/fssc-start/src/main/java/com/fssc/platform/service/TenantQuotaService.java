// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfPackage;
import com.fssc.platform.entity.PfTenantPackage;
import com.fssc.platform.mapper.PfPackageMapper;
import com.fssc.platform.mapper.PfTenantPackageMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 租户套餐配额校验。
 *
 * <p>{@code pf_package} 有 max_user / max_company / max_storage_mb / max_api_daily / max_bill_month
 * 五列，仓内此前<b>无任何读取处</b>——套餐卖点是"50 人/200 人"，系统一点不拦，超卖是静默的。
 *
 * <p>本轮只落 {@code max_user}（ 荐解）：建用户这一处判，超限直接拒并给出可执行提示。
 * 其余四列<b>未实现</b>，不得在对外材料中承诺；表注与界面应标"未启用"。
 *
 * <p>取数规则：租户当前生效套餐（pf_tenant_package.status=ACTIVE，未过期）→ 套餐 max_user；
 * 无套餐行或 max_user 为空＝不限（历史租户按不限处理，避免升级即锁死）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaService {

    private final PfTenantPackageMapper tenantPackageMapper;
    private final PfPackageMapper packageMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 新建用户前校验人数配额。
     *
     * @throws BizException 超限时抛出（含当前人数与上限，便于运营判断该升配还是清人）
     */
    public void assertUserQuota() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        Integer maxUser = activeMaxUser(tenantId);
        if (maxUser == null || maxUser <= 0) {
            return;
        }
        long current = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getDelFlag, 0));
        if (current >= maxUser) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "已达套餐人数上限（" + current + "/" + maxUser + " 人），无法新增用户；请联系平台升级套餐或停用不再使用的账号");
        }
        log.debug("租户 {} 人数配额 {}/{}", tenantId, current, maxUser);
    }

    /** 当前生效套餐的 max_user；无套餐/无上限返回 null（＝不限） */
    private Integer activeMaxUser(Long tenantId) {
        try {
            PfTenantPackage tp = tenantPackageMapper.selectOne(new LambdaQueryWrapper<PfTenantPackage>()
                    .eq(PfTenantPackage::getRefTenantId, tenantId)
                    .eq(PfTenantPackage::getStatus, "ACTIVE")
                    .orderByDesc(PfTenantPackage::getId)
                    .last("limit 1"));
            if (tp == null || tp.getPackageId() == null) {
                return null;
            }
            PfPackage pkg = packageMapper.selectById(tp.getPackageId());
            return pkg == null ? null : pkg.getMaxUser();
        } catch (Exception ex) {
            // 配额取数失败不阻断建人（宁放行不误锁）；但要留日志，否则"配额形同虚设"会再次静默
            log.warn("读取租户 {} 套餐配额失败，本次按不限处理: {}", tenantId, ex.getMessage());
            return null;
        }
    }
}
