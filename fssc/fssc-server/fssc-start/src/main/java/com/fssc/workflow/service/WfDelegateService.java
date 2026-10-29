// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.workflow.entity.WfDelegate;
import com.fssc.workflow.mapper.WfDelegateMapper;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 流程委托代理（wf_delegate，带 tenant_id）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfDelegateService {

    private final WfDelegateMapper delegateMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    /** A2①：受托人须持有"未停用"的角色，判定要读到角色本体的 status */
    private final SysRoleMapper sysRoleMapper;
    /** 租户参数（sys_config）：跨公司委托白名单，每次实时读取，改配置无需重启 */
    private final ConfigService configService;
    /** 站内信发信通道（委托建立/改派/回跳三处出声） */
    private final NotifyService notifyService;
    /**
     * 委托改派 / 回跳的执行通道：走引擎 {@code taskService.setAssignee} + 待办镜像 + 记录，
     * 本服务不直写 {@code wf_task}（直写会让 Flowable 与镜像两套数据分叉，正是本轮体检要消灭的病根）。
     */
    private final WfEngineFacade wfEngineFacade;

    /**
     * 跨公司委托白名单（sys_config.config_key），值为逗号分隔的 {@code 委托方公司>受托方公司}，
     * 两侧都支持 {@code *} 通配（如 {@code 33>1,1>33} 表示允许昆山与集团互委；
     * {@code *>1} 表示任何公司都可以委托给集团）。
     *
     * <p><b>缺省＝全禁</b>（fail-closed）：现网 {@code sys_user.company_id} 有 5 家公司
     * （1 集团 / 31 销售 / 32 湖南 / 33 昆山 / 35 研发），而此前 {@link #create} 只校验了
     * U9 的四条规则（自己 / 可用 / 同期唯一 / 循环），**没有公司维度校验**——实测
     * 3818（昆山）成功委托给 3797（集团），且受托人随后真的办理了该公司的任务、
     * 记录操作人跨了公司（2026-09-23 运行期实证，见 docs/委托代理治理审计报告.md）。
     */
    static final String CROSS_COMPANY_WHITELIST_KEY = "delegate.cross_company.whitelist";

    /** {@link #page} 的三档可见范围（String 常量，switch 需编译期常量）。 */
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_AGENT = "agent";
    public static final String SCOPE_ALL = "all";

    /** {@link #assertNoCircularChain} 沿链走样的深度上限（每跳一次库查，超限只记日志不拒绝）。 */
    private static final int MAX_DELEGATE_CHAIN_DEPTH = 10;

    /**
     * 委托列表的身份可见范围（E-12 / 方案）。
     *
     * <p><b>为什么要过滤</b>：此端点原先没有任何身份条件，任何持有 {@code wf:delegate:list}
     * 的普通员工都能翻到<b>全租户</b>的委托安排——谁把审批权临时借给了谁、借到哪天、覆盖哪些单据。
     * 委托关系本身是审批授权信息，2026-12-25 演练中「我的委托代理」页把他人安排一并列出即为此缺口。
     *
     * <p><b>规则</b>：{@code mine}（本人作为委托人，缺省）/ {@code agent}（由本人代办）/
     * {@code all}（全量，仅无公司维度的集团级账号）。公司维度账号请求 {@code all} 直接 403，
     * 不做静默降级——列表少了几行却看不出原因，比报错更难排查。
     */
    public PageResult<WfDelegate> page(long pageNum, long pageSize, Long fromUserId, Long toUserId, String scope) {
        LoginUser u = UserContext.getRequired();
        String s = scope == null || scope.isBlank() ? SCOPE_MINE : scope.trim().toLowerCase();
        LambdaQueryWrapper<WfDelegate> qw = new LambdaQueryWrapper<>();
        switch (s) {
            case SCOPE_ALL -> {
                if (u.getCompanyId() != null) {
                    throw new BizException(ErrorCode.FORBIDDEN,
                            "全量委托列表仅集团级账号可查（当前账号属公司 " + u.getCompanyId()
                                    + "）；请改为查看「我委托的」或「由我代办」");
                }
                if (fromUserId != null) {
                    qw.eq(WfDelegate::getFromUserId, fromUserId);
                }
                if (toUserId != null) {
                    qw.eq(WfDelegate::getToUserId, toUserId);
                }
            }
            case SCOPE_AGENT -> qw.eq(WfDelegate::getToUserId, u.getUserId());
            default -> qw.eq(WfDelegate::getFromUserId, u.getUserId());
        }
        qw.orderByDesc(WfDelegate::getId);
        Page<WfDelegate> p = delegateMapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillUserNames(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 列表展示回填：实体只存 {@code fromUserId/toUserId}，前端两列要显示姓名。
     * 一次 {@code selectBatchIds} 取齐，避免逐行查（N+1）。
     */
    private void fillUserNames(List<WfDelegate> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (WfDelegate r : rows) {
            if (r.getFromUserId() != null) {
                ids.add(r.getFromUserId());
            }
            if (r.getToUserId() != null) {
                ids.add(r.getToUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
            nameById.put(u.getId(), u.getNickName() == null || u.getNickName().isBlank()
                    ? u.getUserName() : u.getNickName());
        }
        for (WfDelegate r : rows) {
            r.setFromUserName(nameById.get(r.getFromUserId()));
            r.setToUserName(nameById.get(r.getToUserId()));
        }
    }

    public Long create(WfDelegate delegate) {
        if (delegate.getFromUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择委托人");
        }
        if (delegate.getToUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择代理人");
        }
        // 越权修复：委托人必须是当前登录用户，杜绝请求体伪造他人 fromUserId
        LoginUser u = UserContext.getRequired();
        if (!Objects.equals(u.getUserId(), delegate.getFromUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能委托本人的待办");
        }
        if (delegate.getBeginTime() == null || delegate.getEndTime() == null
                || !delegate.getEndTime().isAfter(delegate.getBeginTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "委托时间区间不合法");
        }
        // 委托代理的硬规则（方案 §11.2 A-4）：
        // ① 不能委托给自己 —— 否则"不能审自己提交的单"可被自己给自己代办合法绕开；
        // ② 受托人必须是可办理的真人（存在/在职/有角色/非 admin，铁律禁 admin 参与任何节点）；
        // ③ 同一时间只能委托给一个人（U9 原文："若委托给多人，系统只会给其中一人转发"——与其运行期挑一个，
        //    不如在配置期就拒绝歧义）；④ 禁止循环委托（A2②：沿委托链逐跳走样，A→B→C→A 也算回环）；
        // ⑤ 公司维度边界（2026-09-23 补，原四条之外新增）——默认禁止跨公司，避免 A 公司的单据
        //    被 B 公司的人代审：委托只是把"办理资格"临时借出，但写入数据库的 oper_user_id 是受托人本人，
        //    于是审批记录上会出现不属于该公司的经办人（数据隔离/内控可见性问题）。
        // 「只支持一级委托」无需实现：canOperate 只做受托人→委托人一跳，不存在链式递归。
        if (Objects.equals(delegate.getFromUserId(), delegate.getToUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把待办委托给自己");
        }
        assertDelegateTargetUsable(delegate.getToUserId());
        // ⑤ 公司维度边界（2026-09-23 补）：默认禁止跨公司委托，命中白名单才放行
        assertCompanyBoundary(delegate.getFromUserId(), delegate.getToUserId());
        LocalDateTime begin = delegate.getBeginTime(), end = delegate.getEndTime();
        if (countActiveInRange(delegate.getFromUserId(), null, begin, end) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该时间段你已有一条生效中的委托（同一时间只能委托给一个人），请先取消或调整区间");
        }
        assertNoCircularChain(delegate.getFromUserId(), delegate.getToUserId(), begin, end);
        delegate.setId(null);
        if (delegate.getStatus() == null) {
            delegate.setStatus("NORMAL");
        }
        delegateMapper.insert(delegate);
        // 通知补全：委托建立当即告知受托人——改造前只有"办结响应"，受托人从来不知道自己被委托了。
        notifyDelegateCreated(delegate);
        // 历史改派：窗口已开（begin<=now）才动别人的待办，未来生效的委托由
        // {@code WfDelegateExpireJob} 的生效扫描在窗口开启那一刻补做，避免"提前建委托就提前抢单"。
        if (delegate.getBeginTime() == null || !delegate.getBeginTime().isAfter(LocalDateTime.now())) {
            try {
                int moved = wfEngineFacade.takeOverDelegatedTasks(
                        delegate.getFromUserId(), delegate.getToUserId(), delegate.getId());
                if (moved > 0) {
                    log.info("委托建立即改派存量待办: delegate={} {} -> {} 共 {} 条",
                            delegate.getId(), delegate.getFromUserId(), delegate.getToUserId(), moved);
                }
            } catch (Exception ex) {
                // 改派失败不影响委托本身（借资格通道仍然可用），但必须冒泡到日志：静默失败＝又是"配了不生效"
                log.error("委托存量改派失败 delegate={}: {}", delegate.getId(), ex.getMessage(), ex);
            }
        }
        return delegate.getId();
    }

    /**
     * 委托建立通知受托人。发信失败不阻断委托本身——委托是配置动作，通知是附加出声，
     * 但异常必须留日志，否则受托人"被委托却不知情"这一条又回到无声状态。
     */
    private void notifyDelegateCreated(WfDelegate delegate) {
        try {
            String fromName = displayName(delegate.getFromUserId());
            String window = formatWindow(delegate.getBeginTime(), delegate.getEndTime());
            String scope = delegate.getBizTypes() == null || delegate.getBizTypes().isBlank()
                    ? "全部待办" : "指定单据类型：" + delegate.getBizTypes();
            notifyService.notifyUser(delegate.getToUserId(), "DELEGATE_NEW", "收到委托代办",
                    fromName + " 已将其" + scope + "委托给您代办，有效期 " + window
                            + "；期内该范围内的待办会出现在您的待办列表中",
                    delegate.getId(), "/approval");
        } catch (Exception ex) {
            log.warn("委托建立通知失败 delegate={}: {}", delegate.getId(), ex.getMessage());
        }
    }

    /** 用户展示名（姓名优先，退登录名，再退用户 ID）；查不到不抛异常。 */
    private String displayName(Long userId) {
        if (userId == null) {
            return "未知用户";
        }
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null) {
                return String.valueOf(userId);
            }
            if (u.getNickName() != null && !u.getNickName().isBlank()) {
                return u.getNickName();
            }
            return u.getUserName() == null ? String.valueOf(userId) : u.getUserName();
        } catch (Exception ex) {
            return String.valueOf(userId);
        }
    }

    private String formatWindow(LocalDateTime begin, LocalDateTime end) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return (begin == null ? "—" : f.format(begin)) + " ~ " + (end == null ? "—" : f.format(end));
    }

    /**
     * 委托取消：改派档下必须把"当初转出去、至今仍未被办"的待办回跳给委托人，
     * 否则取消委托只是撤掉了授权，单子却永久留在受托人名下（体检 E-07 的反向缺口）。
     * 只读判定（当前 assignee 仍是受托人才回跳）由 {@code revertDelegatedTasks} 负责。
     */
    public void cancel(Long id) {
        WfDelegate exist = delegateMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "委托不存在: " + id);
        }
        // 越权修复：只能取消本人的委托，集团级账号（无公司）除外
        LoginUser u = UserContext.getRequired();
        if (u.getCompanyId() != null && !Objects.equals(u.getUserId(), exist.getFromUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能取消本人的委托");
        }
        WfDelegate d = new WfDelegate();
        d.setId(id);
        d.setStatus("DISABLED");
        delegateMapper.updateById(d);
        // 反向回跳：撤销授权的同时把还压在受托人名下的待办收回来（已办的不动）。
        // 缺这一步，"取消委托"只改了配置表，单子却永久留在别人待办里＝又造一个配置与行为分叉。
        try {
            int back = wfEngineFacade.revertDelegatedTasks(exist.getFromUserId(), exist.getToUserId(),
                    exist.getBeginTime(), exist.getEndTime());
            if (back > 0) {
                log.info("委托取消回跳: delegate={} {} <- {} 共 {} 条",
                        id, exist.getFromUserId(), exist.getToUserId(), back);
            }
        } catch (Exception ex) {
            log.error("委托取消回跳失败 delegate={}: {}", id, ex.getMessage(), ex);
        }
    }

    /**
     * 受托人可用性校验（A2 第①条硬约束，人工裁决 RA-F4a 前置）：
     * 存在 + 本人可登录（status=NORMAL）+ 非 admin + <b>持有至少一个未停用的角色</b>。
     *
     * <p>规则来源：〔E·BOSBCFG·8〕委托的资格是"借"给一个<b>能办事的操作员</b>；同类做法
     * （「代理人只可为『操作员』」〔丙·开发指南〕）。只数 {@code sys_user_role} 的行数不够——
     * 角色本身可以停用（{@code sys_role.status=DISABLE}，现网 4 条 / 4 个用户持有），
     * 停用角色运行期取不到任何权限，委托过去等于把单据塞进一个谁也办不动的收件箱（静默吞单）。
     *
     * <p>现网影响面（只读普查 {@code 临时脚本/_proof/a2_roles2.txt}）：持有"全部角色已停用"的用户
     * <b>0 人</b>，故本条对历史委托零行为变化，只是把口子焊上。
     *
     * <p>admin 是铁律红线（任何流程节点不得由 admin 办理）。
     */
    private void assertDelegateTargetUsable(Long toUserId) {
        SysUser su = toUserId == null ? null : sysUserMapper.selectById(toUserId);
        if (su == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "受托人不存在（ID " + toUserId + "）");
        }
        if ("admin".equals(su.getUserName()) || Integer.valueOf(1).equals(su.getIsAdmin())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "受托人不能是系统管理员 admin —— 任何流程节点都不允许 admin 办理");
        }
        if (!"NORMAL".equals(su.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "受托人 " + su.getNickName() + " 状态为 " + su.getStatus() + "，无法代为办理");
        }
        List<Long> roleIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, toUserId))
                .stream().map(SysUserRole::getRoleId).filter(Objects::nonNull)
                .toList();
        if (roleIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "受托人 " + su.getNickName() + "（" + su.getUserName() + "）未持有任何角色，委托后无人可办");
        }
        Long usable = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, "NORMAL"));
        if (usable == null || usable == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "受托人 " + su.getNickName() + "（" + su.getUserName() + "）持有的角色已全部停用，"
                            + "委托后取不到任何办理权限——请先在 系统>角色管理 启用角色或改选受托人");
        }
    }

    /**
     * 公司维度边界校验（2026-09-23 补，方案 §1.4-2）。
     *
     * <p><b>缺口的实证</b>：{@code create()} 原有四条 U9 规则全都与"人"有关，没有任何一条看"公司"，
     * 而现网 {@code sys_user.company_id} 有 5 家公司。运行期实测：
     * <pre>
     *   3818 陈修利（company 33 昆山）→ 3797 付程鹏（company 1 集团）：HTTP 200，委托建立成功
     *   随后 3819（company 32）作为受托人成功办理了 company 1 的任务，wf_task_history.oper_user_id=3819
     * </pre>
     * 即"跨公司代办"不仅能建立、而且真的能落地执行，审批记录上出现外公司的经办人。
     *
     * <p><b>判定规则</b>：
     * <ol>
     *   <li>同公司（含两侧都为 null）→ 放行；</li>
     *   <li>任一侧公司为 null（无公司维度的集团级账号）→ 放行但记日志——这不构成
     *       "A 公司单被 B 公司人代审"，且与 {@code NodeOrgResolver.canOperate}
     *       的既有规则（用户公司为 null → 放宽）保持一致；</li>
     *   <li>其余跨公司情形 → 查白名单 {@link #CROSS_COMPANY_WHITELIST_KEY}，命中放行（记 warn），
     *       未命中 **fail-closed 拒绝**。</li>
     * </ol>
     *
     * <p>选择 fail-closed 而非 fail-open：跨公司委托是"临时借出审批权"，属内控敏感动作，
     * 默认拒绝 + 显式白名单放行，与项目内 admin 禁入、空策略默认 ERROR 等校验同一风格。
     */
    private void assertCompanyBoundary(Long fromUserId, Long toUserId) {
        Long fromCompany = companyOf(fromUserId);
        Long toCompany = companyOf(toUserId);
        if (Objects.equals(fromCompany, toCompany)) {
            return;
        }
        if (fromCompany == null || toCompany == null) {
            log.info("委托涉及无公司维度账号（fromCompany={}, toCompany={}），按既有口径放行 "
                    + "（与 canOperate 的 null 放宽一致）", fromCompany, toCompany);
            return;
        }
        if (crossCompanyAllowed(fromCompany, toCompany)) {
            log.warn("跨公司委托放行（白名单命中）：公司 {} → 公司 {}（from={}, to={}）",
                    fromCompany, toCompany, fromUserId, toUserId);
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                String.format("不允许跨公司委托：委托人在 %s 公司、受托人在 %s 公司。"
                        + "跨公司代办会让外公司的人出现在本公司单据的审批记录上；"
                        + "确需放行请由管理员在参数配置中设置 %s（形如 33>1，* 可通配公司号）",
                        fromCompany, toCompany, CROSS_COMPANY_WHITELIST_KEY));
    }

    /** 跨公司白名单匹配：逗号分隔的 {@code 委托方公司>受托方公司}，两侧支持 {@code *}；未配置＝全禁。 */
    private boolean crossCompanyAllowed(Long fromCompany, Long toCompany) {
        String cfg;
        try {
            cfg = configService.getValue(CROSS_COMPANY_WHITELIST_KEY);
        } catch (Exception e) {
            log.warn("读取跨公司委托白名单失败，按 fail-closed 拒绝: {}", e.getMessage());
            return false;
        }
        if (cfg == null || cfg.isBlank()) {
            return false;
        }
        String f = String.valueOf(fromCompany);
        String t = String.valueOf(toCompany);
        for (String seg : cfg.split(",")) {
            String s = seg.trim();
            if (s.isEmpty()) {
                continue;
            }
            int p = s.indexOf('>');
            if (p <= 0 || p == s.length() - 1) {
                continue;
            }
            String left = s.substring(0, p).trim();
            String right = s.substring(p + 1).trim();
            if (("*".equals(left) || left.equals(f)) && ("*".equals(right) || right.equals(t))) {
                return true;
            }
        }
        return false;
    }

    /** 用户所属公司（company_id）；用户不存在返回 null。 */
    private Long companyOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? null : u.getCompanyId();
    }

    /** 统计与 [begin,end] 重叠的生效委托条数；toUserId 为 null 时不限受托人。 */
    private long countActiveInRange(Long fromUserId, Long toUserId,
                                    LocalDateTime begin, LocalDateTime end) {
        LambdaQueryWrapper<WfDelegate> qw = new LambdaQueryWrapper<WfDelegate>()
                .eq(WfDelegate::getFromUserId, fromUserId)
                .eq(WfDelegate::getStatus, "NORMAL")
                .le(WfDelegate::getBeginTime, end)
                .ge(WfDelegate::getEndTime, begin);
        if (toUserId != null) {
            qw.eq(WfDelegate::getToUserId, toUserId);
        }
        Long c = delegateMapper.selectCount(qw);
        return c == null ? 0L : c;
    }

    /** 与 [begin,end] 重叠的生效委托的受托人（同委托人多条时取 id 最大，与 {@link #activeDelegateTo} 同一规则）；无则 null。 */
    private Long delegateTargetInRange(Long fromUserId, LocalDateTime begin,
                                       LocalDateTime end) {
        WfDelegate d = delegateMapper.selectOne(new LambdaQueryWrapper<WfDelegate>()
                .eq(WfDelegate::getFromUserId, fromUserId)
                .eq(WfDelegate::getStatus, "NORMAL")
                .le(WfDelegate::getBeginTime, end)
                .ge(WfDelegate::getEndTime, begin)
                .orderByDesc(WfDelegate::getId)
                .last("LIMIT 1"));
        return d == null ? null : d.getToUserId();
    }

    /**
     * 循环委托检查（A2 第②条硬约束，人工裁决 RA-F4a 前置）：沿委托链一跳跳走下去，
     * 只要能绕回委托人（A→B→C→A）就拒绝。
     *
     * <p>改造前只查<b>直接两跳</b>（A→B 且 B→A），三跳及以上的环放行；而受托人可持有委托人的
     * 办理资格这一事实会沿链叠出"谁都在等别人办"的死锁面。规则依据：〔E·BOSBCFG·8〕原文
     * 「<b>不允许循环委托！</b>」——外部资料未把该禁令限定在两跳。
     *
     * <p>深度上限 {@value #MAX_DELEGATE_CHAIN_DEPTH}：链上每一跳都要查一次库，且现网委托窗口互斥
     * 同一委托人同期只能委托一人 本就让链难以变长；超限只记日志放行，
     * 不让一条校验把建委托的耗时拖成不可控。
     */
    private void assertNoCircularChain(Long fromUserId, Long toUserId,
                                       LocalDateTime begin, LocalDateTime end) {
        StringBuilder chain = new StringBuilder(String.valueOf(fromUserId)).append(" → ")
                .append(toUserId);
        Long cursor = toUserId;
        for (int hop = 0; hop < MAX_DELEGATE_CHAIN_DEPTH; hop++) {
            Long next = delegateTargetInRange(cursor, begin, end);
            if (next == null) {
                return;
            }
            chain.append(" → ").append(next);
            if (Objects.equals(next, fromUserId)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "委托链 " + chain + " 构成回环，系统不支持循环委托（委托链上任一环都不允许绕回起点）");
            }
            cursor = next;
        }
        log.warn("委托链超过 {} 跳未收口（{}），按放行处理——循环判定只保证该深度内的环",
                MAX_DELEGATE_CHAIN_DEPTH, chain);
    }

    /**
     * 查询某用户当前生效的委托（处理待办转办时使用）。
     * 返回受托人 ID；无生效委托返回 null。
     */
    public Long activeDelegateTo(Long fromUserId, String bizType) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WfDelegate> qw = new LambdaQueryWrapper<WfDelegate>()
                .eq(WfDelegate::getFromUserId, fromUserId)
                .eq(WfDelegate::getStatus, "NORMAL")
                .le(WfDelegate::getBeginTime, now)
                .ge(WfDelegate::getEndTime, now)
                .orderByDesc(WfDelegate::getId)
                .last("LIMIT 1");
        WfDelegate d = delegateMapper.selectOne(qw);
        if (d == null) {
            return null;
        }
        if (d.getBizTypes() != null && !d.getBizTypes().isBlank()
                && (bizType == null || !("," + d.getBizTypes() + ",").contains("," + bizType + ","))) {
            return null;
        }
        return d.getToUserId();
    }
}
