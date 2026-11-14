package com.fssc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.mapper.PfTenantMapper;
import com.fssc.system.entity.SysNumberRule;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysNumberRuleMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
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
    /** 开通模板（ 最小集：角色 + 编号规则 + 默认期间） */
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysNumberRuleMapper sysNumberRuleMapper;

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
            // 开通模板（ 荐解的最小三件：角色 / 编号规则 / 默认期间）。
            // 改造前 approve() 只建一个 admin 账号，新租户是空壳——无角色（零角色用户进不了任何候选组）、
            // 无编号规则（所有单据取号回退时间戳）、无期间，运营开通完还得手工补半天。
            // 其余模板（组织/账簿/科目/流程）按域分批立项，不在本方法里开造。
            provisionTenantTemplate(t, adminUserId);
        }
    }

    /** 开通模板：角色 + 编号规则 + 默认期间（幂等：已存在即跳过） */
    private void provisionTenantTemplate(PfTenant tenant, Long adminUserId) {
        TenantContext.callAs(tenant.getId(), () -> {
            // ① 角色：系统管理员（全数据）＋ 财务主管（本公司）＋ 普通员工（本人）
            Long adminRoleId = ensureRole("ADMIN", "系统管理员", 5, "开通模板内置角色");
            ensureRole("FIN_MANAGER", "财务主管", 1, "开通模板内置角色");
            ensureRole("EMPLOYEE", "普通员工", 4, "开通模板内置角色");
            // 管理员默认挂 ADMIN 角色，否则零角色用户连候选组通道都进不去
            if (sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, adminUserId)
                    .eq(SysUserRole::getRoleId, adminRoleId)) == 0) {
                SysUserRole ur = new SysUserRole();
                ur.setTenantId(tenant.getId());
                ur.setUserId(adminUserId);
                ur.setRoleId(adminRoleId);
                sysUserRoleMapper.insert(ur);
            }
            // ② 编号规则：单据号是"能不能开单"的前置，缺规则会静默退回时间戳号（不可读、不可对账）
            ensureNumberRule("EXP_CLAIM", "报销单", "BX", "yyyyMMdd", 5, "DAY");
            ensureNumberRule("EXP_APPLY", "申请单", "SQ", "yyyyMMdd", 5, "DAY");
            ensureNumberRule("EXP_PAY", "付款单", "FK", "yyyyMMdd", 5, "DAY");
            ensureNumberRule("EXP_LOAN", "借款单", "JK", "yyyyMMdd", 5, "DAY");
            ensureNumberRule("BG_SCHEME", "预算方案", "BGS", "yyyy", 3, "YEAR");
            ensureNumberRule("BUDGET_ADJUST", "预算调整单", "ADJ", "yyyyMMdd", 4, "DAY");
            ensureNumberRule("TAX_DECLARATION", "纳税申报单", "TX", "yyyyMM", 4, "MONTH");
            ensureNumberRule("PAY_BATCH", "付款批次", "PB", "yyyyMMdd", 4, "DAY");
        });
    }

    private Long ensureRole(String code, String name, int dataScope, String remark) {
        SysRole exist = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code)
                .last("limit 1"));
        if (exist != null) {
            return exist.getId();
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDataScope(dataScope);
        role.setStatus("NORMAL");
        role.setRemark(remark);
        sysRoleMapper.insert(role);
        return role.getId();
    }

    private void ensureNumberRule(String code, String name, String prefix, String dateFormat,
                                  int seqLength, String resetCycle) {
        if (sysNumberRuleMapper.selectCount(new LambdaQueryWrapper<SysNumberRule>()
                .eq(SysNumberRule::getRuleCode, code)) > 0) {
            return;
        }
        SysNumberRule r = new SysNumberRule();
        r.setRuleCode(code);
        r.setRuleName(name);
        r.setPrefix(prefix);
        r.setDateFormat(dateFormat);
        r.setSeqLength(seqLength);
        r.setResetCycle(resetCycle);
        r.setStep(1);
        r.setStatus("NORMAL");
        r.setRemark("开通模板内置编号规则");
        sysNumberRuleMapper.insert(r);
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
