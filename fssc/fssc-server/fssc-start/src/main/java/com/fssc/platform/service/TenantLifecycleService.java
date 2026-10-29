// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.mapper.PfTenantMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 租户生命周期服务（状态机）：
 * APPLYING 待审核 → NORMAL 正常 → FROZEN 冻结 → CANCELLED 已注销；
 * APPLYING → REJECTED 已拒绝；FROZEN → NORMAL（解冻）。
 * 过期自动冻结由 PfTenantStatusValidator 在请求入口实时拦截（懒冻结）。
 */
@Service
@RequiredArgsConstructor
public class TenantLifecycleService {

    private final PfTenantMapper pfTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /** 自助注册开关（默认关闭，财务规范要求仅管理员邀请） */
    @Value("${fssc.self-registration.enabled:false}")
    private boolean selfRegistrationEnabled;

    /**
     * 租户管理员初始口令：改由配置注入，缺省为空＝fail-closed。
     * 原实现是一把 {@code public static final} 明文常量，且同一字面量被抄进前端注册成功提示与登录表单预填
     * ⇒ 任何读过发布件的人都可用它登录「新开通租户」的管理员账号。宁可不开通，也不用一把人人可读的固定口令。
     */
    @Value("${fssc.tenant.init-admin-pwd:}")
    private String initAdminPwd;

    /** 新增租户（待审核） */
    public Long create(PfTenant tenant) {
        PfTenant exist = pfTenantMapper.findByCode(tenant.getTenantCode());
        if (exist != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户编码已存在: " + tenant.getTenantCode());
        }
        tenant.setId(null);
        tenant.setStatus("APPLYING");
        tenant.setEffectTime(null);
        pfTenantMapper.insert(tenant);
        return tenant.getId();
    }

    /**
     * 企业自助注册（公开接口，无租户上下文）。
     * 校验编码格式与唯一性 → 写入数据库 APPLYING（待平台审核）。
     * 审核通过后由 {@link #approve(Long)} 自动创建租户管理员并开通。
     */
    public Long register(String tenantCode, String tenantName, String shortName,
                         String creditCode, String industry, String scale,
                         String contactName, String contactPhone, String contactEmail, String remark) {
        // 自助注册开关校验
        if (!selfRegistrationEnabled) {
            throw new BizException(ErrorCode.FORBIDDEN, "自助注册已关闭，请联系平台管理员");
        }
        String code = tenantCode == null ? "" : tenantCode.trim().toUpperCase();
        if (code.length() < 2 || code.length() > 20 || !code.matches("[A-Z0-9_]+")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业编码仅支持 2-20 位大写字母/数字/下划线");
        }
        PfTenant exist = pfTenantMapper.findByCode(code);
        if (exist != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业编码已存在: " + code);
        }
        PfTenant t = new PfTenant();
        t.setTenantCode(code);
        t.setTenantName(tenantName == null ? "" : tenantName.trim());
        t.setShortName(shortName == null ? "" : shortName.trim());
        t.setCreditCode(creditCode == null ? "" : creditCode.trim());
        t.setIndustry(industry);
        t.setScale(scale);
        t.setContactName(contactName == null ? "" : contactName.trim());
        t.setContactPhone(contactPhone == null ? "" : contactPhone.trim());
        t.setContactEmail(contactEmail);
        t.setRemark(remark);
        t.setStatus("APPLYING");
        pfTenantMapper.insert(t);
        return t.getId();
    }

