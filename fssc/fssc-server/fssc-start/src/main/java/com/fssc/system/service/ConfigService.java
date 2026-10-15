package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 租户参数配置（sys_config，带 tenant_id）。editable=0 为平台下发只读。 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final com.fssc.system.mapper.SysUserMapper sysUserMapper;

    /**
     * 平台属主参数键前缀（G4 参数权属显式化，2026-09-24）。
     *
     * 一般有管理员权限的用户设置也不生效」。而改造前 {@code sys_config} 的写入口对 wf./approve. 这些
     * <b>全局流程语义开关</b>（如 {@code approve.deny_self}、{@code wf.timeout.autopass.nodes}）
     * 只校验 {@code editable} 列，任何持 {@code system:config:update} 的租户管理员都能改——
     * 一处误改即改变全租户的审批控制流，且无属主可追。
     *
     * <p>闸门只作用于这两个前缀，<b>其它域配置的现有行为完全不变</b>（本类其余方法亦不改）。
     */
    private static final java.util.List<String> PLATFORM_OWNED_PREFIXES = java.util.List.of("wf.", "approve.");

    public PageResult<SysConfig> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysConfig> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysConfig::getConfigKey, keyword)
                    .or().like(SysConfig::getConfigName, keyword));
        }
        qw.orderByAsc(SysConfig::getId);
        Page<SysConfig> p = sysConfigMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(SysConfig config) {
        assertPlatformOwned(config.getConfigKey());
        long dup = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, config.getConfigKey()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数键已存在: " + config.getConfigKey());
        }
        config.setId(null);
        sysConfigMapper.insert(config);
        return config.getId();
    }

    public void update(SysConfig config) {
        SysConfig exist = sysConfigMapper.selectById(config.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不存在: " + config.getId());
        }
        // 属主闸：改键与改值都要过（否则可"先改名绕闸、再改值"）
        assertPlatformOwned(exist.getConfigKey());
        assertPlatformOwned(config.getConfigKey());
        if (exist.getEditable() != null && exist.getEditable() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该参数为平台下发只读，租户不可修改");
        }
        sysConfigMapper.updateById(config);
    }

    /** 工作流域参数键是否受平台属主约束（键名前缀 wf. 或 approve.） */
    private static boolean isPlatformOwned(String configKey) {
        if (configKey == null) {
            return false;
        }
        String k = configKey.trim().toLowerCase();
        for (String p : PLATFORM_OWNED_PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 工作流域参数属主校验（G4）：只有平台 administrator 可写，其余一律拒绝。
     *
     * <p><b>administrator 的真实判定依据（库内实测）</b>：{@code sys_user.is_admin = 1}
     * —— 当前库中同时满足 {@code user_name='admin'} 与 {@code is_admin=1} 的账号只有一个
     * （id=1 / tenant_id=1），与 {@code NodeOrgResolver.isAdministrator()} 全仓口径一致
     * （is_admin=1 <b>或</b> user_name='admin'）；此外 {@code LoginUser.isAdmin()}（perms 含
     * {@code *:*:*}）亦视为平台超管。三者取并集，避免出现"铁律判定说他是 admin、参数闸说不算"的漂移。</p>
     */
    private void assertPlatformOwned(String configKey) {
        if (!isPlatformOwned(configKey)) {
            return; // 非工作流域参数：行为与改造前完全一致
        }
        if (isPlatformAdministrator()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "工作流参数「" + configKey + "」只能由平台 administrator 设置（工作流参数与组织无关，"
                        + "普通管理员设置不生效）。请用平台 administrator 账号操作，或联系平台运维下发该参数。");
    }

    /** 当前登录人是否平台 administrator：perms 含 *:*:* ∪ sys_user.is_admin=1 ∪ user_name='admin' */
    private boolean isPlatformAdministrator() {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.get();
        if (u == null) {
            return false;
        }
        if (u.isAdmin()) {
            return true;
        }
        if (u.getUserId() == null) {
            return false;
        }
        com.fssc.system.entity.SysUser su = sysUserMapper.selectById(u.getUserId());
        if (su == null) {
            return false;
        }
        return (su.getIsAdmin() != null && su.getIsAdmin() == 1)
                || "admin".equalsIgnoreCase(su.getUserName());
    }

    /**
     * 按 key 读单值（租户隔离），供其他域 Service 读取开关类配置。
     * 分库改造：sys_config 在 master 库，方法级 master 路由 + NOT_SUPPORTED
     * 挂起外层（可能为 image 库）事务后取 master 连接，避免跨库复用连接。
     */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public String getValue(String key) {
        Long tenantId = TenantContext.getTenantId();
        SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getTenantId, tenantId)
                .last("limit 1"));
        return c == null ? null : c.getConfigValue();
    }

    /**
     * 按 key 写单值（存在则更新，不存在则新建；供业务域做开关/锁标记，如账期锁定）。
     * 系统标记类写入不受 editable=0 限制（该限制仅针对租户侧手工 update 入口）。
     */
    @FsscDataSource("master")
    public void setValue(String key, String value, String name) {
        Long tenantId = TenantContext.getTenantId();
        SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getTenantId, tenantId)
                .last("limit 1"));
        if (c == null) {
            SysConfig n = new SysConfig();
            n.setTenantId(tenantId);
            n.setConfigKey(key);
            n.setConfigName(name == null ? key : name);
            n.setConfigValue(value);
            n.setValueType("STRING");
            n.setEditable(1);
            sysConfigMapper.insert(n);
        } else {
            SysConfig u = new SysConfig();
            u.setId(c.getId());
            u.setConfigValue(value);
            sysConfigMapper.updateById(u);
        }
    }
}
