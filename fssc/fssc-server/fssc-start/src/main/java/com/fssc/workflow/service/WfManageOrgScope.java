package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.enums.DataScope;
import com.fssc.framework.security.DataScopeHolder;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * <p><b>开关（fail-closed，缺省关）</b>：{@code sys_config.wf.manage.org-isolated}
 * —— 缺省（无记录 / 非 '1'）时本类<b>不做任何过滤</b>，与改造前行为完全一致；
 * 这是必须的：租户内流程数据是否按组织收口属"管理策略"，不该由一次代码升级替租户拍板。</p>
 *
 * <p><b>为什么复用 @DataScope 而不另造一套</b>：项目已有 data_scope 五级过滤（{@code PermissionAspect}
 * 写入 {@link DataScopeHolder}、各 Service 读取），组织维度的取数、最宽优先级、fail-closed 默认值
 * 都已在那里定版。本类只做一件事——在开关打开时，把"流程实例按发起人所属公司"这一个
 * <b>流程域专有维度</b>接进既有载体，不复制范围判定逻辑：
 * 范围 &le; 1（本租户全部）不动；其余档位按登录人 companyId 收口。</p>
 *
 * <p><b>为什么按"发起人所属公司"而不是读实例上的公司列</b>：{@code wf_instance} 无 company_id
 * （只有 start_user_id / start_dept_id），给该表加列属结构变更且需回填存量数据；而
 * "单据属于哪个组织"在本库里等价于"提单人属于哪个公司"（{@code OrgGuard} 亦以 company_id 为唯一组织维）。
 * 故这里用 {@code start_user_id IN (本公司用户)} 表达同一语义，零 DDL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfManageOrgScope {

    /** 组织隔离开关键（缺省 0=关闭，零行为变化） */
    public static final String KEY_ORG_ISOLATED = "wf.manage.org-isolated";

    private final ConfigService configService;
    private final SysUserMapper sysUserMapper;

    /** 开关是否打开（只有显式 '1' 才算开；缺省/读取失败均为关，保持既有行为） */
    public boolean enabled() {
        try {
            String v = configService.getValue(KEY_ORG_ISOLATED);
            return v != null && "1".equals(v.trim());
        } catch (Exception ex) {
            // 读取失败一律按"关"处理：本开关是新增的收口能力，不应因一次 DB 抖动让管理列表凭空变空
            log.warn("读取 {} 失败，按缺省（不隔离）处理: {}", KEY_ORG_ISOLATED, ex.getMessage());
            return false;
        }
    }

    /**
     * 按组织范围收口流程实例查询（仅在开关打开且登录人非集团级全量范围时生效）。
     *
     * @param qw 实例查询构造器（会被原地修改）
     */
    public void applyInstanceScope(LambdaQueryWrapper<WfInstance> qw) {
        if (!enabled()) {
            return;
        }
        Long companyId = resolveScopeCompanyId();
        if (companyId == null) {
            return; // 集团级账号 / 本租户全部范围：不限制
        }
        List<Long> userIds = companyUserIds(companyId);
        if (userIds.isEmpty()) {
            // fail-closed：本公司取不到任何用户时收敛为空集，而不是静默放开全租户
            qw.eq(WfInstance::getId, -1L);
            return;
        }
        qw.in(WfInstance::getStartUserId, userIds);
    }

    /** 生效的组织范围：优先取 @DataScope 载体（与全站数据权限同源），载体缺失时退回登录人公司 */
    private Long resolveScopeCompanyId() {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s != null) {
            if (s.getScope() <= DataScope.TENANT.getCode()) {
                return null; // 本租户全部：集团/共享岗可见全量，隔离对这类账号无意义
            }
            return s.getCompanyId();
        }
        LoginUser u = UserContext.get();
        return u == null ? null : u.getCompanyId();
    }

    /** 本公司（含 NULL 公司不纳入）的在职用户 ID；租户条件由拦截器注入 */
    private List<Long> companyUserIds(Long companyId) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getCompanyId, companyId)
                .eq(SysUser::getStatus, "NORMAL"));
        List<Long> ids = new ArrayList<>(users.size());
        for (SysUser u : users) {
            if (u.getId() != null) {
                ids.add(u.getId());
            }
        }
        return ids;
    }
}