    /** 审核通过：APPLYING → NORMAL，默认有效期 1 年；并自动创建租户管理员（开通闭环） */
    public void approve(Long id) {
        PfTenant t = require(id);
        if (!"APPLYING".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅待审核租户可审核通过，当前状态: " + t.getStatus());
        }
        PfTenant update = new PfTenant();
        update.setId(id);
        update.setStatus("NORMAL");
        update.setEffectTime(LocalDateTime.now());
        if (t.getExpireTime() == null) {
            update.setExpireTime(LocalDateTime.now().plusYears(1));
        }
        // 初始口令未注入时在改状态之前拦截，避免「租户已 NORMAL、管理员却建不出来」的半态
        if (t.getAdminUserId() == null && !StringUtils.hasText(initAdminPwd)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置租户管理员初始口令（fssc.tenant.init-admin-pwd），审核开通已拦截；请由运维注入一次性口令后重试");
        }
        pfTenantMapper.updateById(update);

        // 开通闭环：创建租户管理员 sys_user（is_admin=1），回写 pf_tenant.admin_user_id
        if (t.getAdminUserId() == null) {
            Long adminUserId = createTenantAdmin(t);
            PfTenant backfill = new PfTenant();
            backfill.setId(id);
            backfill.setAdminUserId(adminUserId);
            pfTenantMapper.updateById(backfill);
        }
    }

    /** 在租户上下文内创建管理员：user_name = admin_{tenantCode 小写}，初始口令取配置项 {@code fssc.tenant.init-admin-pwd} */
    private Long createTenantAdmin(PfTenant tenant) {
        final Long[] adminId = new Long[1];
        TenantContext.callAs(tenant.getId(), () -> {
            SysUser admin = new SysUser();
            admin.setTenantId(tenant.getId()); // 显式设置，不依赖 MetaObjectHandler
            admin.setUserName("admin_" + tenant.getTenantCode().toLowerCase());
            admin.setNickName(tenant.getShortName() == null ? tenant.getTenantName() + "管理员" : tenant.getShortName() + "管理员");
            admin.setPassword(passwordEncoder.encode(initAdminPwd));
            admin.setStatus("NORMAL");
            admin.setIsAdmin(1);
            admin.setDelFlag(0);
            sysUserMapper.insert(admin);
            adminId[0] = admin.getId();
        });
        return adminId[0];
    }

    /** 拒绝：APPLYING → REJECTED */
    public void reject(Long id, String reason) {
        PfTenant t = require(id);
        if (!"APPLYING".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅待审核租户可拒绝，当前状态: " + t.getStatus());
        }
        PfTenant update = new PfTenant();
        update.setId(id);
        update.setStatus("REJECTED");
        update.setRejectReason(reason);
        pfTenantMapper.updateById(update);
    }

    /** 冻结：NORMAL → FROZEN（欠费/违规） */
    public void freeze(Long id, String reason) {
        PfTenant t = require(id);
        if (!"NORMAL".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅正常租户可冻结，当前状态: " + t.getStatus());
        }
        PfTenant update = new PfTenant();
        update.setId(id);
        update.setStatus("FROZEN");
        update.setFreezeReason(reason);
        pfTenantMapper.updateById(update);
    }

    /** 解冻：FROZEN → NORMAL */
    public void unfreeze(Long id) {
        PfTenant t = require(id);
        if (!"FROZEN".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅冻结租户可解冻，当前状态: " + t.getStatus());
        }
        PfTenant update = new PfTenant();
        update.setId(id);
        update.setStatus("NORMAL");
        update.setFreezeReason(null);
        pfTenantMapper.updateById(update);
    }

    /** 注销：NORMAL/FROZEN → CANCELLED */
    public void cancel(Long id) {
        PfTenant t = require(id);
        if (!"NORMAL".equals(t.getStatus()) && !"FROZEN".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅正常/冻结租户可注销，当前状态: " + t.getStatus());
        }
        PfTenant update = new PfTenant();
        update.setId(id);
        update.setStatus("CANCELLED");
        pfTenantMapper.updateById(update);
    }

    public PageResult<PfTenant> page(long pageNum, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<PfTenant> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(PfTenant::getTenantName, keyword)
                    .or().like(PfTenant::getTenantCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(PfTenant::getStatus, status);
        }
        qw.orderByDesc(PfTenant::getId);
        Page<PfTenant> p = pfTenantMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public PfTenant detail(Long id) {
        return require(id);
    }

    private PfTenant require(Long id) {
        PfTenant t = pfTenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "租户不存在: " + id);
        }
        return t;
    }
}
