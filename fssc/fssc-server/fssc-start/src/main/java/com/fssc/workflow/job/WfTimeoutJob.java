// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.OpSlaBreach;
import com.fssc.compliance.mapper.OpSlaBreachMapper;
import com.fssc.framework.spi.DistributedState;
import com.fssc.system.entity.SysMessage;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysMessageMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.workflow.entity.WfFlowConfig;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfFlowConfigMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.service.BillTerminalChecker;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.workflow.service.WfNodeConfigResolver;
import com.fssc.workflow.service.WfQueryService;
import com.fssc.workflow.service.WorkCalendarService;
import com.fssc.workflow.service.EngineTenantScope;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批超时治理。
 *
 * <p>本类的定位是「兜底」，不是超时的主机制——超时的第一触发点是
 * {@code WfBpmnTemplateService} 为 {@code timeout_hours > 0} 的节点生成的 BPMN 边界定时器
 * （{@code WfTimeoutBoundaryDelegate}）。但边界定时器是<b>一次性</b>的：它只唤醒一次，
 * 之后"催办→阶梯升级"这类需要反复推进的档位，以及触发时尚未到 {@code due_time}
 * （工作日历把到期日推到节后）的情形，都需要有人继续扫。本类保留 {@code @Scheduled} 60 秒扫描，
 * 承担这两件兜底事；两条路径共用同一个单任务处置方法 {@link #handleTimeoutTask}，规则只写一遍。
 *
 * <p>每分钟扫描 {@code wf_task} 的 PENDING 待办，按节点配置 {@code wf_node_config.timeout_hours /
 * timeout_action} 执行三档语义：<b>AUTO_REMIND</b> 催办（站内信给真实责任方）、
 * <b>AUTO_PASS</b> 自动通过、<b>ESCALATE</b> 升级转办给上级。后两档一律走
 * {@link WfEngineFacade} 的机器路径办结（记录 + 双账 + 下一节点取人），不在本类直连引擎。
 *
 * <p><b>四种会让整套超时形同虚设的写法</b>（本类的结构就是为绕开它们而定的）：
 * <ol>
 *  <li>把 {@code wf_flow_config} 的 TIMEOUT 规则当作扫描<b>前置条件</b>：该表 0 行时直接 return，
 *      连节点配置都不读，配了 timeout_hours 的节点将一次也扫不到；</li>
 *  <li>{@code timeout_action} 拼写不统一（AUTO_REMIND/REMIND/URGE、ESCALATE/AUTO_ESCALATE/REDISTRIBUTE），
 *      不认识的值只落一条"未知的超时动作"warn，等于静默失效；</li>
 *  <li>催办只在 {@code assignee_id != null} 时发出，而候选组/任务池（POOL）态的任务恰恰长期无人签收
 *      ——最需要催的形态一条都催不到；</li>
 *  <li>AUTO_PASS 直连 {@code taskService.complete()}：推进出的下一节点没人取人、也没有镜像行，
 *      单子"自动通过后失踪"，且不留审批历史。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfTimeoutJob {

    private final TaskService taskService;
    private final WfTaskMapper wfTaskMapper;
    private final WfFlowConfigMapper wfFlowConfigMapper;
    private final WfNodeConfigMapper wfNodeConfigMapper;
    /** 升级记录读取：判断本任务是否已被超时升级过（升级后不再二次超时） */
    private final WfTaskHistoryMapper wfTaskHistoryMapper;
    /** AUTO_PASS 白名单与金额阈值来源（sys_config） */
    private final ConfigService configService;
    /** 节点键解析（含 Task_N 兜底映射），与超时监控接口共用同一实现，避免两处规则漂移 */
    private final WfNodeConfigResolver wfNodeConfigResolver;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;
    /** 超时转办的升级对象解析：与 empty_strategy=ESCALATE 同一套"沿汇报线往上报、绝不落 admin"规则 */
    private final NodeOrgResolver nodeOrgResolver;
    /** 催办限频（跨进程一致状态）：同一任务 URGE_INTERVAL 内只催一次 */
    private final DistributedState distributedState;
    /** 超时推进/升级的执行通道：与人工审批同一套双账办结与记录 */
    private final WfEngineFacade wfEngineFacade;
    /** 镜像表统一办结：催办次数不在这里直写 wf_task */
    private final WfQueryService wfQueryService;
    /** 工作日历：due_time 缺失时按"工作小时"而非自然小时回算到期时间 */
    private final WorkCalendarService workCalendarService;
    /** 卡单聚合告警正文取在办人姓名：只读，不参与任何处置判断 */
    private final SysUserMapper sysUserMapper;
    /** 卡单聚合告警的跨重启去重：读 {@code sys_message} 已有的 {@value #STUCK_ALERT_MSG_TYPE} 行 */
    private final SysMessageMapper sysMessageMapper;
    /** 催办校验·单据环节：单据最终状态判定（作废/关闭/归档）——单已收尾就不再催（与待办查询共用同一规则） */
    private final BillTerminalChecker billTerminalChecker;
    /** 违约记录：审批超时违约落 {@code op_sla_breach} 一行（复用 compliance 现有写入位置与列规则） */
    private final OpSlaBreachMapper slaBreachMapper;

    /** 同一任务的催办最小间隔 */
    private static final Duration URGE_INTERVAL = Duration.ofHours(24);

    /**
     * 整轮扫描内的「同一收件人 → 待发超时提醒」缓冲（防同一人一轮收多条同义提醒）。
     *
     * <p>只在 {@link #processTimeout()} 的整轮范围内建立与清空：边界定时器路径（
     * {@link #handleTimeoutTask} 由 {@code WfTimeoutBoundaryDelegate} 单任务调入）与发起人人工催办
     * 取到的是 {@code null}，一律<b>即时单发</b>——那两条路本来就一次只涉及一个环节，无风暴可言。
     */
    private final ThreadLocal<Map<String, List<UrgeLine>>> urgeDigest = new ThreadLocal<>();

    /**
     * 整轮扫描内的引擎侧在办任务 id 快照。
     *
     * <p><b>为什么需要</b>：{@link #handleByNodeConfig}/{@link #handleByLegacyRule} 对每条已超时的
     * 任务都要 {@code EngineTenantScope.taskQuery(taskService).taskId(..).singleResult()} 判「引擎侧是否已办结」。
     * 在办任务上万、其中大量镜像行在引擎侧并无对应任务（幽灵 PENDING）时，
     * 逐条单行查询会把每轮扫描拖到几十秒（而扫描是 60 秒一次），挤占交互请求。
     *
     * <p><b>口径不变</b>：快照只在 {@link #processTimeout()} 的整轮范围内建立，{@code null} 时
     * （边界定时器单任务路径、人工催办、快照装载失败）一律退回逐条查引擎，与不用快照时同一结论。
     * 快照里查无该 id ⇔ 原 {@code singleResult()} 返回 null，两条分支的返回值按调用点分别对齐。
     */
    private final ThreadLocal<Set<String>> engineTaskSnapshot = new ThreadLocal<>();

    /**
     * 整轮扫描内「24 小时内已催办」的任务 id 快照。
     *
     * <p><b>为什么需要</b>：限频真值是 {@code wf_task_history} 的 {@code URGE} 记录，而
     * {@link #urgedWithinInterval} 是逐任务 COUNT。万级在办下一轮里 7,476 条已超时任务各查一次，
     * 实测 5.3 s 的服务端时间 + 同量级客户端往返等待，把单轮扫描推到 23 s（每 81 s 一轮、
     * 约四分之一的时间在跟库往返）。按租户一次取出 24 小时内的 URGE 任务 id 后，判定退化为集合查找；
     * 口径与逐条 COUNT 完全同源（同一张表、同一个 {@link #URGE_INTERVAL} 窗口）。
     *
     * <p>{@code null} 时（人工催办路径、快照装载失败）退回逐条 COUNT，结论不变。
     */
    private final ThreadLocal<Set<String>> urgedTaskSnapshot = new ThreadLocal<>();

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
     * 超时升级记录的文案前缀（与 {@code empty_strategy} 兜底升级区分开，见 {@link #alreadyEscalated}）：
     * 既是写入侧的唯一出处，也是读取侧唯一的比对依据，两处不得各写一遍字面量。
     */
    static final String ESCALATE_COMMENT_PREFIX = "超时升级";

    /** 卡单聚合告警收件角色（{@code sys_role.role_code}＝流程管理员） */
    static final String STUCK_ALERT_ROLE = "WF_ADMIN";
    /** 卡单聚合告警的站内信类型（{@code sys_message.msg_type}），同时是跨重启去重的依据 */
    static final String STUCK_ALERT_MSG_TYPE = "WF_STUCK_ALERT";
    /** 催办记录的动作码（{@code wf_task_history.action}），同时是跨重启限频的依据 */
    private static final String HISTORY_URGE = "URGE";
    /** 催办/超时提醒的跳转页：与待办视图同页，杜绝 {@code link_url} 为 NULL 的"点不动的催办" */
    private static final String URGE_LINK = "/approval";
    /** 聚合告警最小间隔：同一租户一天一条，避免每分钟把管理员刷一遍 */
    private static final Duration STUCK_ALERT_INTERVAL = Duration.ofHours(24);
    /** 告警正文最多列几条，其余只报总数 */
    private static final int STUCK_ALERT_LINES = 10;
    /** 单人多条超时提醒合并后正文最多列几条（风暴办结，与卡单告警同一折叠规则） */
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
     * AUTO_PASS 白名单：{@code sys_config.config_key}，值为逗号分隔的
     * {@code modelKey:nodeKey}。<b>未配置＝全库一档自动通过都不放行</b>（缺省语义＝NOTIFY），
     * 节点配置里写 AUTO_PASS 也不再"配了就敢自动过"。
     */
    static final String AUTOPASS_NODES_KEY = "wf.timeout.autopass.nodes";
    /** AUTO_PASS 金额敞口上限（sys_config）：单据金额超过即不放行 */
    static final String AUTOPASS_MAX_AMOUNT_KEY = "wf.timeout.autopass.max_amount";
    static final BigDecimal AUTOPASS_MAX_AMOUNT_DEFAULT = new BigDecimal("5000");
    /** 作业节点（POOL）自动回收的催办次数门槛：第 N 次催办仍无人办 → 退回任务池 */
    static final int POOL_RELEASE_AFTER_URGES = 2;
    /**
     * 「催办 → 升级」阶梯档（{@code timeout_action} 的一个取值）。
     *
     * <p><b>为什么需要这一档</b>：{@code timeout_action} 实际只用到 {@code NOTIFY} 与 {@code AUTO_REMIND}
     * 两种值，两个拼写归一后是<b>同一档</b>，即处置档（AUTO_PASS / ESCALATE）<b>零配置</b>，
     * 于是"超时"在实际运行中只等于"一直催"：任务会一直挂在同一批候选人上；
     * {@code wf_task_history} 里的 ESCALATE 全部来自 {@code empty_strategy} 空策略兜底，
     * <b>没有一条来自超时升级</b>。
     *
     * <p>本档语义＝先催办，累计满阈值仍无人办理再升级（超时逐级上收）。
     * 与 {@code ESCALATE} 档的区别：ESCALATE 是 SLA 一到立即升级（无缓冲），本档给出缓冲期。
     *
     * <p><b>改档必须逐节点进行</b>（业务决策）：本档只是新增一个可选取值，
     * <b>不改任何历史节点的行为</b>——配了 NOTIFY 的节点仍只催办。
     */
    static final String ACT_ESCALATE_LADDER = "ESCALATE_AFTER_URGES";
    /**
     * 阶梯档的催办次数阈值（{@code sys_config.config_key}）：同一任务累计催办满 N 次仍无人办理，
     * 升级给沿汇报线第 {@code wf_node_config.up_level} 级真实负责人（绝不落 admin）。
     * 设 0 / 负数＝该档不升级（退化为纯催办）。
     */
    static final String ESCALATE_AFTER_URGES_KEY = "wf.timeout.escalate.after_urges";
    static final int ESCALATE_AFTER_URGES_DEFAULT = 3;
    /** 升级记录的动作名（{@code wf_task_history.action}，与 WfEngineFacade.escalateByTimeout 同源） */
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
            // wf_flow_config 的 TIMEOUT 规则只是可选叠加层：节点配置才是主规则，
            // 规则表空不空都不能跳过整轮扫描。
            List<WfFlowConfig> rules = ignoreTenant(() -> wfFlowConfigMapper.selectList(
                    new LambdaQueryWrapper<WfFlowConfig>()
                            .eq(WfFlowConfig::getCfgType, "TIMEOUT")
                            .eq(WfFlowConfig::getStatus, "NORMAL")));
            // 催办提醒先进缓冲、扫描末尾合并，缓冲的生命周期严格等于一次扫描
            Map<String, List<UrgeLine>> digest = new LinkedHashMap<>();
            urgeDigest.set(digest);
            // 引擎侧在办任务 id 快照：一条查询替代逐条 singleResult()（见 engineTaskSnapshot 注释）。
            // 装载失败则保持 null ⇒ 全部退回逐条查引擎，结论与用快照时一致。
            try {
                Set<String> engineIds = new HashSet<>();
                for (Task ft : EngineTenantScope.taskQuery(taskService).list()) {
                    engineIds.add(ft.getId());
                }
                engineTaskSnapshot.set(engineIds);
            } catch (Exception ex) {
                log.warn("超时扫描引擎任务快照装载失败，本次退回逐条查引擎: {}", ex.getMessage());
            }
            try {
                // 闸门数据按租户批量装载一次，替代逐任务的两次往返：
                // 在办单上万后，逐任务发「实例状态 + 单据终态」查询会让本作业自己占满连接池，
                // 把交互请求挤到秒级。批量后每种闸门每租户各一条 IN 查询，
                // 判定口径与逐条完全同源，取不到的一律仍按原规则跳过。
                Map<Long, List<WfTask>> byTenant = new LinkedHashMap<>();
                for (WfTask t : pending) {
                    byTenant.computeIfAbsent(t.getTenantId(), k -> new ArrayList<>()).add(t);
                }
                List<WfFlowConfig> taskRules = rules == null ? List.of() : rules;
                for (Map.Entry<Long, List<WfTask>> e : byTenant.entrySet()) {
                    Long tenantId = e.getKey();
                    List<WfTask> tasks = e.getValue();
                    // 本轮限频真值先按租户一次取全：逐任务 COUNT 在万级在办下是本轮的主要往返成本
                    try {
                        TenantContext.callAs(tenantId, () -> urgedTaskSnapshot.set(loadUrgedTaskIds()));
                    } catch (Exception ex) {
                        log.warn("超时扫描催办快照装载失败（租户 {} 本次退回逐条判定）: {}", tenantId, ex.getMessage());
                    }
                    // 扫描跨租户，处置必须回到该待办自己的租户：节点配置读取、催办写入数据库、引擎推进
                    // 全是租户表，缺上下文会被拦截器 fail-closed 拦下，整批抛错、三档动作一条都触发不了
                    Gate gate = null;
                    try {
                        // TenantContext.callAs 返回 void（本仓库既定用法）：结果用一元数组带回
                        Gate[] holder = new Gate[1];
                        TenantContext.callAs(tenantId, () -> holder[0] = loadGate(tasks));
                        gate = holder[0];
                    } catch (Exception ex) {
                        log.warn("超时扫描闸门批量装载失败（租户 {} 本次退回逐条判定）: {}", tenantId, ex.getMessage());
                    }
                    final Gate g = gate;
                    for (WfTask t : tasks) {
                        try {
                            TenantContext.callAs(tenantId, () -> handleTimeoutTask(t, taskRules, g));
                        } catch (Exception ex) {
                            log.error("超时任务处理失败 taskId={}", t.getTaskId(), ex);
                        }
                    }
                }
            } finally {
                urgeDigest.remove();
                engineTaskSnapshot.remove();
                urgedTaskSnapshot.remove();
            }
            // 催办按收件人合并投递：逐任务发信的话，一个人同时压着几条超时单
            // 就会在一轮里收到几条同义站内信。合并成一轮一人一条。
            flushUrgeDigest(digest);
            // 卡单聚合上报：处置档只覆盖配了阶梯档的节点，
            // 其余催办档节点催满也不出声——本段补的就是"出声"这一环。
            alertStuckTasks(pending);
        } catch (Exception ex) {
            log.error("审批超时扫描异常", ex);
        }
        });
    }

    /**
     * 卡单聚合上报（只催不升的那一半）。
     *
     * <p>补的是"出声"，不是"处置"：多数节点的 {@code timeout_action} 是催办档
     * （AUTO_REMIND 与 NOTIFY 归一后同义），处置档只覆盖少数节点。于是催办满 N 次的单子只会一直催同一批
     * 办理人，没有任何人知道它卡住了。本方法把"催满阈值仍 PENDING"的待办按租户聚合成
     * <b>一条</b>站内信发给流程管理员（{@value #STUCK_ALERT_ROLE}），一天最多一条。
     *
     * <p><b>明确不做</b>：不改派、不自动通过、不动 {@code assignee}（是否升级属业务决策，走阶梯档
     * {@code ESCALATE_AFTER_URGES} 逐节点改档）。看板的红牌标记同为派生态，不写入数据库。
     *
     * <p>收件人为空时只记 warn 不静默丢弃——"告警发不出去"本身就是要暴露的配置缺陷。
     *
     * <p>阈值（sys_config）、收件人（sys_role/sys_user）、站内信写入数据库（sys_message）<b>全是租户表</b>，
     * 而扫描是跨租户跑的，所以整段必须回到该租户的上下文里执行；放在外面会被租户拦截器 fail-closed
     * 拦下，阈值读不到、只能按默认值跑，配了也不生效＝假配置。
     */
    private void alertStuckTasks(List<WfTask> pending) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        Map<Long, List<WfTask>> byTenant = new LinkedHashMap<>();
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
            // 卡单告警同样要过双条件校验——对已作废/已终结单据"出声上报"与继续催办是同一种打扰
            if (wfQueryService.isStuck(t.getUrgedCount()) && urgeEligible(t)) {
                stuck.add(t);
            }
        }
        if (stuck.isEmpty()) {
            return;
        }
        String dedupKey = "wf:timeout:stuck:alert:" + tenantId;
        // 去重不能只靠内存键：重启即清零，第二天同一批单、同一批收件人会再收一遍。
        // 去重真值改读已发出的告警本身（站内信写入数据库即凭据），跨重启、跨实例一致。
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
     * 本租户 24 小时内是否已发过卡单聚合告警（的跨重启去重）。
     * 读失败按"未发过"放行，与催办限频同一 fail-open 取向——少发一条告警比漏发整档出声更可接受。
     * 必须在租户上下文内调用：{@code sys_message} 是租户表，租户条件由拦截器注入。
     */
    private boolean stuckAlertSentRecently() {
        try {
            Long n = sysMessageMapper.selectCount(
                    new LambdaQueryWrapper<SysMessage>()
                            .eq(SysMessage::getMsgType, STUCK_ALERT_MSG_TYPE)
                            .ge(SysMessage::getCreateTime,
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
            SysUser u = sysUserMapper.selectById(t.getAssigneeId());
            return u == null ? String.valueOf(t.getAssigneeId())
                    : (u.getNickName() == null || u.getNickName().isBlank()
                        ? u.getUserName() : u.getNickName() + "(" + u.getUserName() + ")");
        } catch (Exception ex) {
            return String.valueOf(t.getAssigneeId());
        }
    }

    /**
     * 扫描内预取的闸门数据：<b>只读快照</b>，让逐任务判定不再各发一次查询。
     *
     * @param instStatus  {@code proc_inst_id → wf_instance.status}（缺失＝查无实例，按原规则跳过）
     * @param terminalBiz {@code bizType#bizId} 集合＝单据已到最终状态（按原规则跳过）
     */
    public record Gate(Map<String, String> instStatus, java.util.Set<String> terminalBiz) {
    }

    /**
     * 批量装载闸门（在本租户上下文里调用）：实例状态一次 IN 查完，单据终态按 bizType 分组一次 IN 查完。
     * <p>口径与 {@link #urgeEligible(WfTask)} 逐条判定同源——同一张 {@code wf_instance}、同一份
     * {@link BillTerminalChecker} 终态集、同一 fail-open 方向（读不到的单据不算终态）。
     */
    private Gate loadGate(List<WfTask> tasks) {
        java.util.Set<String> procs = new java.util.LinkedHashSet<>();
        Map<String, java.util.Set<Long>> idsByType = new LinkedHashMap<>();
        for (WfTask t : tasks) {
            if (t.getProcInstId() != null && !t.getProcInstId().isBlank()) {
                procs.add(t.getProcInstId());
            }
            if (t.getBizType() != null && t.getBizId() != null) {
                idsByType.computeIfAbsent(t.getBizType(), k -> new java.util.LinkedHashSet<>()).add(t.getBizId());
            }
        }
        Map<String, String> status = wfQueryService.statusesByProcInstIds(procs);
        java.util.Set<String> terminal = new java.util.HashSet<>();
        for (Map.Entry<String, java.util.Set<Long>> e : idsByType.entrySet()) {
            try {
                for (Long id : billTerminalChecker.terminalBillIds(e.getKey(), e.getValue())) {
                    terminal.add(e.getKey() + "#" + id);
                }
            } catch (Exception ex) {
                log.warn("超时扫描：单据终态批量判定失败 bizType={}（该类型本次按未收尾放行）: {}",
                        e.getKey(), ex.getMessage());
            }
        }
        return new Gate(status, terminal);
    }

    /**
     * 单个任务的超时处置（<b>可复用入口</b>）：边界定时器 Delegate 与 60 秒兜底扫描共用本方法，
     * 保证两条触发路径的三档语义、白名单门槛、记录规则完全一致（抽方法而非复制一份逻辑）。
     * <p>调用前提：当前线程已带该任务所属租户上下文（节点配置/催办写入数据库/引擎推进都是租户表）。
     *
     * @param rules 旧 {@code wf_flow_config} TIMEOUT 规则（可选叠加层）；定时器路径传空表即可
     */
    public void handleTimeoutTask(WfTask t, List<WfFlowConfig> rules) {
        handleTimeoutTask(t, rules, null);
    }

    /**
     * 同上，闸门走预取的 {@link Gate}（{@code null}＝逐条查库，单任务路径与批量装载失败时的退回档）。
     */
    public void handleTimeoutTask(WfTask t, List<WfFlowConfig> rules, Gate gate) {
        if (t == null || t.getCreateTime() == null) {
            return;
        }
        // 双条件校验：催办扫描只处置「实例 RUNNING 且单据非最终状态」的在办任务。
        // 作废单（VOID）的流程往往没被终止，镜像行滞留在 PENDING，不校验就会被每分钟扫到、空转催办。
        // 校验在处置入口而非仅扫描循环：边界定时器路径同样受约束，两条触发路径规则一致。
        if (!urgeEligible(t, gate)) {
            return;
        }
        if (handleByLegacyRule(t, rules == null ? List.of() : rules)) {
            return;
        }
        handleByNodeConfig(t);
    }

    /**
     * 催办/超时处置双条件校验（(1) AND (2) 全真才放行）：
     * <ol>
     *  <li><b>实例 RUNNING</b>：按 {@code proc_inst_id} 读 wf_instance，最终状态（CANCELLED/APPROVED/REJECTED）
     *      或查无实例（幽灵待办）一律跳过——对已死实例催办只会打扰人。</li>
     *  <li><b>单据非最终状态</b>：{@link #billTerminal} 现按采购订单面办结（VOID/CLOSED/ARCHIVED）。</li>
     * </ol>
     * 读取取向：实例环节 fail-closed（状态不可判定宁可跳过不催，宁可不催不漏判）；单据环节 fail-open
     * （读失败不阻断催办，与限频设施"宁可多催不漏催"同向）。跳过只记 debug：
     * 最终状态单会在一分钟内被反复扫到，info 会刷屏。
     */
    private boolean urgeEligible(WfTask t) {
        return urgeEligible(t, null);
    }

    /**
     * 同上；{@code gate != null} 时实例状态与单据终态取预取快照。
     * <p>判定取向与逐条路径同源：快照里查无实例＝按原规则跳过（fail-closed）；快照里没有该单据＝非终态放行（fail-open）。
     */
    private boolean urgeEligible(WfTask t, Gate gate) {
        if (t == null) {
            return false;
        }
        String procInstId = t.getProcInstId();
        if (procInstId == null || procInstId.isBlank()) {
            return false;
        }
        if (gate != null) {
            String st = gate.instStatus().get(procInstId);
            if (st == null || WfQueryService.isTerminalInstanceStatus(st)) {
                log.debug("催办闸跳过（实例非 RUNNING）: procInstId={}, instStatus={}, taskId={}",
                        procInstId, st == null ? "缺失" : st, t.getTaskId());
                return false;
            }
            if (t.getBizType() != null && t.getBizId() != null
                    && gate.terminalBiz().contains(t.getBizType() + "#" + t.getBizId())) {
                log.debug("催办闸跳过（单据已终态）: bizType={}, bizId={}, taskId={}",
                        t.getBizType(), t.getBizId(), t.getTaskId());
                return false;
            }
            return true;
        }
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst == null || WfQueryService.isTerminalInstanceStatus(inst.getStatus())) {
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
     * 业务单据是否已收尾（最终状态）。判定真值表统一办结在 {@link BillTerminalChecker}
     * （与待办查询 WfQueryService.myTodo / WfEngineFacade.todo 共用同一规则，杜绝多处分写漂移）；
     * 其余 bizType 返回 false＝放行。读取失败按非最终状态放行（不阻断催办）。
     */
    private boolean billTerminal(WfTask t) {
        return billTerminalChecker.isBillTerminal(t.getBizType(), t.getBizId());
    }

    /**
     * 到期时刻真值：<b>优先 {@code wf_task.due_time}</b>，
     * 取不到才用 {@code create_time + timeout_hours} 回算，且回算走工作日历。
     * 两处规则必须一致，否则"同一张单在定时器与兜底作业里到期时间不同"。
     *
     * <p>回算时按单据所属<b>公司</b>取日历（按组织取日历）；{@link #orgOf} 解析不到组织时
     * 返回 {@code null}，日历服务会回落租户规则。
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
     * 解析不到一律返回 {@code null}，由日历服务回落租户规则（零影响）。
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
     * 现统一归一到三档规则执行。原 AUTO_ESCALATE 只是"给候选组加一个角色"——加完没人认领还是没人办，
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
            // 到期真值优先取 wf_task.due_time（含工作日历规则），取不到才按自然/工作小时回算
            LocalDateTime due = dueOf(t, hours);
            if (LocalDateTime.now().isBefore(due)) {
                continue;
            }
            String action = normalizeAction(extractString(json, "action"));
            // 24 小时内已催过且没有待办的池回收 ⇒ 本轮无事可做，连引擎单查都不发（同 handleByNodeConfig）
            if (ACT_REMIND.equals(action) && alreadyUrged(t) && !poolReleaseDue(t)) {
                return true;
            }
            if (engineTaskGone(t.getTaskId())) {
                // 引擎侧已办结，镜像行是幽灵 PENDING：交给双账补齐脚本，超时动作不空转
                return true;
            }
            Task ftask = EngineTenantScope.taskQuery(taskService).taskId(t.getTaskId()).singleResult();
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
     * 引擎任务快照里是否已无该任务（幽灵 PENDING 判定）。
     *
     * <p>{@code false} 有两种含义：快照里<b>有</b>该任务（正常在办），或本次<b>没有</b>装载快照
     * （单任务路径/装载失败）——后者必须继续走逐条 {@code singleResult()} 的原有判定，故这里只回答
     * "可以确定已经不在引擎了"，绝不把"不确定"当成"已办结"。
     */
    private boolean engineTaskGone(String taskId) {
        Set<String> snap = engineTaskSnapshot.get();
        return snap != null && taskId != null && !snap.contains(taskId);
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
        // 到期真值优先取 wf_task.due_time（含工作日历规则），取不到才按工作小时回算
        LocalDateTime due = dueOf(t, timeoutHours);
        if (LocalDateTime.now().isBefore(due)) {
            return;
        }
        String action = normalizeAction(timeoutAction);
        if (action == null) {
            // 不认识的拼写按最轻副作用处理（催办），而不是静默丢弃整档配置
            log.warn("未识别的超时动作: taskId={}, action={}，按催办处理", t.getTaskId(), timeoutAction);
            action = ACT_REMIND;
        }
        // 24 小时内已催过、且没有待办的池回收 ⇒ 本轮无事可做：连引擎单查都不发。
        // 万级在办下这一条就是本轮的主要往返成本（每条约 0.2 ms 服务端 + 等量客户端等待）。
        if (ACT_REMIND.equals(action) && alreadyUrged(t) && !poolReleaseDue(t)) {
            return;
        }
        if (engineTaskGone(t.getTaskId())) {
            return;
        }
        Task ftask = EngineTenantScope.taskQuery(taskService).taskId(t.getTaskId()).singleResult();
        if (ftask == null) {
            return;
        }
        String reason = "节点 SLA " + timeoutHours + " 小时，已超时 " + overdueHours(t) + " 小时";
        // 升级层级随节点配置下传：up_level 是节点列，忽略它就只能升一级，配了几级都白配
        apply(action, t, ftask, reason, null, nodeConfig.getUpLevel());
    }

    /** 按归一后的三档语义分派执行。 */
    private void apply(String action, WfTask t, Task ftask, String reason, String legacyTargetRole,
                       Integer upLevel) {
        switch (action) {
            case ACT_AUTO_PASS -> {
                // 会签节点不支持超时自动处理：AUTO_PASS 是白名单制，默认一档都不放行＝退回催办；
                // 节点配置里写 AUTO_PASS 也不等于"配了就敢自动过"。
                String deny = autoPassDenial(t, ftask);
                if (deny != null) {
                    // 不放行时若直接落回催办，就是一路催到天荒地老、没有终局（会签节点上的并行实例会同时被催）。
                    // 超时策略两档都要有终局——要么自动执行，要么"超时后自动转给指定处理人"。
                    // 所以改走既有阶梯档：阈值内仍只催办，满 wf.timeout.escalate.after_urges 次后
                    // 沿汇报线升级给真实负责人（绝不落 admin）。
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
     * AUTO_PASS 放行判定。返回 {@code null} 表示允许自动通过，否则返回拒因（写进催办文案与日志）。
     *
     * <p>四道门槛：
     * <ol>
     *  <li><b>白名单</b>：{@code sys_config.wf.timeout.autopass.nodes}（逗号分隔 {@code modelKey:nodeKey}）
     *      未配置/不含本节点 → 不放行。缺省空＝AUTO_PASS 全库不生效，等价于默认 NOTIFY；</li>
     *  <li><b>无金额敞口</b>：单据金额超 {@code wf.timeout.autopass.max_amount}（默认 5000）→ 不放行；</li>
     *  <li><b>节点未绑定单据操作</b>：任务池/共享池作业节点（付款、记账、归档等落地动作）→ 不放行，
     *      同样只对纯审批节点做自动执行；</li>
     *  <li><b>非会签/并联会审</b>：Flowable 多实例节点（有 {@code nrOfInstances} 变量）→ 不放行。</li>
     * </ol>
     * 另加本系统铁律：机器路径记录的办理人不得为 admin（零 admin）。
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
        BigDecimal amount = t.getAmount();
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
     * 判定失败按"非多实例"处理（白名单本身已是主校验，不因变量读取失败而放开不该放的节点）。
     */
    private boolean isMultiInstance(Task ftask) {
        try {
            return taskService.getVariables(ftask.getId()).containsKey("nrOfInstances");
        } catch (Exception e) {
            log.warn("会签（多实例）判定异常，按非会签处理 taskId={}: {}", ftask.getId(), e.getMessage());
            return false;
        }
    }

    private BigDecimal autPassMaxAmount() {        String raw = configValue(AUTOPASS_MAX_AMOUNT_KEY);
        if (raw != null && !raw.isBlank()) {
            try {
                BigDecimal v = new BigDecimal(raw.trim());
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
        return Arrays.stream(csv.split("[,，;；\\s]+"))
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
     * 超时动作别名归一。库内历史只写过 {@code NOTIFY}，但配置页/文档/旧规则里出现过
     * AUTO_REMIND、REMIND、URGE、TRANSFER、ESCALATE、AUTO_ESCALATE、REDISTRIBUTE 等拼法；
     * 不归一则这些配置整档失效。归一后只有三档，且 {@code NOTIFY} 继续可用（602 行历史语义不变）。
     *
     * <p>新增阶梯档别名：{@code ESCALATE_AFTER_URGES} /
     * {@code URGE_THEN_ESCALATE} / {@code REMIND_THEN_ESCALATE} 归一为
     * {@link #ACT_ESCALATE_LADDER}。
     *
     * @return 规范值，无法识别返回 null（由调用方兜底为催办）
     */
    static String normalizeAction(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toUpperCase(Locale.ROOT)) {
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
     * @return 本次催办之后该任务的累计催办次数（被限频跳过时返回当前值），供阶梯档判断是否该升级
     */
    private int handleNotify(WfTask t, Task ftask, String reason) {
        String content = "您有审批任务「" + (t.getTitle() != null ? t.getTitle() : "") + "」（节点："
                + t.getNodeName() + "）已超时，" + reason + "，请尽快处理。";
        int before = t.getUrgedCount() == null ? 0 : t.getUrgedCount();
        int urged = sendUrge(t, ftask, "TIMEOUT_REMINDER", "审批超时催办通知", content, "超时催办", reason, null);
        if (urged > before) {
            // 违约记录：催办真正发出（未被 24h 限频拦下）＝本任务 SLA 违约成立，
            // 复用 compliance 现有写入位置落 op_sla_breach 一行；限频即天然去重，同一窗口不重复落行。
            recordSlaBreach(t, reason);
        }
        // 作业节点（POOL）有人领取却办不动 → 催办满 {@value #POOL_RELEASE_AFTER_URGES} 次自动回收，
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
     * 违约落行：审批任务超时（SLA 违约）时向 {@code op_sla_breach} 写一条记录，
     * 复用 compliance 既有的写入位置与列规则（{@code SlaMonitorService} 同表写入：task_id 挂任务主键、
     * bill_id/bill_type 挂业务单据、breach_type=BREACH、breach_time=检测时刻）。
     *
     * <p>去重：只在 {@link #handleNotify} 的催办真正发出时写入，天然继承 {@code URGE_INTERVAL}（24h）
     * 限频——同一任务同一窗口只落一行，绝不让每分钟扫描把违约表刷爆。
     * <b>租户显式落值</b>：与 SlaMonitorService 同因（定时/引擎线程依赖 ThreadLocal 会漏填 tenant_id，
     * 该列 NOT NULL）。
     */
    private void recordSlaBreach(WfTask t, String reason) {
        try {
            OpSlaBreach breach = new OpSlaBreach();
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
            // 违约记录是附加可观测性：写失败只 warn，绝不阻断催办主链路（与站内信容错同一规则）
            log.warn("SLA 违约落行失败（不阻断催办）taskId={}: {}", t.getTaskId(), e.getMessage());
        }
    }

    /**
     * 催办的唯一发信通道：限频判定 → 站内信 → 计次 → 时间线记录。
     * 超时作业与发起人催办共用，两处规则不会漂移；而 POOL 回收这类"顺带改任务归属"的
     * 副作用留在超时调用方，人工催办不触发。
     *
     * <p><b>限频是两段式的</b>：内存标记只是同一进程内的快路径，
     * <b>真值是 {@code wf_task_history} 上这条任务最近一次 {@code URGE} 记录的时刻</b>——
     * 记录与发信本就是同一动作的两面（下面第 4 步写的就是它），因此限频状态天然跨重启、跨实例，
     * 不需要新表。只靠内存那一段的话，重启即清零，同一个节点几分钟内就会被再催一遍。
     *
     * @param historyLabel 写进 {@code wf_task_history} 的前缀（"超时催办" / "发起人催办"）
     * @param reason 催办缘由，随次数一起落进记录
     * @param operUserId 催办发起人；超时作业传 null（无人身后可归因），人工催办必须带，否则记录答不出"谁催的"
     * @return 该任务的累计催办次数（命中 24 小时限频时返回当前值，不重复发信）
     */
    private int sendUrge(WfTask t, Task ftask, String msgType, String title, String content,
                         String historyLabel, String reason, Long operUserId) {
        String urgeKey = urgeKey(t);
        boolean alreadyUrged = alreadyUrged(t);
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
        // 催办次数改走 WfQueryService（原 wfTaskMapper.updateById 绕过了镜像表的统一写入口，
        // 与"镜像表写路径只有一个办结点"的约束冲突）。
        wfQueryService.bumpUrgedCount(t.getTaskId(), urged);
        log.info("{}: taskId={}, node={}, 对象数={}, urgedCount={}",
                historyLabel, t.getTaskId(), t.getNodeName(), targets.size(), urged);
        // 催办动作补进时间线（作业线程无登录人，显式带租户上下文才写得进记录）
        // 这条 URGE 记录同时是 {@link #urgedWithinInterval} 的限频真值，两者不得拆成两处规则。
        TenantContext.callAs(t.getTenantId(), () -> wfEngineFacade.recordJobAction(t.getProcInstId(),
                t.getTaskId(), t.getNodeKey(), t.getNodeName(), HISTORY_URGE, operUserId, t.getAssigneeId(),
                historyLabel + "（第 " + urged + " 次）：" + reason));
        return urged;
    }

    /**
     * 本任务是否在 {@link #URGE_INTERVAL} 内已被催办：内存标记 → 本轮快照 → 逐条 COUNT。
     *
     * <p>三档读的是同一份真值（{@code wf_task_history} 的 {@code URGE} 记录），只是成本递减：
     * 前两档是进程内查找，第三档才是一次库往返，只在人工催办与快照装载失败时走到。
     * 任何一档异常一律按"未催办"放行——限频设施不可用则宁可多催，绝不因一次异常关掉催办档。
     */
    private boolean alreadyUrged(WfTask t) {
        if (t == null || t.getTaskId() == null) {
            return false;
        }
        try {
            if (distributedState.marked(urgeKey(t))) {
                return true;
            }
            Set<String> snap = urgedTaskSnapshot.get();
            if (snap != null) {
                return snap.contains(t.getTaskId());
            }
            return urgedWithinInterval(t);
        } catch (Exception e) {
            return false;
        }
    }

    /** 催办限频键（同一任务 24 小时内只催一次） */
    private static String urgeKey(WfTask t) {
        return "wf:timeout:urge:" + t.getTaskId();
    }

    /**
     * 已催办的任务里唯一"还要做事"的分支：作业节点的催满自动回收（判据与 {@link #handleNotify} 逐字一致）。
     * 早退闸必须放它过去，否则回收动作会随早退一起被跳过。
     */
    private boolean poolReleaseDue(WfTask t) {
        int urged = t.getUrgedCount() == null ? 0 : t.getUrgedCount();
        return urged >= POOL_RELEASE_AFTER_URGES && t.getAssigneeId() != null && isWorkNode(t);
    }

    /**
     * 本轮已催办任务 id 的一次性装载（租户上下文由调用方建立）。
     *
     * <p>取 {@code (tenant_id, oper_time)} 前缀再按 {@code action='URGE'} 过滤：24 小时分区上实测
     * 111 ms 读 4.2 万行、留下 7,476 个 id，替代同样 7,476 次逐条 COUNT。
     */
    private Set<String> loadUrgedTaskIds() {
        LocalDateTime since = LocalDateTime.now().minus(URGE_INTERVAL);
        List<WfTaskHistory> rows = wfTaskHistoryMapper.selectList(
                new LambdaQueryWrapper<WfTaskHistory>()
                        .select(WfTaskHistory::getTaskId)
                        .eq(WfTaskHistory::getAction, HISTORY_URGE)
                        .ge(WfTaskHistory::getOperTime, since));
        Set<String> out = new HashSet<>();
        for (WfTaskHistory h : rows) {
            if (h.getTaskId() != null) {
                out.add(h.getTaskId());
            }
        }
        return out;
    }

    /**
     * 本任务在 {@link #URGE_INTERVAL} 内是否已被催办过 —— 读 {@code wf_task_history} 的 {@code URGE} 记录。
     *
     * <p>记录按 {@code (tenant_id, proc_inst_id)} 走 {@code idx_tenant_inst}，再按 {@code task_id + oper_time}
     * 收窄（{@code oper_time} 是分区列，带上它即只扫近期分区）。
     *
     * <p>读取失败一律返回 {@code false}（放行）：限频设施不可用则宁可多催，绝不因一次查询异常
     * 把催办／升级阶梯整档关掉。
     */
    private boolean urgedWithinInterval(WfTask t) {
        if (t == null || t.getTaskId() == null) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minus(URGE_INTERVAL);
        Long[] holder = new Long[1];
        Runnable query = () -> holder[0] = wfTaskHistoryMapper.selectCount(
                new LambdaQueryWrapper<WfTaskHistory>()
                        .eq(WfTaskHistory::getTaskId, t.getTaskId())
                        .eq(WfTaskHistory::getAction, HISTORY_URGE)
                        .ge(WfTaskHistory::getOperTime, since)
                        // 22:3x 修：这里原本是 .ge（区间比较），本意是"限定到这个流程实例"。
                        // 区间谓词让 idx_tenant_inst 退化成半个表的范围扫——单次 126 ms / 3.7 万行，
                        // 作业每秒 8 次把整库算力吃满（万级在办下每次跑量前都得先等它）。改等值后
                        // EXPLAIN rows=1，走 (tenant_id, proc_inst_id) 精确前缀。
                        .eq(t.getProcInstId() != null,
                                WfTaskHistory::getProcInstId, t.getProcInstId()));
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

    /** 投递一条催办提醒（站内信 + 按配置的多渠道）；单条失败只记日志，绝不中断本次其余处置。 */
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
     * 扫描末尾合并投递：同一收件人在一次扫描里收到的多条超时提醒合成<b>一条</b>站内信。
     *
     * <p>只合并不丢弃：正文逐条列出各环节，超过 {@value #URGE_DIGEST_LINES} 条折叠为总数；
     * 联查键 {@code (biz_type, biz_id)} 取第一条（合并信本就指向"去看你的待办"这一页）。
     * 单条时原样投递，不多包一层。
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
     * 发起人催办。
     *
     * <p>补的是入口，不是机制：催办通道一直存在，但只有超时作业会按它。提单人这边没有任何按钮，
     * 只能干等——单据在谁手里、卡在哪一节，发起人本是最关心的人，却是唯一不能出声的人。
     *
     * <p>限频与超时催办共用同一把键（{@code wf:timeout:urge:<taskId>}，24 小时）：代价是发起人这一催
     * 可能占掉作业催办的额度，即同一环节一天内只打扰一次，比"人工催 + 作业催"各发一遍更克制。
     *
     * <p>命中限频抛业务异常而不是静默返回 0——界面必须让用户知道"这一催没发出去"。
     *
     * @param initiatorId 发起人用户 id，落进 URGE 记录的 oper_user_id
     * @param initiatorName 发起人姓名，进通知文案与 URGE 记录
     * @return 本次实际催办的任务数（并行/会签多任务时 &gt; 1）
     */
    private int urgeByInitiator(String procInstId, Long initiatorId, String initiatorName) {
        List<Task> open = EngineTenantScope.taskQuery(taskService).processInstanceId(procInstId).active().list();
        if (open.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
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
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "24 小时内已催办过，同一环节每天只催一次；提醒已发给办理人");
        }
        return urged;
    }

    /**
     * 发起人催办的鉴权入口（控制器只递 procInstId + 当前登录人，判定规则集中在本类）。
     *
     * <p>鉴权看 {@code wf_instance.start_user_id}：催办的语义是"我问一句我这张单怎么样了"，
     * 别人问就没有依据——非发起人调用一律 403，不做静默通过。
     */
    public int urgeAsInitiator(String procInstId, Long userId, String userLabel) {
        WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
        if (inst == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "流程实例不存在: " + procInstId);
        }
        if (!Objects.equals(inst.getStartUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "只有发起人本人可以催办本单（该单由用户 " + inst.getStartUserId() + " 提交）");
        }
        return urgeByInitiator(procInstId, userId, userLabel == null || userLabel.isBlank()
                ? String.valueOf(userId) : userLabel);
    }

    /**
     * 本任务是否已被<b>超时</b>升级过（读升级记录，"升级后不再二次超时"）。
     * 读取失败按"未升级过"处理：宁可再升一次级，也不要把超时治理整体关掉。
     *
     * <p>只看 {@code action = ESCALATE} 不够：{@code empty_strategy} 取人兜底也借用同一个
     * 动作码写记录，于是任务一落地就被判成"已升级过"，阶梯升级与 AUTO_PASS 退路档被永久短路。
     * {@code action} 分不开这两个机制，只有文案前缀分得开 ⇒ 收窄为"超时升级"记录。
     */
    private boolean alreadyEscalated(String taskId) {
        try {
            Long n = wfTaskHistoryMapper.selectCount(
                    new LambdaQueryWrapper<
                            WfTaskHistory>()
                            .eq(WfTaskHistory::getTaskId, taskId)
                            .eq(WfTaskHistory::getAction, HISTORY_ESCALATE)
                            .likeRight(WfTaskHistory::getComment, ESCALATE_COMMENT_PREFIX));
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
     * <p>只按 {@code assignee_id != null} 发通知是不够的：最需要催的"无人认领"形态会一条都催不到。
     */
    private List<Long> notifyTargets(WfTask t, Task ftask) {
        List<Long> out = new ArrayList<>();
        if (t.getAssigneeId() != null) {
            out.add(t.getAssigneeId());
            return out;
        }
        // 与待办视图同一份候选人解析规则（1,2,3, 包裹、非数字段跳过）
        out.addAll(WfQueryService.parseCandidateIds(t.getCandidateIds()));
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
     * <p>不能 {@code addCandidateGroup(task, "admin")}：既违背"任何流程节点都不允许 admin 参与"的铁律，
     * 且 {@code sys_role} 里没有裸 {@code ADMIN} 这个 role_code，按 {@code canOperate} 的
     * "候选组 ∩ 用户角色"规则没人命中＝任务悬空。升级规则与 {@code empty_strategy=ESCALATE} 共用
     * {@link NodeOrgResolver#escalateTarget(String)} 一套。
     */
    private void handleEscalate(WfTask t, Task ftask, String reason, String legacyTargetRole, Integer upLevel) {
        // 升级后不再二次超时（"指定处理人"档明确"收到任务后没有处理时长的限制"）。
        // 判断依据用升级记录本身（wf_task_history 的 ESCALATE 行），不加列、不改表；升级后仍会催办，
        // 但不再自动通过、也不再反复升级换人——否则一张单会在汇报线上一路踢上去。
        if (alreadyEscalated(t.getTaskId())) {
            log.info("审批任务已升级过，本次不再二次升级，仅催办: taskId={}, node={}", t.getTaskId(), t.getNodeName());
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
     * 「催办 → 升级」阶梯档（{@code timeout_action = ESCALATE_AFTER_URGES}）。
     *
     * <p>先按催办档发通知并累加 {@code urged_count}；累计满
     * {@code sys_config.wf.timeout.escalate.after_urges}（默认 {@value #ESCALATE_AFTER_URGES_DEFAULT}）
     * 仍无人办理时，升级给沿汇报线第 {@code up_level} 级真实负责人（绝不落 admin，
     * 复用 {@link #handleEscalate} 的同一套规则与"升级后不再二次升级"约束）。
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

    /** 已超时小时数（整除，用于提示文案与统计方式一致）。 */
    private long overdueHours(WfTask t) {
        return Duration.between(t.getCreateTime(), LocalDateTime.now()).toHours();
    }

    private long extractHours(String json) {
        try {
            JsonNode n = new ObjectMapper()
                    .readTree(json).get("timeoutHours");
            return n == null ? 0 : n.asLong();
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractString(String json, String key) {
        try {
            JsonNode n = new ObjectMapper()
                    .readTree(json).get(key);
            return n == null ? "" : n.asText();
        } catch (Exception e) {
            return "";
        }
    }

    /** 跨租户读取：{@code runIgnoreTenant} 只收 Runnable，用单元素数组把结果带出来。 */
    private <T> T ignoreTenant(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
