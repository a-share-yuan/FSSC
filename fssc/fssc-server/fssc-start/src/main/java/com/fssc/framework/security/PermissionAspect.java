// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.platform.entity.PfFeature;
import com.fssc.platform.mapper.PfFeatureMapper;
import com.fssc.system.entity.SysDept;
import com.fssc.system.mapper.SysDeptMapper;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ③ 权限防线：把注解翻译成运行期校验。四道防线中唯一"业务语义"的一层。
 * <ul>
 *   <li>@RequiresPerm：权限码必须在 LoginUser.perms 中。</li>
 *   <li>@RequiresFeature：功能码必须在 LoginUser.features（套餐已购）中。</li>
 *   <li>@PlatformOnly：主体必须是 PLATFORM。</li>
 *   <li>@DataScope：写入 DataScopeHolder（scope 取最宽，查不到默认最窄 5）。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private PfFeatureMapper pfFeatureMapper;

    @Around("@annotation(requiresPerm)")
    public Object aroundPerm(ProceedingJoinPoint pjp, RequiresPerm requiresPerm) throws Throwable {
        LoginUser u = UserContext.getRequired();
        String[] need = requiresPerm.value();
        // 通配权限：平台超管 / 租户管理员拥有 *:*:*，等同于全部权限
        boolean wildcard = u.getPerms() != null && u.getPerms().contains("*:*:*");
        if (!wildcard) {
            // 多码 OR：持有任意一个即通过；单码时退化为原等值语义。
            boolean ok = false;
            if (u.getPerms() != null) {
                for (String c : need) {
                    if (c != null && !c.isBlank() && u.getPerms().contains(c)) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                // 多码场景下把全部可接受码列出，便于运维定位应授哪个码（避免只报第一个造成误判）
                throw new BizException(ErrorCode.PERMISSION_DENIED,
                        "缺少权限：" + String.join(" 或 ", need));
            }
            // B 项：运行时兜底 —— 持有某权限码时，必须同时持有其前置权限码
            // （通用形态：查看是其余权限的前置。规则见 PermDependency 类注释）。
            // 为什么要放进切面：PermDependency.assertGrantClosure 只能在「角色授权保存期」拦，
            // 拦不住历史授权（缺陷前授的、或直接写库的）；assertHolds 只挂在
            // ArchiveAccessService 一处，导致「op:bank:import 却没有 op:bank:list」这类
            // 历史错配在运行期照样放行 —— 银行流水导入功能因此在 UI 上无人可用。
            // 多码 OR 场景：逐个已持有的码做依赖校验（只校验实际持有的那一个）。
            for (String c : need) {
                if (c != null && !c.isBlank() && u.getPerms().contains(c)) {
                    PermDependency.assertHolds(u, c);
                }
            }
        }
        return pjp.proceed();
    }

    @Around("@annotation(requiresFeature)")
    public Object aroundFeature(ProceedingJoinPoint pjp, RequiresFeature requiresFeature) throws Throwable {
        LoginUser u = UserContext.getRequired();
        if (u.getFeatures() == null || !u.getFeatures().contains(requiresFeature.value())) {
            // detail 只放功能名：BizException 会拼成「当前套餐未开通该功能：银企对账」。
            // 原实现再带一层「未开通功能：」前缀，读起来是「…：未开通功能：FSSC_BANK」的病句。
            throw new BizException(ErrorCode.FEATURE_DISABLED, featureLabel(requiresFeature.value()));
        }
        return pjp.proceed();
    }

    /**
     * 功能码 → 中文名（pf_feature.feature_name）。
     *
     * <p>这条消息是弹给业务用户的，直接抛码会变成「未开通功能：FSSC_BANK」——
     * 用户看不懂 FSSC_BANK 是什么，也无从判断该找谁开通。查库失败或功能点未登记时退回原码，
     * 保证「翻译」本身不会把一次正常的拦截变成 500。</p>
     */
    private String featureLabel(String code) {
        try {
            List<PfFeature> hit = pfFeatureMapper.selectList(
                    new LambdaQueryWrapper<PfFeature>().eq(PfFeature::getFeatureCode, code));
            if (!hit.isEmpty()) {
                String name = hit.get(0).getFeatureName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception e) {
            log.debug("功能码 {} 取中文名失败：{}", code, e.getMessage());
        }
        return code;
    }

    @Around("@annotation(platformOnly)")
    public Object aroundPlatform(ProceedingJoinPoint pjp, PlatformOnly platformOnly) throws Throwable {
        LoginUser u = UserContext.getRequired();
        if (!u.isPlatform()) {
            throw new BizException(ErrorCode.PLATFORM_ONLY);
        }
        return pjp.proceed();
    }

    @Around("@annotation(dataScope)")
    public Object aroundDataScope(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        LoginUser u = UserContext.getRequired();
        if (u.isPlatform()) {
            // 平台端跨租户聚合由 TenantContext.runIgnoreTenant 控制，此处不注入范围
            return pjp.proceed();
        }
        int scope = u.getDataScope() == null ? com.fssc.common.enums.DataScope.SELF.getCode() : u.getDataScope();
        // 部门/本部门及以下两级需本部门及其下级部门集合（仅本部门范围才计算，避免无谓查询）
        Long deptId = u.getDeptId();
        Set<Long> deptAndChild = null;
        if ((scope == com.fssc.common.enums.DataScope.DEPT_AND_CHILD.getCode()
                || scope == com.fssc.common.enums.DataScope.DEPT.getCode())
                && deptId != null) {
            deptAndChild = childDeptIds(u.getCompanyId(), deptId);
        }
        DataScopeHolder.set(scope, u.getCompanyId(), deptId, u.getUserId(), deptAndChild);
        try {
            return pjp.proceed();
        } finally {
            DataScopeHolder.clear();
        }
    }

    /** 计算本部门及其全部下级部门 ID（含本部门），用于 DEPT_AND_CHILD 范围。 */
    private Set<Long> childDeptIds(Long companyId, Long rootDeptId) {
        Set<Long> result = new HashSet<>();
        if (rootDeptId == null) {
            return result;
        }
        try {
            List<SysDept> all = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .eq(companyId != null, SysDept::getCompanyId, companyId));
            Map<Long, List<SysDept>> parentToChildren = new HashMap<>();
            for (SysDept d : all) {
                if (d.getParentId() != null) {
                    parentToChildren.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
                }
            }
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(rootDeptId);
            result.add(rootDeptId);
            while (!stack.isEmpty()) {
                Long cur = stack.pop();
                List<SysDept> children = parentToChildren.get(cur);
                if (children != null) {
                    for (SysDept c : children) {
                        if (result.add(c.getId())) {
                            stack.push(c.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("计算下级部门失败 company={} dept={}: {}", companyId, rootDeptId, e.getMessage());
        }
        return result;
    }
}
