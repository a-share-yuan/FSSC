package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfCc;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.mapper.WfCcMapper;
import com.fssc.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程传阅（G2）投递服务 —— {@code wf_cc} 的<b>唯一写入点</b>（P3，2026-09-23）。
 *
 * <p><b>为什么需要它</b>：复核发现 G2 是「三缺一之三」的彻底空壳 ——
 * 表 / 实体 / Mapper / 查询（{@code ccPage}）/ 已读标记 / 3 个 Controller 端点 /
 * 前端（{@code MyCc.vue}、工作台卡片、流程中心概览计数）全部齐备，
 * 而 {@code WfQueryService.recordCc()} <b>全仓零调用</b>、{@code wf_node_config} 无抄送列、
 * {@code wf_cc} 实测 <b>0 行</b>。即读端与前端 100% 就绪、生产端 0%，
 * 与 {@code MyDelegate.vue} 早已备好 {@code EXPIRED} 分支而后端从不产出同型。
 *
 * <p><b>抄送人来源（取并集后去重）</b>：
 * <ol>
 *   <li><b>节点级配置</b>：{@code wf_node_config.cc_users}（用户ID）∪ {@code cc_roles}
 *       （角色编码，运行期展开为在职成员）。按 {@code model_key + node_key} <b>精确匹配</b>
 *       —— 与 timeout_hours / up_level 同一条口径，<b>孤儿行配置一律不读</b>
 *       （当前配置表约 42.7% 的行 node_key 不在已部署 BPMN 的 userTask 集合内）。</li>
 *   <li><b>运行时入参</b>：审批人在审批弹窗临时指定的「抄送给」，不依赖任何配置，
 *       因而完全不受孤儿行问题影响。</li>
 * </ol>
 *
 * <p><b>投递时机</b>：仅在审批<b>通过并推进</b>后投递（驳回会终止实例，且已有独立终止通知链路；
 * 给已终止的实例挂抄送点击价值低）。若后续需要「驳回也抄送」，在此处放开即可。
 *
 * <p><b>通知出口（D-DEC-119/149/187，2026-10-01 收口）</b>：投递成功后逐条发 {@code sys_message}
 * 站内信（{@value #CC_MSG_TYPE}）。此前本服务只写 {@code wf_cc}，而 {@code wf_cc} 不是任何人会主动去翻的
 * 地方 —— 抄送在数据层成立、在运营层失效，被抄送人永远不知道有单可看。站内信与 {@code wf_cc} 行
 * <b>同一条据</b>：{@code (biz_type, biz_id)} 直接取 {@code wf_cc} 上的业务域与单据主键，
 * 使提醒本身可联查回被抄送的单据。
 *
 * <p><b>发送失败不阻断</b>：站内信在投递循环之外补发，单条异常只记 warn，既不影响 {@code wf_cc} 已成的事实，
 * 也不影响审批主链路。
 *
 * <p><b>默认零影响</b>：两路来源皆空时不写任何行，{@code complete()} 行为与改造前逐字一致。
 *
 * <p><b>幂等</b>：库层 {@code wf_cc.uk_proc_node_user (proc_inst_id, from_node, cc_user_id)}
 * 兜底，重复投递（如驳回后重走同一节点）静默跳过，绝不因重复键中断审批。
 *
 * <p><b>失败不阻断</b>：调用方（{@code WfEngineFacade.complete}）已整体 try/catch；
 * 本类内部亦逐条 catch —— 传阅是附加可观测性，不允许影响审批主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfCcService {

    /** 抄送站内信类型（{@code sys_message.msg_type}）；前端 {@code MSG_TYPE} 枚举同步登记 */
    static final String CC_MSG_TYPE = "CC_NOTICE";

    /**
     * 抄送维护通道权限码（D-DEC-187）：持有者可<b>代人</b>标记已读（流程管理员整理收件状态的通道）。
     * 普通收件人不再需要它——见 {@link #markRead(Long, com.fssc.framework.security.LoginUser)}。
     */
    static final String MAINTAIN_PERM = "wf:cc:update";

    private final WfCcMapper wfCcMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeConfigResolver nodeConfigResolver;
    private final SysUserMapper sysUserMapper;
    private final com.fssc.framework.notify.NotifyService notifyService;
    /**
     * 不变量 II（2026-10-15）：抄送角色展开复用<b>取人侧</b>的 scope 口径——
     * {@code COMPANY} 角色只投单据公司持有人、{@code GROUP} 角色投全局（本公司优先），
     * 与 {@code NodeOrgResolver} 的指派/候选人查询完全同源，杜绝「审批收敛、抄送撒全网」的口径分裂。
     */
    private final NodeOrgResolver nodeOrgResolver;

    /**
     * 按<b>取人侧同口径</b>展开 {@code cc_roles} 为收件人集合（不变量 II，2026-10-15）。
     *
     * <p><b>为什么不能按角色编码直接反查全网用户</b>：指派链路（{@code NodeOrgResolver}）按
     * {@code sys_role.org_scope} 收敛——{@code COMPANY} 角色只取<b>单据公司</b>持有人、
     * {@code GROUP} 角色才取全网；而 {@code SysUserMapper.selectUserIdsByRoleCodes} 没有任何
     * company/scope 条件。两套口径并存时，单公司单据会把抄送投给<b>所有公司</b>的同角色人员
     * （实测：company 1 的应收单抄出 20 人、横跨 9 家公司 ⇒ 过度知会/信息越界）。
     * 该缺陷在「角色 × 公司全覆盖」补位（278 账号）之后从偶发放大为必然，故必须与指派同源收敛。
     *
     * <p><b>口径</b>：逐角色走 {@link NodeOrgResolver#listRoleCandidatesInScope}——
     * COMPANY→本公司持有人（单据公司解析不到时取空，<b>不做全局兜底</b>：宁可少抄，
     * 也不在解析失败时把抄送撒满全租户）；GROUP→全局（本公司优先序）；附带排除停用与 admin。
     * 单角色展开失败只跳过该角色，不影响其余角色与主链路（fail-soft，与 {@link #deliver} 整体一致）。
     *
     * <p>顺带说明：{@code wf_node_config} 键为 {@code model_key + node_key}，<b>没有公司维度</b>，
     * 所以"各公司配各自抄送人"在配置层无法表达——收敛只能落在代码层，这也是本方法存在的理由。
     */
    private void expandRoleTargets(java.util.Set<Long> targets, WfNodeConfig cfg, WfInstance inst) {
        List<String> roles = parseCodes(cfg.getCcRoles());
        if (roles.isEmpty()) {
            return;
        }
        Long billCompany = null;
        try {
            billCompany = nodeOrgResolver.companyIdOfBill(inst.getBizType(), inst.getBizId());
        } catch (Exception ex) {
            log.warn("抄送：单据公司解析失败 bizType={} bizId={}（COMPANY 角色按取人侧同语义取空）",
                    inst.getBizType(), inst.getBizId(), ex);
        }
        for (String rc : roles) {
            try {
                // 发起人传 startUserId：DIRECT_SUPERVISOR 这类"人维度"角色在抄送语境下同样成立
                targets.addAll(nodeOrgResolver.listRoleCandidatesInScope(billCompany, inst.getStartUserId(), rc));
            } catch (Exception ex) {
                log.warn("抄送：角色成员展开失败 role={}（跳过该角色，不影响其余）", rc, ex);
            }
        }
    }

    /**
     * 按「节点配置 ∪ 运行时入参」投递抄送。
     *
     * @param procInstId   Flowable 流程实例 ID
     * @param nodeKey      刚办结的任务的 taskDefinitionKey（写入 {@code wf_cc.from_node}）
     * @param operator     本次操作人；抄送给操作人自己无意义，会被剔除
     * @param runtimeCcCsv 审批人临时指定的抄送人用户ID，逗号分隔，可空
     * @return 本次实际新增的抄送条数（已存在的重复项不计）
     */
    public int deliver(String procInstId, String nodeKey, Long operator, String runtimeCcCsv) {
        if (procInstId == null || procInstId.isBlank()) {
            return 0;
        }
        // 节点级配置需要 model_key；实例查不到（历史数据/未双写）时跳过配置源，
        // 运行时入参仍然可用 —— 不因实例缺失而整体失效。
        WfInstance inst = null;
        try {
            inst = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getProcInstId, procInstId).last("limit 1"));
        } catch (Exception ex) {
            log.warn("抄送：查流程实例失败 procInst={}", procInstId, ex);
        }

        Set<Long> targets = new LinkedHashSet<>();

        // ① 节点级配置（精确键匹配；孤儿行不读）
        if (inst != null && nodeKey != null) {
            try {
                WfNodeConfig cfg = nodeConfigResolver.resolve(inst.getModelKey(), nodeKey);
                if (cfg != null) {
                    targets.addAll(parseUserIds(cfg.getCcUsers()));
                    expandRoleTargets(targets, cfg, inst);
                }
            } catch (Exception ex) {
                log.warn("抄送：节点配置解析失败 procInst={} node={}", procInstId, nodeKey, ex);
            }
        }

        // ② 运行时入参
        targets.addAll(parseUserIds(runtimeCcCsv));

        // ③ 剔除操作人自己
        targets.removeIf(uid -> uid == null || uid.equals(operator));
        if (targets.isEmpty()) {
            return 0;
        }

        // ④ 剔除不存在/停用账号（抄送不该投给停用用户）
        Set<Long> valid = new LinkedHashSet<>();
        try {
            for (SysUser u : sysUserMapper.selectBatchIds(new ArrayList<>(targets))) {
                if (u.getStatus() != null && !"NORMAL".equals(u.getStatus())) {
                    log.debug("抄送跳过停用用户 uid={}", u.getId());
                    continue;
                }
                valid.add(u.getId());
            }
        } catch (Exception ex) {
            log.warn("抄送：用户校验失败，改用原始目标集 procInst={}", procInstId, ex);
            valid.addAll(targets);
        }
        if (valid.isEmpty()) {
            return 0;
        }

        // ⑤ 逐条投递（重复键静默跳过）
        int ok = 0;
        List<WfCc> delivered = new ArrayList<>();
        for (Long uid : valid) {
            WfCc cc = new WfCc();
            cc.setProcInstId(procInstId);
            cc.setFromNode(nodeKey);
            cc.setCcUserId(uid);
            cc.setIsRead(0);
            if (inst != null) {
                cc.setBizType(inst.getBizType());
                cc.setBizId(inst.getBizId());
                cc.setBizNo(inst.getBizNo());
                cc.setTitle(inst.getTitle());
            }
            try {
                wfCcMapper.insert(cc);
                ok++;
                delivered.add(cc);
            } catch (DuplicateKeyException ex) {
                // uk_proc_node_user 幂等命中：同实例同来源节点同人已投递过。
                // 不再补发提醒——提醒与抄送行同生同灭，重复投递正是"同一份抄送不要打扰两次"的信号。
                log.debug("抄送已存在，跳过 procInst={} node={} uid={}", procInstId, nodeKey, uid);
            } catch (Exception ex) {
                log.warn("抄送投递失败 procInst={} node={} uid={}", procInstId, nodeKey, uid, ex);
            }
        }
        // ⑥ 通知出口（D-DEC-119/149/187）：抄送行落库后补发站内信，被抄送人才可能知道有单可看。
        //    整段独立 try/catch——传阅提醒失败绝不回滚已成的抄送，也绝不阻断审批。
        for (WfCc cc : delivered) {
            notifyCc(cc);
        }
        if (ok > 0) {
            log.info("传阅投递成功 procInst={} node={} operator={} 新增={} 目标={}",
                    procInstId, nodeKey, operator, ok, valid);
        }
        return ok;
    }

    /** 抄送站内信：与 {@code wf_cc} 行同据（业务域 + 单据主键）提醒收件人去「我的抄送」查看。 */
    private void notifyCc(WfCc cc) {
        try {
            String billLabel = cc.getBizNo() != null && !cc.getBizNo().isBlank()
                    ? cc.getBizNo() : (cc.getTitle() == null ? cc.getProcInstId() : cc.getTitle());
            String content = "单据「" + (cc.getTitle() == null || cc.getTitle().isBlank()
                    ? billLabel : cc.getTitle()) + "」（单号 " + billLabel + "）已抄送给您"
                    + (cc.getFromNode() == null ? "" : "，来源节点：" + cc.getFromNode())
                    + "。请知悉，可在「流程中心 > 我的抄送」查看该单。";
            notifyService.notifyUser(cc.getCcUserId(), CC_MSG_TYPE, "流程抄送提醒", content,
                    cc.getBizType(), cc.getBizId(), "/flow-center/cc");
        } catch (Exception ex) {
            log.warn("抄送提醒发送失败 procInst={} uid={}: {}",
                    cc.getProcInstId(), cc.getCcUserId(), ex.getMessage());
        }
    }

    /**
     * 抄送已读回写（D-DEC-187，2026-10-01 收口）。
     *
     * <p><b>为什么改</b>：{@code wf_cc.is_read} 是<b>收件人的个人状态</b>，不是流程管理动作，
     * 而端点此前挂 {@code wf:cc:update}——该码在授权表里只授 CONFIG_ADMIN／FIN_MANAGER／IT_ADMIN 三岗
     * （sys_menu id=3350 → sys_role_menu 反查，FIND_IN_SET 复核），而实际收到抄送的岗位
     * （现网 wf_cc 收件人 3901／3915 = FSSC_SUPERVISOR＋COMMON_EMP）一个都不持 ⇒
     * 前端「标记已读」按钮对他们<b>不渲染</b>、接口对他们 403，{@code is_read} 结构性恒为 0
     * （实测 {@code wf_cc} 12 行全部 {@code is_read=0}、{@code read_time} 全 NULL，
     * 与 D-DEC-187 登记的「5 条未读」同因，只是行数随投递增加）。
     * 光补通知（D-DEC-149）不解决这一半：提醒发得出去，已读状态仍永远翻不过来。
     *
     * <p><b>口径</b>：与 {@code GET /cc/mine} 的「本人视角免权限码」一致——自己的抄送行不需要权限码；
     * 持 {@code wf:cc:update}（或通配 {@code *:*:*}）者仍可代人标记，原维护通道不缩。
     *
     * <p><b>归属校验必须留着</b>：去掉权限码不等于去掉。{@code wf_cc.id} 是自增主键、可枚举，
     * 无归属判据时任一登录用户可把全库抄送行刷成已读，收件人再也看不出哪条没看过。
     * 跨租户由 MyBatis-Plus 租户拦截器在 {@code selectById} 上天然收敛。
     *
     * @param operator 当前登录人（控制器传入，不接受裸 userId——需要它的 perms 判维护通道）
     * @return {@code true} 本次真的把该行从 unread 置为 read；{@code false} 该行本就已是已读（重复点击幂等）
     */
    public boolean markRead(Long id, com.fssc.framework.security.LoginUser operator) {
        if (id == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "抄送记录 ID 必填");
        }
        if (operator == null || operator.getUserId() == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.UNAUTHORIZED, "未登录，无法标记抄送已读");
        }
        WfCc cc = wfCcMapper.selectById(id);
        if (cc == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.NOT_FOUND, "抄送记录不存在: " + id);
        }
        java.util.Set<String> perms = operator.getPerms();
        boolean privileged = perms != null
                && (perms.contains("*:*:*") || perms.contains(MAINTAIN_PERM));
        if (!privileged && !operator.getUserId().equals(cc.getCcUserId())) {
            // 只回结果不回「谁的」：id 可枚举时，差异化的报错文本就是抄送名册的拖库接口。
            log.warn("抄送已读越权被拒 ccId={} 操作人={}", id, operator.getUserId());
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.FORBIDDEN, "只能将自己的抄送标记为已读");
        }
        if (Integer.valueOf(1).equals(cc.getIsRead())) {
            return false;
        }
        WfCc upd = new WfCc();
        upd.setId(id);
        upd.setIsRead(1);
        upd.setReadTime(java.time.LocalDateTime.now());
        wfCcMapper.updateById(upd);
        return true;
    }

    /**
     * 抄送候选人（只读）：供审批弹窗「抄送给」做多选/远程搜索。
     *
     * <p>范围＝本租户 {@code status='NORMAL'} 的用户，<b>排除管理员</b>（与「admin 禁入业务审批节点」
     * 同口径，抄送同样不往 admin 投）。租户条件由 MyBatis-Plus 拦截器注入。
     *
     * @param keyword 姓名或登录名模糊匹配，可空
     * @param limit   返回上限，缺省 200、硬顶 500（抄送是辅助动作，不允许拉全量表）
     */
    public java.util.List<java.util.Map<String, Object>> candidates(String keyword, Integer limit) {
        int cap = (limit == null || limit <= 0) ? 200 : Math.min(limit, 500);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getStatus, "NORMAL");
        // is_admin <> 1 对 NULL 不成立（SQL 三值逻辑），须显式并上 IS NULL
        qw.and(w -> w.ne(SysUser::getIsAdmin, 1).or().isNull(SysUser::getIsAdmin));
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(w -> w.like(SysUser::getNickName, k).or().like(SysUser::getUserName, k));
        }
        qw.orderByAsc(SysUser::getId);
        qw.last("limit " + cap);
        java.util.List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (SysUser u : sysUserMapper.selectList(qw)) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("name", u.getNickName() != null && !u.getNickName().isBlank()
                    ? u.getNickName() : u.getUserName());
            m.put("userName", u.getUserName());
            out.add(m);
        }
        return out;
    }

    /** 解析逗号分隔的用户ID串：忽略空白与非数字，保序去重 */
    private List<Long> parseUserIds(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                long v = Long.parseLong(t);
                if (!out.contains(v)) {
                    out.add(v);
                }
            } catch (NumberFormatException ex) {
                log.warn("抄送配置中的用户ID不是数字，已忽略: {}", t);
            }
        }
        return out;
    }

    /** 解析逗号分隔的角色编码串：去空白、去重、保序 */
    private List<String> parseCodes(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
