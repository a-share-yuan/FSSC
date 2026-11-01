// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充。INSERT 时填充审计字段 + 租户ID；UPDATE 时填充更新人/时间。
 * <p>
 * /314：审计四列 strict 语义——无条件覆盖（旧行回显/入参伪造不得冻结审计列）；
 * tenantId 入参携带值与登录上下文不一致时 fail-closed 抛错，不静默改写。
 * <p>
 * 审计字段按实体实际类型自适应：
 * <ul>
 *   <li>租户业务表（BaseEntity）：create_by/update_by 为 varchar → 填用户名（String）。</li>
 *   <li>平台表（PlatformBaseEntity）：create_by/update_by 为 bigint → 填用户ID（Long）。</li>
 * </ul>
 * 租户ID 来自 {@link TenantContext}（已验签 Token 建立的上下文），平台端 / 忽略表跳过。
 */
@Slf4j
@Component
public class FsscMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("insertFill called, hasTenantSetter={}, ctx={}/platform={}/ignore={}",
                metaObject.hasSetter("tenantId"),
                TenantContext.hasContext(), TenantContext.isPlatform(), TenantContext.isIgnoreTenant());
        LocalDateTime now = LocalDateTime.now();
        fillAudit(metaObject, "createBy", now);
        fillAudit(metaObject, "updateBy", now);
        fillTime(metaObject, "createTime", now);
        fillTime(metaObject, "updateTime", now);
        fillValue(metaObject, "delFlag", 0);

        // 租户ID：仅处于租户上下文且非聚合模式时填充；平台端跳过（pf_* 无此字段）
        if (TenantContext.hasContext() && !TenantContext.isPlatform() && !TenantContext.isIgnoreTenant()) {
            fillTenant(metaObject, TenantContext.getTenantId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // strictUpdateFill：无条件刷新，杜绝「旧值回显→填充被跳过→审计列冻结」
        fillAudit(metaObject, "updateBy", LocalDateTime.now());
        fillTime(metaObject, "updateTime", LocalDateTime.now());
    }

    // 工具

    /** 审计人字段：strict 语义无条件覆盖（/314）。String 填用户名，Long 填用户ID */
    private void fillAudit(MetaObject mo, String field, LocalDateTime now) {
        if (!mo.hasSetter(field)) {
            return;
        }
        Class<?> t = mo.getSetterType(field);
        LoginUser u = UserContext.get();
        Object v;
        if (t == Long.class || t == long.class) {
            v = (u != null && u.getUserId() != null) ? u.getUserId() : 0L;
        } else {
            v = (u != null && u.getUsername() != null) ? u.getUsername() : "system";
        }
        mo.setValue(field, v);
    }

    private void fillTime(MetaObject mo, String field, LocalDateTime now) {
        if (mo.hasSetter(field)) {
            mo.setValue(field, now);
        }
    }

    private void fillValue(MetaObject mo, String field, Object v) {
        if (mo.hasSetter(field) && mo.getValue(field) == null) {
            mo.setValue(field, v);
        }
    }

    /**
     * 租户列 fail-closed：入参携带 tenantId 且与登录上下文不一致即拒绝写入，不静默改写；
     * 与上下文一致等价放行；为空则按上下文填充。
     */
    private void fillTenant(MetaObject mo, Long tid) {
        if (!mo.hasSetter("tenantId")) {
            return;
        }
        Object cur = mo.getValue("tenantId");
        if (cur == null) {
            mo.setValue("tenantId", tid);
            return;
        }
        if (cur instanceof Number && ((Number) cur).longValue() == tid.longValue()) {
            return;
        }
        throw new BizException(ErrorCode.TENANT_CONTEXT_MISSING,
                "提交数据的租户归属与当前登录上下文不一致，已拒绝写入");
    }
}
