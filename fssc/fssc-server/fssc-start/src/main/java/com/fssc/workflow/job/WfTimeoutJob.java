package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.workflow.entity.WfFlowConfig;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfFlowConfigMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批超时治理（需求31 附录B + 改造方案 G3-1）。
 *
 * <p><b>G2 第 3 条后本类的定位：从「主机制」降为「兜底」</b>——超时的第一触发点是
 * {@code WfBpmnTemplateService} 为 {@code timeout_hours > 0} 的节点生成的 BPMN 边界定时器
 * （{@code WfTimeoutBoundaryDelegate}）。但边界定时器是<b>一次性</b>的：它只唤醒一次，
 * 之后"催办→阶梯升级"这类需要反复推进的档位、以及定时器触发时尚未到 {@code due_time}
 * （工作日历把到期日推到节后）的情形，都需要有人继续扫。本类保留 {@code @Scheduled} 60 秒扫描，
 * 承担这两件兜底事；两条路径共用同一个单任务处置方法 {@link #handleTimeoutTask}，语义绝不漂移。
 *
 * <p>每分钟扫描 {@code wf_task} 的 PENDING 待办，按节点配置 {@code wf_node_config.timeout_hours /
 * timeout_action} 执行三档语义：<b>AUTO_REMIND</b> 催办（站内信给真实责任方）、
 * <b>AUTO_PASS</b> 自动通过、<b>ESCALATE</b> 升级转办给上级。后两档一律走
 * {@link WfEngineFacade} 的机器路径收口（留痕 + 双账 + 下一节点取人），不在本类直连引擎。
 *
 * <p><b>为什么此前整套超时"名存实亡"（2026-09-22 只读勘查）</b>：
 * <ol>
 *  <li>扫描入口把 {@code wf_flow_config} 的 TIMEOUT 规则当作<b>前置条件</b>，该表 0 行时直接 return，
 *      连节点配置都不读——528 行配了 timeout_hours 的节点从未被扫过一次；</li>
 *  <li>{@code timeout_action} 全库 602 行只有一个值 NOTIFY，AUTO_PASS/AUTO_TRANSFER 零配置，
 *      且各处拼写不一（AUTO_REMIND/REMIND/URGE、ESCALATE/AUTO_ESCALATE/REDISTRIBUTE），
 *      不认识的拼写只落一条"未知的超时动作"warn；</li>
 *  <li>催办只在 {@code assignee_id != null} 时发出，而候选组/任务池（POOL）态的任务恰恰长期无人签收
 *      ——最需要催的形态一条都催不到；</li>
 *  <li>AUTO_PASS 直连 {@code taskService.complete()}，推进出的下一节点没人取人、也没有镜像行，
 *      单子"自动通过后失踪"，且不留审批历史。</li>
 * </ol>
 * 本轮四点全部收口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfTimeoutJob {

    private final TaskService taskService;
    private final WfTaskMapper wfTaskMapper;
    private final WfFlowConfigMapper wfFlowConfigMapper;
    private final WfNodeConfigMapper wfNodeConfigMapper;
    /** 升级留痕读取：判断本任务是否已被超时升级过（升级后不再二次超时） */
    private final com.fssc.workflow.mapper.WfTaskHistoryMapper wfTaskHistoryMapper;
    /** AUTO_PASS 白名单与金额阈值来源（sys_config） */
    private final com.fssc.system.service.ConfigService configService;
    /** 节点键解析（含 Task_N 兜底映射），与超时监控接口共用同一实现，避免两处规则漂移 */
    private final com.fssc.workflow.service.WfNodeConfigResolver wfNodeConfigResolver;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;
    /** 超时转办的升级对象解析：与 empty_strategy=ESCALATE 同一套"沿汇报线往上报、绝不落 admin"口径 */
    private final com.fssc.workflow.service.NodeOrgResolver nodeOrgResolver;
    /** 催办限频（跨进程一致状态）：同一任务 URGE_INTERVAL 内只催一次 */
    private final com.fssc.framework.spi.DistributedState distributedState;
    /** 超时推进/升级的执行通道：与人工审批同一套双账收口与留痕 */
    private final WfEngineFacade wfEngineFacade;
    /** 镜像表统一收口：催办次数不再由本类直写 wf_task（G2 第 3 条） */
    private final com.fssc.workflow.service.WfQueryService wfQueryService;
    /** 工作日历：due_time 缺失时按"工作小时"而非自然小时回算到期时间（G2 第 3 条） */
    private final com.fssc.workflow.service.WorkCalendarService workCalendarService;
    /** 卡单聚合告警正文取在办人姓名（F3-2）：只读，不参与任何处置判断 */
    private final com.fssc.system.mapper.SysUserMapper sysUserMapper;
    /** 卡单聚合告警的跨重启去重：读 {@code sys_message} 已有的 {@value #STUCK_ALERT_MSG_TYPE} 行（D-DEC-118） */
    private final com.fssc.system.mapper.SysMessageMapper sysMessageMapper;
    private final com.fssc.workflow.service.BillTerminalChecker billTerminalChecker;
    /** R5-D-068 违约留痕：审批超时违约落 {@code op_sla_breach} 一行（复用 compliance 现有写入点与列口径） */
    private final com.fssc.compliance.mapper.OpSlaBreachMapper slaBreachMapper;

    /** 同一任务的催办最小间隔 */
    private static final java.time.Duration URGE_INTERVAL = java.time.Duration.ofHours(24);

    /**
     * 本轮兜底扫描的「同一收件人 → 待发超时提醒」缓冲（D-DEC-162 风暴收口）。
     *
     * <p>只在 {@link #processTimeout()} 的整轮范围内建立与清空：边界定时器路径（
     * {@link #handleTimeoutTask} 由 {@code WfTimeoutBoundaryDelegate} 单任务调入）与发起人人工催办
     * 取到的是 {@code null}，一律<b>即时单发</b>——那两条路本来就一次只涉及一个环节，无风暴可言。
     */
    private final ThreadLocal<Map<String, List<UrgeLine>>> urgeDigest = new ThreadLocal<>();

    /** 一条待投递的催办提醒（含租户与业务域，投递时才落站内信） */
    private static final class UrgeLine {
        final Long tenantId;
        final Long userId;
        final String msgType;
        final String title;
        final String content;
        final String bizType;
        final Long bizId;
        final String linkUrl;

        UrgeLine(Long tenantId, Long userId, String msgType, String title, String content,
                 String bizType, Long bizId, String linkUrl) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.msgType = msgType;
            this.title = title;
            this.content = content;
            this.bizType = bizType;
            this.bizId = bizId;
            this.linkUrl = linkUrl;
        }
    }

    /**
     * 超时升级留痕的文案前缀（与 {@code empty_strategy} 兜底升级区分开，见 {@link #alreadyEscalated}）：
     * 既是写入侧的唯一出处，也是读取侧的判据，两处不得各写一遍字面量。
     */
    static final String ESCALATE_COMMENT_PREFIX = "超时升级";

    /** 卡单聚合告警收件角色（{@code sys_role.role_code}＝流程管理员） */
    static final String STUCK_ALERT_ROLE = "WF_ADMIN";
    /** 卡单聚合告警的站内信类型（{@code sys_message.msg_type}），同时是跨重启去重的判据 */
    static final String STUCK_ALERT_MSG_TYPE = "WF_STUCK_ALERT";
    /** 催办留痕的动作码（{@code wf_task_history.action}），同时是跨重启限频的判据 */
    private static final String HISTORY_URGE = "URGE";
    /** 催办/超时提醒的跳转页：与待办视图同页，杜绝 {@code link_url} 为 NULL 的"点不动的催办"（D-DEC-162） */
    private static final String URGE_LINK = "/approval";
    /** 聚合告警最小间隔：同一租户一天一条，避免每分钟把管理员刷一遍 */
    private static final java.time.Duration STUCK_ALERT_INTERVAL = java.time.Duration.ofHours(24);
    /** 告警正文最多列几条，其余只报总数 */
    private static final int STUCK_ALERT_LINES = 10;
    /** 单人多条超时提醒合并后正文最多列几条（D-DEC-162 风暴收口，与卡单告警同一折叠口径） */
    private static final int URGE_DIGEST_LINES = 10;
    /** {@code sys_message.content} 列宽 varchar(1000)，留余量后的截断阈值 */
    private static final int CONTENT_MAX_LEN = 950;

    /** 未认领任务的催办对象上限：大角色（COMMON_EMP 151 人）全发＝通知风暴 */
    private static final int NOTIFY_CAP = 20;

    /** 超时动作三档（归一后的规范值） */
    static final String ACT_REMIND = "AUTO_REMIND";
    static final String ACT_AUTO_PASS = "AUTO_PASS";
    static final String ACT_ESCALATE = "ESCALATE";

    /**
     * AUTO_PASS 白名单（批次 1 ②）：{@code sys_config.config_key}，值为逗号分隔的
     * {@code modelKey:nodeKey}。<b>未配置＝全库一档自动通过都不放行</b>（缺省语义＝NOTIFY），
     * 节点配置里写 AUTO_PASS 也不再"配了就敢自动过"。
     */
    static final String AUTOPASS_NODES_KEY = "wf.timeout.autopass.nodes";
    /** AUTO_PASS 金额敞口上限（sys_config）：单据金额超过即不放行 */
    static final String AUTOPASS_MAX_AMOUNT_KEY = "wf.timeout.autopass.max_amount";
    static final java.math.BigDecimal AUTOPASS_MAX_AMOUNT_DEFAULT = new java.math.BigDecimal("5000");
    /** 作业节点（POOL）自动回收的催办次数门槛：第 N 次催办仍无人办 → 退回任务池 */
    static final int POOL_RELEASE_AFTER_URGES = 2;
    /**
     * 「催办 → 升级」阶梯档（{@code timeout_action} 的一个取值，2026-09-23 新增）。
     *
     * <p><b>为什么必须补这一档</b>（现网取证）：全库 613 个配了 SLA 的节点里，{@code timeout_action}
     * 只有 {@code NOTIFY}(357) 与 {@code AUTO_REMIND}(256) 两种值——两个拼写归一后是<b>同一档</b>，
     * 即处置档（AUTO_PASS / ESCALATE）<b>零配置</b>，于是"超时"在现网只等于"一直催"。
     * 实测 LOAN 单「部门经理审核【部门经理】」催办 <b>10 次</b>仍 PENDING、候选 {@code ,3875,3887,}
     * 无人认领 ⇒ 任务永久滞留；而 {@code wf_task_history} 里 32 条 ESCALATE 全部来自
     * {@code empty_strategy} 空策略兜底（comment="空策略兜底…"），<b>无一条来自超时升级</b>。
     *
     * <p>本档语义＝<b>先催办，累计满阈值仍无人办理再升级</b>（参考行业做法"超时处理"的逐级上收）。
     * 与 {@code ESCALATE} 档的区别：ESCALATE 是 SLA 一到立即升级（无缓冲），本档给出缓冲期。
     *
     * <p> <b>改档必须逐节点进行</b>（业务决策）：本档只是新增一个可选取值，
     * <b>不改任何存量节点的行为</b>——配了 NOTIFY 的 357 个节点仍只催办。
     * 治理清点与逐节点建议见 {@code scripts/_audit_timeout_action.py}。
     */
    static final String ACT_ESCALATE_LADDER = "ESCALATE_AFTER_URGES";
    /**
     * 阶梯档的催办次数阈值（{@code sys_config.config_key}）：同一任务累计催办满 N 次仍无人办理，
     * 升级给沿汇报线第 {@code wf_node_config.up_level} 级真实负责人（绝不落 admin）。
     * 设 0 / 负数＝该档不升级（退化为纯催办）。
     */
    static final String ESCALATE_AFTER_URGES_KEY = "wf.timeout.escalate.after_urges";
    static final int ESCALATE_AFTER_URGES_DEFAULT = 3;
    /** 升级留痕的动作名（{@code wf_task_history.action}，与 WfEngineFacade.escalateByTimeout 同源） */
    private static final String HISTORY_ESCALATE = "ESCALATE";

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processTimeout() {
        jobHeartbeatService.run("WfTimeoutJob", () -> {
        try {
            List<WfTask> pending = ignoreTenant(() -> wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getStatus, "PENDING")));
            if (pending == null || pending.isEmpty()) {
                return;
            }
            // 旧 wf_flow_config TIMEOUT 规则只是可选叠加层（现网 0 行）：节点配置才是主口径，
            // 规则表空不空都不能跳过整轮扫描。
            List<WfFlowConfig> rules = ignoreTenant(() -> wfFlowConfigMapper.selectList(
                    new LambdaQueryWrapper<WfFlowConfig>()
                            .eq(WfFlowConfig::getCfgType, "TIMEOUT")
                            .eq(WfFlowConfig::getStatus, "NORMAL")));
            // D-DEC-162：本轮的催办提醒先进缓冲、轮末合并，缓冲的生命周期严格等于本轮
            Map<String, List<UrgeLine>> digest = new java.util.LinkedHashMap<>();
            urgeDigest.set(digest);
            try {
                for (WfTask t : pending) {
                    try {
                        // 扫描跨租户，处置必须回到该待办自己的租户：节点配置读取、催办落库、引擎推进
                        // 全是租户表，缺上下文会被 fail-closed 拦下（2026-09-22 现网：每轮整批抛错，三档动作零触发）
                        Long tenantId = t.getTenantId();
                        List<WfFlowConfig> taskRules = rules == null ? List.of() : rules;
                        TenantContext.callAs(tenantId, () -> handleTimeoutTask(t, taskRules));
                    } catch (Exception ex) {
                        log.error("超时任务处理失败 taskId={}", t.getTaskId(), ex);
                    }
                }
            } finally {
                urgeDigest.remove();
            }
            // 本轮催办按收件人合并投递（D-DEC-162）：扫描逐任务发信，一个人同时压着 4 条超时单
            // 就会在一轮里收到 4 条同义站内信——实测 10:41:02~10:41:06 四秒 18 条。合并成一轮一人一条。
            flushUrgeDigest(digest);
            // 卡单聚合上报（F3-2 / 体检 E-03）：处置档只覆盖配了阶梯档的 13 个节点，
            // 其余 342 个催办档节点催满也不出声——本段补的就是"出声"这一环。
            alertStuckTasks(pending);
        } catch (Exception ex) {
            log.error("审批超时扫描异常", ex);
        }
        });
    }

    /**
     * 卡单聚合上报（F3-2 / 体检 E-03「只催不升」）。
     *
     * <p><b>补的是"出声"，不是"处置"</b>：现网 355 个有效节点里 342 个的 {@code timeout_action} 是催办档
     * （AUTO_REMIND 250 + NOTIFY 92，归一后同义），处置档只有 13 个。于是催办满 N 次的单子只会一直催同一批
     * 办理人，没有任何人知道它卡住了——实测最长催办 37 次。本方法把"催满阈值仍 PENDING"的待办按租户聚合成
     * <b>一条</b>站内信发给流程管理员（{@value #STUCK_ALERT_ROLE}），一天最多一条。
     *
     * <p><b>明确不做</b>：不改派、不自动通过、不动 {@code assignee}（是否升级属业务决策，走阶梯档
     * {@code ESCALATE_AFTER_URGES} 逐节点改档，见方案 §4.2 与 RA-F3b）。看板的红牌标记同为派生态，不落库。
     *
     * <p>收件人为空时只记 warn 不静默丢弃——"告警发不出去"本身就是要暴露的配置缺陷（E-15 单点岗同族）。
     *
     * <p>阈值（sys_config）、收件人（sys_role/sys_user）、站内信落库（sys_message）<b>全是租户表</b>，
     * 而本轮扫描是跨租户跑的，所以整段必须回到该租户的上下文里执行；放在外面会被租户拦截器 fail-closed
     * 拦下（实测抛「租户上下文缺失」，阈值永远读不到、只能按默认值跑，配了也不生效＝假配置）。
     */
    private void alertStuckTasks(List<WfTask> pending) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        Map<Long, List<WfTask>> byTenant = new java.util.LinkedHashMap<>();
        for (WfTask t : pending) {
            if (t.getTenantId() != null) {
                byTenant.computeIfAbsent(t.getTenantId(), k -> new ArrayList<>()).add(t);
            }
        }
        for (Map.Entry<Long, List<WfTask>> e : byTenant.entrySet()) {
            Long tenantId = e.getKey();
            List<WfTask> tasks = e.getValue();
            try {
                TenantContext.callAs(tenantId, () -> alertStuckForTenant(tenantId, tasks));
            } catch (Exception ex) {
                log.warn("卡单聚合告警失败 租户={}: {}", tenantId, ex.getMessage());
            }
        }
    }

    /** 单租户内的卡单聚合：读阈值 → 挑卡单 → 去重 → 发一条站内信（必须在 {@link TenantContext#callAs} 里调用） */
    private void alertStuckForTenant(Long tenantId, List<WfTask> tasks) {
        int threshold = wfQueryService.stuckAfterUrges();
        if (threshold <= 0) {
            return;
        }
        List<WfTask> stuck = new ArrayList<>();
        for (WfTask t : tasks) {
            // R5-D-068：卡单告警同样要过双条件闸——对已作废/已终结单据"出声上报"与继续催办是同一种打扰
            if (wfQueryService.isStuck(t.getUrgedCount()) && urgeEligible(t)) {
                stuck.add(t);
            }
        }
        if (stuck.isEmpty()) {
            return;
        }
        String dedupKey = "wf:timeout:stuck:alert:" + tenantId;
        // D-DEC-118：与催办限频同理——内存键重启即清零，实测 07:58 一轮 18 条、08:05:56 重启后又一轮
        // 同样 18 条／同 11 人。去重真值改读已发出的告警本身（站内信落库即凭据），跨重启、跨实例一致。
        if (distributedState.marked(dedupKey) || stuckAlertSentRecently()) {
            return;
        }
        List<Long> receivers = nodeOrgResolver.userIdsGlobalWithRole(STUCK_ALERT_ROLE);
        String content = stuckContent(stuck, threshold);
        if (receivers.isEmpty()) {
            log.warn("卡单聚合告警无接收人（租户 {} 的角色 {} 零授权），仅记日志：卡单 {} 条（阈值 {} 次催办）。{}",
                    tenantId, STUCK_ALERT_ROLE, stuck.size(), threshold, content);
        } else {
            for (Long uid : receivers) {
                notifyService.notifyUser(uid, STUCK_ALERT_MSG_TYPE,
                        "流程卡单上报：" + stuck.size() + " 条待办催满 " + threshold + " 次仍未办理",
                        content, null, null);
            }
            log.info("卡单聚合告警已发: 租户={} 条数={} 收件人={}", tenantId, stuck.size(), receivers);
        }
        distributedState.mark(dedupKey, STUCK_ALERT_INTERVAL);
    }

    /**
     * 本租户 24 小时内是否已发过卡单聚合告警（D-DEC-118 的跨重启去重）。
     * 读失败按"未发过"放行，与催办限频同一 fail-open 取向——少发一条告警比漏发整档出声更可接受。
     * 必须在租户上下文内调用：{@code sys_message} 是租户表，租户条件由拦截器注入。
     */
    private boolean stuckAlertSentRecently() {
        try {
            Long n = sysMessageMapper.selectCount(
                    new LambdaQueryWrapper<com.fssc.system.entity.SysMessage>()
                            .eq(com.fssc.system.entity.SysMessage::getMsgType, STUCK_ALERT_MSG_TYPE)
                            .ge(com.fssc.system.entity.SysMessage::getCreateTime,
                                    LocalDateTime.now().minus(STUCK_ALERT_INTERVAL)));
            return n != null && n > 0;
        } catch (Exception ex) {
            log.warn("卡单告警去重读取失败，按未发过处理: {}", ex.getMessage());
            return false;
        }
    }

    /** 聚合正文：最多 {@value #STUCK_ALERT_LINES} 行明细 + 总数，超出部分折叠，避免站内信被撑爆。 */
    private String stuckContent(List<WfTask> stuck, int threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下待办已催办满 ").append(threshold).append(" 次仍未办理（卡单）。")
                .append("本上报只出声、不处置：不会自动改派，也不会自动通过，请流程管理员用「改派」或调整节点配置处理。\n");
        int shown = Math.min(stuck.size(), STUCK_ALERT_LINES);
        for (int i = 0; i < shown; i++) {
            WfTask t = stuck.get(i);
            sb.append("· ").append(t.getBizNo() == null ? (t.getTitle() == null ? t.getTaskId() : t.getTitle()) : t.getBizNo())
                    .append("｜节点：").append(t.getNodeName() == null ? t.getNodeKey() : t.getNodeName())
                    .append("｜在办人：").append(assigneeLabel(t))
                    .append("｜已催：").append(t.getUrgedCount()).append(" 次\n");
        }
        if (stuck.size() > shown) {
            sb.append("…另有 ").append(stuck.size() - shown).append(" 条，见「流程监控 > 超时未办榜」红牌项。");
        }
        return sb.toString();
    }

    /** 在办人姓名（查不到退回 id；未指派则报"候选组待领取"） */
    private String assigneeLabel(WfTask t) {
        if (t.getAssigneeId() == null) {
            return "候选组待领取";
        }
        try {
            com.fssc.system.entity.SysUser u = sysUserMapper.selectById(t.getAssigneeId());
            return u == null ? String.valueOf(t.getAssigneeId())
                    : (u.getNickName() == null || u.getNickName().isBlank()
                        ? u.getUserName() : u.getNickName() + "(" + u.getUserName() + ")");
        } catch (Exception ex) {
            return String.valueOf(t.getAssigneeId());
        }
    }

    /**
     * 单个任务的超时处置（<b>可复用入口</b>）：边界定时器 Delegate 与 60 秒兜底扫描共用本方法，
     * 保证两条触发路径的三档语义、白名单门槛、留痕口径完全一致（抽方法而非复制一份逻辑）。
     * <p>调用前提：当前线程已带该任务所属租户上下文（节点配置/催办落库/引擎推进都是租户表）。
     *
     * @param rules 旧 {@code wf_flow_config} TIMEOUT 规则（可选叠加层）；定时器路径传空表即可
     */
    public void handleTimeoutTask(WfTask t, List<WfFlowConfig> rules) {
        if (t == null || t.getCreateTime() == null) {
            return;
        }
        // R5-D-068 双条件闸：催办扫描只处置「实例 RUNNING 且单据非终态」的在办任务。
        // 作废单（VOID）此前流程未终止、镜像行滞留 PENDING，每分钟被本扫描空转催办（实测 po_order 14/15）。
        // 闸在处置入口而非仅扫描循环：边界定时器路径同样受约束，两条触发路径口径一致。
        if (!urgeEligible(t)) {
            return;
        }
        if (handleByLegacyRule(t, rules == null ? List.of() : rules)) {
            return;
        }
        handleByNodeConfig(t);
    }

    /**
     * R5-D-068 催办/超时处置双条件闸（(1) AND (2) 全真才放行）：
     * <ol>
     *  <li><b>实例 RUNNING</b>：按 {@code proc_inst_id} 读 wf_instance，终态（CANCELLED/APPROVED/REJECTED）
     *      或查无实例（幽灵待办）一律跳过——对已死实例催办只会打扰人。</li>
     *  <li><b>单据非终态</b>：{@link #billTerminal} 现按采购订单面收口（VOID/CLOSED/ARCHIVED）。</li>
     * </ol>
     * 读取取向：实例腿 fail-closed（状态不可判定宁可跳过本轮，宁可不催不漏判）；单据腿 fail-open
     * （读失败不阻断催办，与限频设施"宁可多催不漏催"的历史取向一致）。跳过只记 debug：
     * 终态单会在一分钟内被反复扫到，info 会刷屏。
     */
    private boolean urgeEligible(WfTask t) {
        if (t == null) {
            return false;
        }
        String procInstId = t.getProcInstId();
        if (procInstId == null || procInstId.isBlank()) {
            return false;
        }
        try {
            com.fssc.workflow.entity.WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst == null || com.fssc.workflow.service.WfQueryService.isTerminalInstanceStatus(inst.getStatus())) {
                log.debug("催办闸跳过（实例非 RUNNING）: procInstId={}, instStatus={}, taskId={}",
                        procInstId, inst == null ? "缺失" : inst.getStatus(), t.getTaskId());
                return false;
            }
        } catch (Exception e) {
            log.warn("催办闸实例状态读取失败，按跳过处理 taskId={}: {}", t.getTaskId(), e.getMessage());
            return false;
        }
        if (billTerminal(t)) {
            log.debug("催办闸跳过（单据已终态）: bizType={}, bizId={}, taskId={}",
                    t.getBizType(), t.getBizId(), t.getTaskId());
            return false;
        }
        return true;
    }

    /**
     * 业务单据是否已收尾（终态）。判定真值表统一收口在 {@link com.fssc.workflow.service.BillTerminalChecker}
     * 其余 bizType 返回 false＝维持改造前行为（放行）。读取失败按非终态放行（不阻断催办）。
     */
    private boolean billTerminal(WfTask t) {
        return billTerminalChecker.isBillTerminal(t.getBizType(), t.getBizId());
    }

    /**
     * 到期时刻真值：<b>优先 {@code wf_task.due_time}</b>（G3 已把该列做成真值），
     * 取不到才用 {@code create_time + timeout_hours} 回算，且回算走工作日历（G2 第 3 条）。
     * 两处口径必须一致，否则"同一张单在定时器与兜底作业里到期时间不同"。
     *
     * <p>回算时按单据所属<b>公司</b>取日历（参考行业做法"按组织取日历"）；{@link #orgOf} 解析不到组织时
     * 返回 {@code null}，日历服务会回落租户口径，与改造前逐字一致。
     */
    private LocalDateTime dueOf(WfTask t, long timeoutHours) {
        if (t.getDueTime() != null) {
            return t.getDueTime();
        }
        try {
            return workCalendarService.plusWorkingHours(t.getCreateTime(), timeoutHours, orgOf(t));
        } catch (Exception ex) {
            log.warn("工作日历回算到期失败，退化为自然小时 taskId={}: {}", t.getTaskId(), ex.getMessage());
            return t.getCreateTime().plusHours(timeoutHours);
        }
    }

    /**
     * 单据所属组织（公司）：{@code bizType/bizId} → 单据表 company_id（取不到再按建单人兜底）。
     * 解析不到一律返回 {@code null}，由日历服务回落租户口径（零影响）。
     */
    private Long orgOf(WfTask t) {
        if (t == null) {
            return null;
        }
        try {
            return nodeOrgResolver.companyIdOfBill(t.getBizType(), t.getBizId());
        } catch (Exception ex) {
            log.warn("超时回算按组织取日历失败，回落租户口径 taskId={}: {}", t.getTaskId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 兼容旧数据：{@code wf_flow_config} 的 TIMEOUT 规则按节点名模糊匹配（如"直接主管审批超时"
     * 匹配节点名含"直接主管"）。命中即由本方法处置，不再叠加节点配置。
     *
     * <p>规则里的 action 与节点配置是两套拼写、语义重叠（NOTIFY / AUTO_ESCALATE / REDISTRIBUTE），
     * 现统一归一到三档口径执行。原 AUTO_ESCALATE 只是"给候选组加一个角色"——加完没人认领还是没人办，
     * 现按 ESCALATE 真语义落到具体人头上；解析不到真人时不放开候选组，只催办。
     */
    private boolean handleByLegacyRule(WfTask t, List<WfFlowConfig> rules) {
        String nodeName = t.getNodeName();
        if (rules.isEmpty() || nodeName == null) {
            return false;
        }
        for (WfFlowConfig r : rules) {
            String cfgName = r.getCfgName();
            if (cfgName == null) {
                continue;
            }
            if (!nodeName.contains(cfgName.replace("超时", "").replace("审批", ""))) {
                continue;
            }
            String json = r.getCfgJson();
            if (json == null || !json.contains("timeoutHours")) {
                continue;
            }
            long hours = extractHours(json);
            if (hours <= 0) {
                continue;
            }
            // G2：到期真值优先取 wf_task.due_time（含工作日历口径），取不到才按自然/工作小时回算
            LocalDateTime due = dueOf(t, hours);
            if (LocalDateTime.now().isBefore(due)) {
                continue;
            }
            String action = normalizeAction(extractString(json, "action"));
            Task ftask = taskService.createTaskQuery().taskId(t.getTaskId()).singleResult();
            if (ftask == null) {
                // 引擎侧已办结，镜像行是幽灵 PENDING：交给双账补齐脚本，超时动作不空转
                return true;
            }
            String reason = "节点 SLA " + hours + " 小时，已超时 " + overdueHours(t) + " 小时";
            log.info("审批任务超时处理（旧规则）: taskId={}, node={}, action={}, targetRole={}",
                    t.getTaskId(), nodeName, action, extractString(json, "targetRole"));
            // 旧规则表无 up_level 字段（升级层级是 wf_node_config 的列），按 1 级处理
            apply(action, t, ftask, reason, extractString(json, "targetRole"), null);
            return true;
        }
        return false;
    }

    /**
     * 按 {@code wf_node_config} 的 timeoutHours/timeoutAction 处理超时任务。
     */
    private void handleByNodeConfig(WfTask t) {
        if (t.getModelKey() == null || t.getNodeKey() == null) {
            return;
        }
        // 节点键解析（wf_task.node_key 为 Flowable Task_N，须经 Task_N→业务节点兜底映射才能命中配置）
        WfNodeConfig nodeConfig = wfNodeConfigResolver.resolve(t.getModelKey(), t.getNodeKey());
        if (nodeConfig == null) {
            return;
        }
        Integer timeoutHours = nodeConfig.getTimeoutHours();
        String timeoutAction = nodeConfig.getTimeoutAction();
        if (timeoutHours == null || timeoutHours <= 0 || timeoutAction == null || timeoutAction.isBlank()) {
            return;
        }
        // G2：到期真值优先取 wf_task.due_time（含工作日历口径），取不到才按工作小时回算
        LocalDateTime due = dueOf(t, timeoutHours);
        if (LocalDateTime.now().isBefore(due)) {
            return;
        }
        Task ftask = taskService.createTaskQuery().taskId(t.getTaskId()).singleResult();
        if (ftask == null) {
            return;
        }
        String action = normalizeAction(timeoutAction);
        if (action == null) {
            // 不认识的拼写按最轻副作用处理（催办），而不是静默丢弃整档配置
            log.warn("未识别的超时动作: taskId={}, action={}，按催办处理", t.getTaskId(), timeoutAction);
            action = ACT_REMIND;
        }
        String reason = "节点 SLA " + timeoutHours + " 小时，已超时 " + overdueHours(t) + " 小时";
        // P2：升级层级随节点配置下传（up_level 此前是死列——配了 2/3 级也一律只升 1 级）
        apply(action, t, ftask, reason, null, nodeConfig.getUpLevel());
    }

    /** 按归一后的三档语义分派执行。 */
    private void apply(String action, WfTask t, Task ftask, String reason, String legacyTargetRole,
                       Integer upLevel) {
        switch (action) {
            case ACT_AUTO_PASS -> {
                // 批次 1 ②（参考行业做法"启用超时处理…会签节点不支持超时自动处理"）：
                // AUTO_PASS 改白名单制，默认一档都不放行＝退回催办；节点配置里写 AUTO_PASS 也不再"配了就敢自动过"。
                String deny = autoPassDenial(t, ftask);
                if (deny != null) {
                    // D-DEC-274：不放行时原先直接落回 handleNotify＝无限催办、无终局（实测 AR_REPORT 三个
                    // 并行节点各催 50 次、超期 46 小时仍 PENDING）。行业方案超时策略两档都有终局——要么自动执行、
                    // 要么"超时后自动将任务转发给指定处理人"。⇒ 改走既有阶梯档：阈值内仍只催办（行为不变），
                    // 满 wf.timeout.escalate.after_urges 次后沿汇报线升级给真实负责人（绝不落 admin）。
                    log.info("超时自动通过未放行（{}），转阶梯档处理: taskId={}, node={}",
                            deny, t.getTaskId(), t.getNodeName());
                    handleEscalateLadder(t, ftask,
                            reason + "（未满足自动通过条件：" + deny + "）", upLevel);
                    return;
                }
                wfEngineFacade.autoPassByTimeout(t.getTaskId(), t.getAssigneeId(), "超时自动通过：" + reason);
                log.info("审批任务超时自动通过: taskId={}, node={}, {}", t.getTaskId(), t.getNodeName(), reason);
            }
            case ACT_ESCALATE -> handleEscalate(t, ftask, reason, legacyTargetRole, upLevel);
            // 阶梯档：先催办，累计满阈值仍无人办理再升级（缓冲期由 sys_config 给）
            case ACT_ESCALATE_LADDER -> handleEscalateLadder(t, ftask, reason, upLevel);
            default -> handleNotify(t, ftask, reason);
        }
    }

    /**
     * AUTO_PASS 放行判定（批次 1 ②）。返回 {@code null} 表示允许自动通过，否则返回拒因（写进催办文案与日志）。
     *
     * <p>四道门槛，全部来自 2026-09-22 裁决与厂商取证（方案 §8.2 ②）：
     * <ol>
     *  <li><b>白名单</b>：{@code sys_config.wf.timeout.autopass.nodes}（逗号分隔 {@code modelKey:nodeKey}）
     *      未配置/不含本节点 → 不放行。缺省空＝AUTO_PASS 全库不生效，等价于默认 NOTIFY；</li>
     *  <li><b>无金额敞口</b>：单据金额超 {@code wf.timeout.autopass.max_amount}（默认 5000）→ 不放行；</li>
     *  <li><b>节点未绑定单据操作</b>：任务池/共享池作业节点（付款、记账、归档等落地动作）→ 不放行，
     *      同类系统同样只对纯审批节点做自动执行；</li>
     *  <li><b>非会签/并联会审</b>：Flowable 多实例节点（有 {@code nrOfInstances} 变量）→ 不放行。</li>
     * </ol>
     * 另加本系统铁律：机器路径留痕的办理人不得为 admin（零 admin）。
     */
    private String autoPassDenial(WfTask t, Task ftask) {
        String key = t.getModelKey() + ":" + t.getNodeKey();
        List<String> whitelist = configList(AUTOPASS_NODES_KEY);
        if (whitelist.isEmpty()) {
            return "白名单 " + AUTOPASS_NODES_KEY + " 未配置";
        }
        if (!whitelist.contains(key)) {
            return "节点 " + key + " 不在白名单";
        }
        java.math.BigDecimal amount = t.getAmount();
        if (amount != null && amount.compareTo(autPassMaxAmount()) > 0) {
            return "单据金额 " + amount + " 超自动通过上限";
        }
        if (isWorkNode(t)) {
            return "共享作业节点（任务池/共享池）绑定单据操作";
        }
        if (isMultiInstance(ftask)) {
            return "会签/并联会审（多实例）节点";
        }
        if (t.getAssigneeId() != null && nodeOrgResolver.isAdministrator(t.getAssigneeId())) {
            return "办理人为 admin 账号（零 admin 铁律）";
        }
        return null;
    }

    /** 节点是否作业档：{@code handling_mode=POOL} 或 {@code assign_type=SHARE_POOL}（付款/记账/归档类落地动作） */
    private boolean isWorkNode(WfTask t) {
        if (t.getModelKey() == null || t.getNodeKey() == null) {
            return false;
        }
        WfNodeConfig cfg = wfNodeConfigResolver.resolve(t.getModelKey(), t.getNodeKey());
        if (cfg == null) {
            return false;
        }
        String mode = cfg.getHandlingMode() == null ? "" : cfg.getHandlingMode().trim();
        String assign = cfg.getAssignType() == null ? "" : cfg.getAssignType().trim();
        return "POOL".equalsIgnoreCase(mode) || "SHARE_POOL".equalsIgnoreCase(assign);
    }

    /**
     * 是否 Flowable 多实例节点（真会签/并联会审）。引擎在多实例执行上放
     * {@code nrOfInstances / nrOfCompletedInstances} 系统变量，据此判定；
     * 判定失败按"非多实例"处理（白名单本身已是主闸，不因变量读取失败而放开不该放的节点）。
     */
    private boolean isMultiInstance(Task ftask) {
        try {
            return taskService.getVariables(ftask.getId()).containsKey("nrOfInstances");
        } catch (Exception e) {
            log.warn("会签（多实例）判定异常，按非会签处理 taskId={}: {}", ftask.getId(), e.getMessage());
            return false;
        }
    }

    private java.math.BigDecimal autPassMaxAmount() {        String raw = configValue(AUTOPASS_MAX_AMOUNT_KEY);
        if (raw != null && !raw.isBlank()) {
            try {
                java.math.BigDecimal v = new java.math.BigDecimal(raw.trim());
                if (v.signum() >= 0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                log.warn("自动通过金额上限配置 {}={} 非法，回退默认 {}",
                        AUTOPASS_MAX_AMOUNT_KEY, raw, AUTOPASS_MAX_AMOUNT_DEFAULT);
            }
        }
        return AUTOPASS_MAX_AMOUNT_DEFAULT;
    }

    /** sys_config 读单值（读取失败按未配置处理，绝不因配置服务异常放大自动通过的口子） */
    private String configValue(String key) {        try {
            return configService.getValue(key);
        } catch (Exception e) {
            log.warn("读取超时矩阵配置 {} 失败（按未配置处理）: {}", key, e.getMessage());
            return null;
        }
    }

    private List<String> configList(String key) {
        String csv = configValue(key);
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split("[,，;；\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /**
     * 阶梯档的催办阈值（{@code sys_config.wf.timeout.escalate.after_urges}）。
     * 未配置取默认 {@value #ESCALATE_AFTER_URGES_DEFAULT}；非法值同样回退默认；
     * 配 0 / 负数＝该档不升级（<=0 由调用方判为不启用）。
     */
    private int escalateAfterUrges() {
        String raw = configValue(ESCALATE_AFTER_URGES_KEY);
        if (raw == null || raw.isBlank()) {
            return ESCALATE_AFTER_URGES_DEFAULT;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("催办升级阈值配置 {}={} 非法，回退默认 {}",
                    ESCALATE_AFTER_URGES_KEY, raw, ESCALATE_AFTER_URGES_DEFAULT);
            return ESCALATE_AFTER_URGES_DEFAULT;
        }
    }

    /**
     * 超时动作别名归一（G3-1）。库内存量只写过 {@code NOTIFY}，但配置页/文档/旧规则里出现过
     * AUTO_REMIND、REMIND、URGE、TRANSFER、ESCALATE、AUTO_ESCALATE、REDISTRIBUTE 等拼法；
     * 不归一则这些配置整档失效。归一后只有三档，且 {@code NOTIFY} 继续可用（602 行存量语义不变）。
     *
     * <p>新增阶梯档别名（2026-09-23）：{@code ESCALATE_AFTER_URGES} /
     * {@code URGE_THEN_ESCALATE} / {@code REMIND_THEN_ESCALATE} 归一为
     * {@link #ACT_ESCALATE_LADDER}。
     *
     * @return 规范值，无法识别返回 null（由调用方兜底为催办）
     */
    static String normalizeAction(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "NOTIFY":
            case "REMIND":
            case "URGE":
            case "AUTO_REMIND":
                return ACT_REMIND;
            case "AUTO_PASS":
            case "AUTOPASS":
                return ACT_AUTO_PASS;
            case "ESCALATE_AFTER_URGES":
            case "URGE_THEN_ESCALATE":
            case "REMIND_THEN_ESCALATE":
                return ACT_ESCALATE_LADDER;
            case "AUTO_TRANSFER":
            case "TRANSFER":
            case "ESCALATE":
            case "AUTO_ESCALATE":
            case "REDISTRIBUTE":
                return ACT_ESCALATE;
            default:
                return null;
        }
    }

    /**
     * AUTO_REMIND 催办：站内信给真实责任方，并累加催办次数。
     *
     * <p>限频：扫描每分钟一次，每次都发＝积压场景的通知风暴。同一任务 24 小时内只催一次，
     * 标记走 {@link DistributedState}（fail-open：标记服务不可用时放行，宁可多催不漏催）。
     *
     * @return 本轮之后该任务的累计催办次数（被限频跳过时返回当前值），供阶梯档判断是否该升级
     */
    private int handleNotify(WfTask t, Task ftask, String reason) {
        String content = "您有审批任务「" + (t.getTitle() != null ? t.getTitle() : "") + "」（节点："
                + t.getNodeName() + "）已超时，" + reason + "，请尽快处理。";
        int before = t.getUrgedCount() == null ? 0 : t.getUrgedCount();
        int urged = sendUrge(t, ftask, "TIMEOUT_REMINDER", "审批超时催办通知", content, "超时催办", reason, null);
        if (urged > before) {
            // R5-D-068 违约留痕：催办真正发出（未被 24h 限频拦下）＝本任务 SLA 违约成立，
            // 复用 compliance 现有写入点落 op_sla_breach 一行；限频即天然去重，同一窗口不重复落行。
            recordSlaBreach(t, reason);
        }
        // 批次 1 ①：作业节点（POOL）有人领取却办不动 → 催办满 {@value #POOL_RELEASE_AFTER_URGES} 次自动回收，
        // 退回任务池让同池他人可领；单个人失手不再把整池卡死（共享作业有同样的超时回收档）。
        // 只挂在超时路径上：发起人手动催办（urgeByInitiator）不得顺带把在办任务退回池里。
        if (urged >= POOL_RELEASE_AFTER_URGES && t.getAssigneeId() != null && isWorkNode(t)) {
            Long owner = t.getAssigneeId();
            try {
                TenantContext.callAs(t.getTenantId(),
                        () -> wfEngineFacade.releaseByTimeout(t.getTaskId(), owner, "催办 " + urged + " 次未办理，自动退回任务池"));
                log.info("作业任务超时自动回收: taskId={}, node={}, 原领取人={} -> 任务池",
                        t.getTaskId(), t.getNodeName(), owner);
            } catch (Exception e) {
                log.warn("作业任务超时回收失败 taskId={}: {}", t.getTaskId(), e.getMessage());
            }
        }
        return urged;
    }

    /**
     * R5-D-068 违约落行：审批任务超时（SLA 违约）时向 {@code op_sla_breach} 写一条记录，
     * 复用 compliance 既有的写入点与列口径（{@code SlaMonitorService} 同表写入：task_id 挂任务主键、
     * bill_id/bill_type 挂业务单据、breach_type=BREACH、breach_time=检测时刻）。
     *
     * <p>去重：只在 {@link #handleNotify} 的催办真正发出时写入，天然继承 {@code URGE_INTERVAL}（24h）
     * 限频——同一任务同一窗口只落一行，绝不让每分钟扫描把违约表刷爆。
     * <b>租户显式落值</b>：与 SlaMonitorService 同因（定时/引擎线程依赖 ThreadLocal 会漏填 tenant_id，
     * 该列 NOT NULL）。
     */
    private void recordSlaBreach(WfTask t, String reason) {
        try {
            com.fssc.compliance.entity.OpSlaBreach breach = new com.fssc.compliance.entity.OpSlaBreach();
            breach.setTenantId(t.getTenantId());
            breach.setTaskId(t.getId());
            breach.setBillId(t.getBizId());
            breach.setBillType(t.getBizType());
            breach.setBreachType("BREACH");
            breach.setBreachTime(LocalDateTime.now());
            breach.setAssignedReviewerId(t.getAssigneeId());
            String remark = "审批任务超时违约：节点「" + (t.getNodeName() == null ? t.getNodeKey() : t.getNodeName())
                    + "」，" + reason;
            breach.setRemark(remark.length() > 500 ? remark.substring(0, 500) : remark);
            slaBreachMapper.insert(breach);
            log.info("SLA 违约落行: taskId={}, billType={}, billId={}", t.getTaskId(), t.getBizType(), t.getBizId());
        } catch (Exception e) {
            // 违约留痕是附加可观测性：写失败只 warn，绝不阻断催办主链路（与站内信容错同口径）
            log.warn("SLA 违约落行失败（不阻断催办）taskId={}: {}", t.getTaskId(), e.getMessage());
        }
    }

    /**
     * 催办的唯一发信通道：限频判定 → 站内信 → 计次 → 时间线留痕。
     * 超时作业与发起人催办（E-10）共用，两处口径不会漂移；而 POOL 回收这类"顺带改任务归属"的
     * 副作用留在超时调用方，人工催办不触发。
     *
     * <p><b>限频是两段式的（D-DEC-118 收口）</b>：内存标记只是同一进程内的快路径，
     * <b>真值是 {@code wf_task_history} 上这条任务最近一次 {@code URGE} 留痕的时刻</b>——
     * 留痕与发信本就是同一动作的两面（下面第 4 步写的就是它），因此限频状态天然跨重启、跨实例，
     * 不需要新表。改造前只有内存那一段：08:01:35 催办成功置 24h 键，08:05:05 重启清零，
     * 08:07:22 同一节点再催一次，6 分 47 秒打扰两遍。
     *
     * @param historyLabel 写进 {@code wf_task_history} 的前缀（"超时催办" / "发起人催办"）
     * @param reason       催办缘由，随次数一起落进留痕
     * @param operUserId   催办发起人；超时作业传 null（无人身后可归因），人工催办必须带，否则留痕答不出"谁催的"
     * @return 该任务的累计催办次数（命中 24 小时限频时返回当前值，不重复发信）
     */
    private int sendUrge(WfTask t, Task ftask, String msgType, String title, String content,
                         String historyLabel, String reason, Long operUserId) {
        String urgeKey = "wf:timeout:urge:" + t.getTaskId();
        boolean alreadyUrged;
        try {
            alreadyUrged = distributedState.marked(urgeKey) || urgedWithinInterval(t);
        } catch (Exception e) {
            alreadyUrged = false; // 限频设施异常不阻断催办
        }
        if (alreadyUrged) {
            // 把跨重启查出来的"仍在窗口内"补回内存，后续各轮走快路径、不再逐分钟查库
            try {
                distributedState.mark(urgeKey, URGE_INTERVAL);
            } catch (Exception e) {
                log.warn("催办限频标记失败 taskId={}: {}", t.getTaskId(), e.getMessage());
            }
            return t.getUrgedCount() == null ? 0 : t.getUrgedCount();
        }
        List<Long> targets = notifyTargets(t, ftask);
        Map<String, List<UrgeLine>> digest = urgeDigest.get();
        for (Long uid : targets) {
            UrgeLine line = new UrgeLine(t.getTenantId(), uid, msgType, title, content,
                    t.getBizType(), t.getBizId(), URGE_LINK);
            if (digest == null) {
                sendUrgeNotice(line);
            } else {
                digest.computeIfAbsent(line.tenantId + ":" + uid, k -> new ArrayList<>()).add(line);
            }
        }
        if (targets.isEmpty()) {
            log.warn("审批任务催办但无可催对象: taskId={}, node={}, 候选组与镜像候选人皆空",
                    t.getTaskId(), t.getNodeName());
        }
        try {
            distributedState.mark(urgeKey, URGE_INTERVAL);
        } catch (Exception e) {
            log.warn("催办限频标记失败 taskId={}: {}", t.getTaskId(), e.getMessage());
        }
        int urged = t.getUrgedCount() != null ? t.getUrgedCount() + 1 : 1;
        // G2 收口：催办次数改走 WfQueryService（原 wfTaskMapper.updateById 绕过了镜像表的统一写入口，
        // 与"镜像表写路径只有一个收口点"的约束冲突）。
        wfQueryService.bumpUrgedCount(t.getTaskId(), urged);
        log.info("{}: taskId={}, node={}, 对象数={}, urgedCount={}",
                historyLabel, t.getTaskId(), t.getNodeName(), targets.size(), urged);
        // ROOT-3：催办动作补进时间线（作业线程无登录人，显式带租户上下文才写得进留痕）
        // 这条 URGE 留痕同时是 {@link #urgedWithinInterval} 的限频真值，两者不得拆成两处口径。
        TenantContext.callAs(t.getTenantId(), () -> wfEngineFacade.recordJobAction(t.getProcInstId(),
                t.getTaskId(), t.getNodeKey(), t.getNodeName(), HISTORY_URGE, operUserId, t.getAssigneeId(),
                historyLabel + "（第 " + urged + " 次）：" + reason));
        return urged;
    }

    /**
     * 本任务在 {@link #URGE_INTERVAL} 内是否已被催办过 —— 读 {@code wf_task_history} 的 {@code URGE} 留痕。
     *
     * <p>留痕按 {@code (tenant_id, proc_inst_id)} 走 {@code idx_tenant_inst}，再按 {@code task_id + oper_time}
     * 收窄（{@code oper_time} 是分区列，带上它即只扫近期分区）。
     *
     * <p>读取失败一律返回 {@code false}（放行）：与改造前"限频设施不可用则宁可多催"的 fail-open 取向逐字一致，
     * 绝不因一次查询异常把催办／升级阶梯整档关掉。
     */
    private boolean urgedWithinInterval(WfTask t) {
        if (t == null || t.getTaskId() == null) {
            return false;
        }
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minus(URGE_INTERVAL);
        Long[] holder = new Long[1];
        Runnable query = () -> holder[0] = wfTaskHistoryMapper.selectCount(
                new LambdaQueryWrapper<com.fssc.workflow.entity.WfTaskHistory>()
                        .eq(com.fssc.workflow.entity.WfTaskHistory::getTaskId, t.getTaskId())
                        .eq(com.fssc.workflow.entity.WfTaskHistory::getAction, HISTORY_URGE)
                        .ge(com.fssc.workflow.entity.WfTaskHistory::getOperTime, since)
                        .ge(t.getProcInstId() != null,
                                com.fssc.workflow.entity.WfTaskHistory::getProcInstId, t.getProcInstId()));
        try {
            if (t.getTenantId() == null) {
                query.run();
            } else {
                TenantContext.callAs(t.getTenantId(), query);
            }
        } catch (Exception e) {
            log.warn("催办限频留痕读取失败，按未催办处理 taskId={}: {}", t.getTaskId(), e.getMessage());
            return false;
        }
        return holder[0] != null && holder[0] > 0;
    }

    /** 投递一条催办提醒（站内信 + 按配置的多渠道）；单条失败只记日志，绝不中断本轮其余处置。 */
    private void sendUrgeNotice(UrgeLine line) {
        Runnable send = () -> notifyService.notifyUser(line.userId, line.msgType, line.title, line.content,
                line.bizType, line.bizId, line.linkUrl);
        try {
            if (line.tenantId == null) {
                send.run();
            } else {
                // 站内信是租户表：合并投递发生在各任务的租户上下文之外，必须逐条回到收件人所属租户
                TenantContext.callAs(line.tenantId, send);
            }
        } catch (Exception e) {
            log.warn("催办通知失败 user={}: {}", line.userId, e.getMessage());
        }
    }

    /**
     * 轮末合并投递（D-DEC-162）：同一收件人在本轮收到的多条超时提醒合成<b>一条</b>站内信。
     *
     * <p>只合并不丢弃：正文逐条列出各环节，超过 {@value #URGE_DIGEST_LINES} 条折叠为总数；
     * 联查键 {@code (biz_type, biz_id)} 取第一条（合并信本就指向"去看你的待办"这一页）。
     * 单条时原样投递，文案与改造前逐字一致。
     */
    private void flushUrgeDigest(Map<String, List<UrgeLine>> digest) {
        if (digest == null || digest.isEmpty()) {
            return;
        }
        for (List<UrgeLine> lines : digest.values()) {
            UrgeLine first = lines.get(0);
            if (lines.size() == 1) {
                sendUrgeNotice(first);
                continue;
            }
            StringBuilder mergedContent = new StringBuilder();
            int shown = Math.min(lines.size(), URGE_DIGEST_LINES);
            for (int i = 0; i < shown; i++) {
                mergedContent.append(i + 1).append("）").append(lines.get(i).content).append('\n');
            }
            if (lines.size() > shown) {
                mergedContent.append("…另有 ").append(lines.size() - shown).append(" 条超时待办，见「我的待办」。");
            }
            // sys_message.content 列宽 varchar(1000)：超长先按条数折叠再截断，绝不让发信因数据截断而失败
            String body = mergedContent.length() > CONTENT_MAX_LEN
                    ? mergedContent.substring(0, CONTENT_MAX_LEN) + "…" : mergedContent.toString();
            UrgeLine merged = new UrgeLine(first.tenantId, first.userId, first.msgType,
                    first.title + "（共 " + lines.size() + " 条）", body,
                    first.bizType, first.bizId, first.linkUrl);
            sendUrgeNotice(merged);
        }
    }

    /**
     * 发起人催办（体检 E-10 / 方案 F1-5）。
     *
     * <p><b>补的是入口，不是机制</b>：催办通道一直存在，但只有超时作业会按它。提单人这边没有任何按钮，
     * 只能干等——单据在谁手里、卡在哪一节，发起人本是最关心的人，却是唯一不能出声的人。
     *
     * <p>限频与超时催办共用同一把键（{@code wf:timeout:urge:<taskId>}，24 小时）：代价是发起人这一催
     * 可能占掉本轮作业催办的额度，即同一环节一天内只打扰一次，比"人工催 + 作业催"各发一遍更克制。
     *
     * <p>命中限频抛业务异常而不是静默返回 0——界面必须让用户知道"这一催没发出去"。
     *
     * @param initiatorId   发起人用户 id，落进 URGE 留痕的 oper_user_id
     * @param initiatorName 发起人姓名，进通知文案与 URGE 留痕
     * @return 本次实际催办的任务数（并行/会签多任务时 &gt; 1）
     */
    private int urgeByInitiator(String procInstId, Long initiatorId, String initiatorName) {
        List<Task> open = taskService.createTaskQuery().processInstanceId(procInstId).active().list();
        if (open.isEmpty()) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "该单据当前没有在办环节，无需催办");
        }
        int urged = 0;
        for (Task ftask : open) {
            WfTask t = wfTaskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getTaskId, ftask.getId()).last("limit 1"));
            if (t == null) {
                log.warn("催办缺镜像行，跳过: procInstId={}, taskId={}", procInstId, ftask.getId());
                continue;
            }
            String content = "您有审批任务「" + (t.getTitle() != null ? t.getTitle() : "") + "」（节点："
                    + t.getNodeName() + "）由发起人「" + initiatorName + "」催办，请尽快处理。";
            int before = t.getUrgedCount() == null ? 0 : t.getUrgedCount();
            if (sendUrge(t, ftask, "APPROVAL_URGE", "审批催办通知", content, "发起人催办", initiatorName,
                    initiatorId) > before) {
                urged++;
            }
        }
        if (urged == 0) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "24 小时内已催办过，同一环节每天只催一次；此前的提醒已发给办理人");
        }
        return urged;
    }

    /**
     * 发起人催办的鉴权入口（控制器只递 procInstId + 当前登录人，判定口径集中在本类）。
     *
     * <p>鉴权看 {@code wf_instance.start_user_id}：催办的语义是"我问一句我这张单怎么样了"，
     * 别人问就没有依据——非发起人调用一律 403，不做静默通过。
     */
    public int urgeAsInitiator(String procInstId, Long userId, String userLabel) {
        com.fssc.workflow.entity.WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
        if (inst == null) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.NOT_FOUND,
                    "流程实例不存在: " + procInstId);
        }
        if (!java.util.Objects.equals(inst.getStartUserId(), userId)) {
            throw new com.fssc.common.exception.BizException(com.fssc.common.core.ErrorCode.FORBIDDEN,
                    "只有发起人本人可以催办本单（该单由用户 " + inst.getStartUserId() + " 提交）");
        }
        return urgeByInitiator(procInstId, userId, userLabel == null || userLabel.isBlank()
                ? String.valueOf(userId) : userLabel);
    }

    /**
     * 本任务是否已被<b>超时</b>升级过（读升级留痕，批次 1 ②"升级后不再二次超时"）。
     * 读取失败按"未升级过"处理：宁可再升一次级，也不要把超时治理整体关掉。
     *
     * <p>D-B4UI-03（本次收口）：判据此前只有 {@code action = ESCALATE}，而 {@code empty_strategy}
     * 取人兜底也借用同一个动作码写留痕（现网 54 行 ESCALATE 里 53 行是"空策略兜底"），于是任务一落地
     * 就被判成"已升级过"，阶梯升级与 AUTO_PASS 退路档被永久短路。{@code action} 分不开这两个机制，
     * 只有文案前缀分得开 ⇒ 收窄为"超时升级"留痕。
     */
    private boolean alreadyEscalated(String taskId) {
        try {
            Long n = wfTaskHistoryMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            com.fssc.workflow.entity.WfTaskHistory>()
                            .eq(com.fssc.workflow.entity.WfTaskHistory::getTaskId, taskId)
                            .eq(com.fssc.workflow.entity.WfTaskHistory::getAction, HISTORY_ESCALATE)
                            .likeRight(com.fssc.workflow.entity.WfTaskHistory::getComment, ESCALATE_COMMENT_PREFIX));
            return n != null && n > 0;
        } catch (Exception e) {
            log.warn("升级留痕读取失败 taskId={}: {}", taskId, e.getMessage());
            return false;
        }
    }

    /**
     * 催办对象：已认领→办理人本人；未认领（候选组 / POOL 任务池）→ 镜像行的候选人名单，
     * 镜像缺列时退回按 Flowable 候选组解析在职真人。
     *
     * <p>原实现只在 {@code assignee_id != null} 时发通知，于是最需要催的"无人认领"形态一条都催不到。
     */
    private List<Long> notifyTargets(WfTask t, Task ftask) {
        List<Long> out = new ArrayList<>();
        if (t.getAssigneeId() != null) {
            out.add(t.getAssigneeId());
            return out;
        }
        // 与待办视图同一份候选人解析口径（,1,2,3, 包裹、非数字段跳过）
        out.addAll(com.fssc.workflow.service.WfQueryService.parseCandidateIds(t.getCandidateIds()));
        if (out.isEmpty()) {
            for (Long id : nodeOrgResolver.listOperableUserIds(ftask)) {
                if (!out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out.size() > NOTIFY_CAP ? out.subList(0, NOTIFY_CAP) : out;
    }

    /**
     * ESCALATE 升级：转办给真实上级（部门汇报线 → 公司组织负责人），解析不到真人时退化为催办。
     *
     * <p>旧实现是 {@code addCandidateGroup(task, "admin")}：既违背"任何流程节点都不允许 admin 参与"的铁律，
     * 且 {@code sys_role} 里没有裸 {@code ADMIN} 这个 role_code，按 {@code canOperate} 的
     * "候选组 ∩ 用户角色"口径没人命中＝任务悬空。现与 {@code empty_strategy=ESCALATE} 共用
     * {@link com.fssc.workflow.service.NodeOrgResolver#escalateTarget(String)} 一套升级口径。
     */
    private void handleEscalate(WfTask t, Task ftask, String reason, String legacyTargetRole, Integer upLevel) {
        // 批次 1 ②：升级后不再二次超时（行业方案"指定处理人"档明确"收到任务后没有处理时长的限制"）。
        // 判据用升级留痕本身（wf_task_history 的 ESCALATE 行），不加列、不改表；升级后仍会催办，
        // 但不再自动通过、也不再反复升级换人——否则一张单会在汇报线上一路踢上去。
        if (alreadyEscalated(t.getTaskId())) {
            log.info("审批任务已升级过，本轮不再二次升级，仅催办: taskId={}, node={}", t.getTaskId(), t.getNodeName());
            handleNotify(t, ftask, reason + "（已升级转办，限时处理由接手人负责）");
            return;
        }
        Long current = resolveCurrentAssignee(t, ftask);
        // 定时任务线程没有登录态：按该待办所属租户建立上下文，否则租户表的读取会被 fail-closed 拦下
        List<Long> resolved = new ArrayList<>(1);
        TenantContext.callAs(t.getTenantId(),
                () -> resolved.add(roleHolderOf(t, legacyTargetRole, upLevel)));
        Long target = resolved.isEmpty() ? null : resolved.get(0);
        if (target == null || target.equals(current)) {
            log.warn("审批任务超时升级无可用对象: taskId={}, node={}, upLevel={}, 当前办理人={}, 升级对象={}, 退化为催办",
                    t.getTaskId(), t.getNodeName(), upLevel, current, target);
            handleNotify(t, ftask, reason);
            return;
        }
        Long escalate = target;
        TenantContext.callAs(t.getTenantId(),
                () -> wfEngineFacade.escalateByTimeout(t.getTaskId(), escalate, ESCALATE_COMMENT_PREFIX + "：" + reason));
        log.info("审批任务超时升级转办: taskId={}, node={}, upLevel={}, {} -> {}",
                t.getTaskId(), t.getNodeName(), upLevel, current, target);
    }

    /**
     * 「催办 → 升级」阶梯档（{@code timeout_action = ESCALATE_AFTER_URGES}，2026-09-23 新增）。
     *
     * <p>先按催办档发通知并累加 {@code urged_count}；累计满
     * {@code sys_config.wf.timeout.escalate.after_urges}（默认 {@value #ESCALATE_AFTER_URGES_DEFAULT}）
     * 仍无人办理时，升级给沿汇报线第 {@code up_level} 级真实负责人（绝不落 admin，
     * 复用 {@link #handleEscalate} 的同一套口径与"升级后不再二次升级"约束）。
     *
     * <p>与 {@code ESCALATE} 档的区别：ESCALATE 是 SLA 一到立即升级（无缓冲），本档给出缓冲期。
     * 作业节点（POOL/共享池）不在本档升级：它们走 {@link #POOL_RELEASE_AFTER_URGES} 的自动回收档，
     * 回任务池由同池他人领取，比升级给上级更符合共享作业的运作方式。
     */
    private void handleEscalateLadder(WfTask t, Task ftask, String reason, Integer upLevel) {
        int threshold = escalateAfterUrges();
        int urged = handleNotify(t, ftask,
                reason + "（阶梯档：满 " + threshold + " 次催办仍无人办理将自动升级）");
        if (threshold <= 0 || urged < threshold || isWorkNode(t) || alreadyEscalated(t.getTaskId())) {
            return;
        }
        log.info("催办阶梯触发升级: taskId={}, node={}, 已催办 {} 次（阈值 {}）, upLevel={}",
                t.getTaskId(), t.getNodeName(), urged, threshold, upLevel);
        handleEscalate(t, ftask, reason + "（已催办 " + urged + " 次仍无人办理，按阶梯档升级）", null, upLevel);
    }

    /**
     * 升级对象解析：旧规则的 {@code targetRole} 优先（配置里点名了岗位/角色，取该角色首个真人），
     * 否则沿汇报线按 {@code up_level} 上收。
     *
     * <p>注意点名角色分支<b>不受 up_level 影响</b>：配置者既已明确指名角色，就不该再被层级规则改写；
     * up_level 只在"沿汇报线自动找上级"这条兜底路径上生效。
     */
    private Long roleHolderOf(WfTask t, String legacyTargetRole, Integer upLevel) {
        if (legacyTargetRole != null && !legacyTargetRole.isBlank()) {
            List<Long> holders = nodeOrgResolver.listRoleCandidates(t.getProcInstId(), legacyTargetRole.trim());
            if (!holders.isEmpty()) {
                return holders.get(0);
            }
        }
        return nodeOrgResolver.escalateTarget(t.getProcInstId(), upLevel);
    }

    /** 当前办理人：优先取 Flowable 运行态，退回待办视图列。 */
    private Long resolveCurrentAssignee(WfTask t, Task ftask) {
        String a = ftask.getAssignee();
        if (a != null && !a.isBlank()) {
            try {
                return Long.valueOf(a.trim());
            } catch (NumberFormatException ignored) {
                return t.getAssigneeId();
            }
        }
        return t.getAssigneeId();
    }

    /** 已超时小时数（整除，用于提示文案与统计口径一致）。 */
    private long overdueHours(WfTask t) {
        return Duration.between(t.getCreateTime(), LocalDateTime.now()).toHours();
    }

    private long extractHours(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(json).get("timeoutHours");
            return n == null ? 0 : n.asLong();
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractString(String json, String key) {
        try {
            com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(json).get(key);
            return n == null ? "" : n.asText();
        } catch (Exception e) {
            return "";
        }
    }

    /** 跨租户读取：{@code runIgnoreTenant} 只收 Runnable，用单元素数组把结果带出来。 */
    private <T> T ignoreTenant(java.util.function.Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
