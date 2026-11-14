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
 * 启用账号数上限校验（可选运维能力，开源发行版默认不启用）。
 *
 * <p>本项目是开源软件：档位只表示「装配哪些能力」，不含人数、公司数、月单据量、存储配额，
 * 也没有价格、席位或续费概念。{@code pf_package} 保留历史的 {@code price_*} / {@code max_*}
 * 列只为兼容既有库结构，发行版中一律保持 0；{@code max_user <= 0} 即视为不限，本方法直接放行。
 *
 * <p>若某个部署方出于自身运维需要自行把 {@code max_user} 设为正数，这里会按「启用账号数」
 * 做一次拦截；这属于部署方策略，不是产品档位差异，也不参与装配配方。
 * 取数规则：租户当前生效套餐（{@code pf_tenant_package.status=ACTIVE}）→ 套餐 {@code max_user}；
 * 无套餐行或取数失败＝不限，宁放行不误锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaService {

    private final PfTenantPackageMapper tenantPackageMapper;
    private final PfPackageMapper packageMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 新建用户前校验可选的「启用账号数上限」。开源发行版所有套餐该值均为 0，直接放行。
     *
     * @throws BizException 部署方自行设定上限且已达上限时抛出
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
        // 只计「启用」账号（status=NORMAL）：停用账号不再占用上限，避免停用后仍无法新增。
        long current = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getDelFlag, 0)
                .eq(SysUser::getStatus, "NORMAL"));
        if (current >= maxUser) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "已达系统设置的启用账号数上限（" + current + "/" + maxUser + " 人），无法新增用户；请联系系统管理员或停用不再使用的账号");
        }
        log.debug("租户 {} 启用账号数 {}/{}", tenantId, current, maxUser);
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
            // 取数失败时按不限处理，但要留日志，避免静默放行无从排查
            log.warn("读取租户 {} 账号数上限失败，本次按不限处理: {}", tenantId, ex.getMessage());
            return null;
        }
    }
}
