// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.budget.entity.BgItem;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpLoanOffsetMapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdExpenseStandard;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdExpenseStandardMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.workflow.entity.WfAddSign;
import com.fssc.workflow.entity.WfApprovalItem;
import com.fssc.workflow.entity.WfBusinessFlow;
import com.fssc.workflow.entity.WfDelegate;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.event.WfProcessEndedEvent;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.workflow.mapper.WfBusinessFlowMapper;
import com.fssc.workflow.mapper.WfDelegateMapper;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程引擎门面（Flowable 7 封装）。
 * 架构红线：业务域禁止直接使用 RuntimeService/TaskService 等 Flowable API，
 * 一律经本门面。流程审计（wf_task_history）与业务收尾（WfProcessEndedEvent）在此统一挂接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("master")
public class WfEngineFacade {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;
    private final WfQueryService wfQueryService;
    /** 单据最终状态判定（作废/关闭/归档）：待办查询要剔除最终状态单，规则与 WfQueryService.myTodo 一致 */
    private final BillTerminalChecker billTerminalChecker;
    private final WfBusinessFlowMapper wfBusinessFlowMapper;
    private final WfBpmnTemplateService bpmnTemplateService;
    private final WfProcessConfigService processConfigService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final NotifyService notifyService;
    private final WfNodeConfigMapper wfNodeConfigMapper;
    private final DataSource dataSource;
    /**
     * 节点配置解析器：wf_node_config.node_key 是业务节点编码（N0xx），
     * 运行时 taskDefinitionKey 是 BPMN 生成的 {@code Task_N}，两者不同形、精确匹配恒不命中。
     * 超时链路和节点审批人覆盖都必须走本解析器，否则「节点配置」页签整页读不到值。
     */
    private final WfNodeConfigResolver wfNodeConfigResolver;
    private final WfDelegateMapper wfDelegateMapper;
    /** 加签（前/后加签）持久化层，能力边界见 {@code WfAddSign} 的类注释 */
    private final WfAddSignService wfAddSignService;
    /** 传阅投递：{@code wf_cc} 的唯一写入位置 */
    private final WfCcService wfCcService;
    /**
     * 「审批项」：{@code wf_approval_item} 的唯一消费点。
     * 只有 {@code complete()} 收到 {@code approvalItemCode} 时才读表——未传时连查询都不发生，
     * 不传审批项的调用方感受不到这次改动。
     */
    private final WfApprovalItemService approvalItemService;
    /**
     * 「流程处理详细日志」：{@code wf_process_log} 的唯一写入者（挂 Flowable 全局事件）。
     * 监听器无条件注册，是否真的落库由 {@code sys_config.wf.log.detailed.enabled} 逐事件判定，
     * 配置改动即时生效、不用重启。
     */
    private final WfProcessLogListener processLogListener;
    /**
     * 参与人解析：{@code wf_assignee_scheme} / {@code wf_assignee_dim} 的唯一消费点，
     * 只服务 {@code applyNodeConfig} 的 default 分支（POST/SHARE_POOL/EXPRESSION 这类原本 no-op 的类型）。
     * 没配方案的节点完全不经过它。
     */
    private final WfAssigneeResolver assigneeResolver;
    /**
     * 镜像写失败的可见物载体。镜像（{@code wf_instance} / {@code wf_task} / {@code wf_task_history}）
     * 写失败一律"记缺陷 + 冒泡"：只留一行 log 的话，镜像没写进去也没人知道。
     */
    private final WfMirrorDefectRecorder mirrorDefectRecorder;
    /**
     * 单一真值：镜像投影监听器。开关打开时由它接管 {@code wf_instance} / {@code wf_task} 的写路径，
     * 门面停止手工写（见 {@link #mirrorByFacade()}）；开关关闭时它根本不注册，本字段不参与任何分支。
     */
    private final WfMirrorProjectionListener mirrorProjectionListener;
    /**
     * 开关：{@code fssc.wf.mirror.projection-enabled}，默认 {@code false}。
     *
     * <p>关着时仍由门面手工写镜像；打开后门面停写 {@code wf_instance} / {@code wf_task}，
     * 改由 {@link WfMirrorProjectionListener} 投影，{@code wf_task_history} 在过渡期双写。开关本身就是回滚路径。
     * 非 final + 字段注入：本类用 Lombok {@code @RequiredArgsConstructor} 只注入 final 字段。
     */
    @Value("${fssc.wf.mirror.projection-enabled:false}")
    private boolean projectionEnabled;
    /**
     * 「流程处理模式＝消息队列」的投递载体与模式判定。
     *
     * <p>门面只用到它的一件事：{@link WfProcessOutboxService#queueModeEnabled()}——
     * 模式为 {@code QUEUE} 时门面同样必须停写镜像（投影改由发件箱消费者执行），
     * 否则发件箱与门面会同时投影同一事件（两个写入者争抢同一行）。
     * 判定带 5 秒 TTL 缓存，不会把 {@link #mirrorByFacade()} 变成逐次查库。
     */
    private final WfProcessOutboxService wfProcessOutboxService;
    /** {@code wf_task_history} 过渡期双写的来源标记（开关打开时附加，便于与投影行区分、便于对账比对） */
    private static final String HISTORY_DUAL_WRITE_MARK = "〔门面双写〕";
    private final WfModelMapper wfModelMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;
    /** 借款冲抵：借款单余额要减去冲抵额 */
    private final ExpLoanOffsetMapper expLoanOffsetMapper;
    /** 资料齐套校验（SUBMIT 只告警／NODE 按规则硬拦）；只依赖各域 Mapper，与本类无循环依赖 */
    private final BillCompletenessGuard billCompletenessGuard;
    /**
     * 预算占用域：复用 {@code resolveOccupyItem}（费用项→预算行，含跨主体重定位）
     * 与 {@code available}（可用额＝总额+调整−占用−已用），不自行拼规则。
     * 注意：{@code BudgetService} 反向依赖本门面（发起 {@code BG_SCHEME} 审批流时调它），
     * 二者构成**构造器循环依赖**（Lombok {@code @RequiredArgsConstructor} 双向 final ⇒ 应用启动失败：
     * "wfEngineFacade ↑↓ budgetService"）。故按本项目既有惯例（参见 {@code PurchaseService.assetService}）
     * 改为 {@code @Lazy} 字段注入打破环；非 final 字段不参与 Lombok 构造器。
     */
    @Lazy
    @Autowired
    private BudgetService budgetService;
    private final BgItemMapper bgItemMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    private final MdExpenseStandardMapper mdExpenseStandardMapper;
    /** 多分子公司审批路由解析器：候选组按公司过滤 + 直线上级/公司内角色办理人解析 */
    private final NodeOrgResolver nodeOrgResolver;
    /** 租户参数（sys_config）：complete 读取 approve.deny_self 自审禁止开关 */
    private final SysConfigMapper sysConfigMapper;
    /**
     * 待办列表按流程实例批量取单据键（{@code wf_instance.biz_type/biz_id}）。
     * 不再为了拿 bizType/bizId 去 {@code includeProcessVariables()}——那条路是 Flowable 的
     * "任务+关联实体"查询，1,500 条在办会把全部运行态变量连表拉回，是万级待办的主要耗时。
     */
    private final WfInstanceMapper wfInstanceMapper;
    /** R1 金额分档流程判定缓存：processKey -> 是否含 金额>/原值> 条件，避免每次发起重复查库 */
    private final ConcurrentHashMap<String, Boolean> tieredCache = new ConcurrentHashMap<>();

    /** 报销审批流程定义KEY */
    public static final String PROC_EXPENSE_CLAIM = "expenseClaim";

    /**
     * 本线程正在办结的任务操作人。
     *
     * <p>为什么用线程标记而不是流程变量：PROCESS_COMPLETED 监听器在
     * {@code taskService.complete()} <b>内</b>同步派发（同线程、不换线程，见
     * {@code WfQueryService.COMPLETING_TASK_ID} 的同类做法），而"谁点的这一节"是
     * 一次<b>调用</b>的事实，不属于实例——写成流程变量会留下"最后一个点按钮的人"这一
     * 跨调用的脏值，还要给每个实例加一列无业务语义的变量。
     * 因此与 {@code COMPLETING_TASK_ID} 的 taskId 标记同源：由 {@code complete()} 的
     * {@code operUserId} 形参在 {@code taskService.complete()} 前置入、finally 还原，
     * 监听器构造 {@link WfProcessEndedEvent} 时读出。
     *
     * <p>用 save/restore 而非 remove：会签/子流程同线程嵌套办结时内层不得抹掉外层标记。
     */
    private static final ThreadLocal<Long> END_OPER_USER_ID = new ThreadLocal<>();

    /** 与 {@link #endEndOperator} 成对使用（放在 try/finally 里）；返回上一层的值供还原。 */
    private static Long beginEndOperator(Long operUserId) {
        Long previous = END_OPER_USER_ID.get();
        // 无条件覆盖：本次调用没交代操作人（机器路径 operUserId 传 null）时标记就必须为空，
        // 否则嵌套办结会把外层的"点按钮的人"当成内层结束事件的操作人。
        END_OPER_USER_ID.set(operUserId);
        return previous;
    }

    private static void endEndOperator(Long previous) {
        if (previous == null) {
            END_OPER_USER_ID.remove();
        } else {
            END_OPER_USER_ID.set(previous);
        }
    }

    /** 读当前线程的办结操作人（仅 PROCESS_COMPLETED 监听器构造结束事件时使用）。 */
    private static Long currentEndOperator() {
        return END_OPER_USER_ID.get();
    }

    @PostConstruct
    public void init() {
        // 流程结束事件监听：发布 WfProcessEndedEvent 供业务域收尾（核销/释放）
        runtimeService.addEventListener(new AbstractFlowableEventListener() {
            @Override
            public void onEvent(FlowableEvent event) {
                ProcessInstance pi = (ProcessInstance) ((FlowableEntityEvent) event).getEntity();
                Map<String, Object> vars;
                try {
                    vars = runtimeService.getVariables(pi.getId());
                } catch (Exception ex) {
                    // 不可恢复分支：引擎侧变量已随实例清理，重放/重试都取不回上下文。
                    // 若在此冒泡，isFailOnException=true 会让该流程实例永远无法结束（死循环），
                    // 故记缺陷（WF-MIRROR-003）后返回——缺陷在对账作业下一轮会被重新暴露。
                    mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_ENGINE_BYPASS,
                            "PROCESS_COMPLETED.getVariables", null, "procInstId=" + pi.getId(), ex);
                    return;
                }
                Boolean approved = (Boolean) vars.get("approved");
                String bizType = (String) vars.get("bizType");
                Long bizId = (Long) vars.get("bizId");
                String comment = (String) vars.get("comment");
                String rejectType = (String) vars.get("rejectType");
                if (bizType == null) {
                    // 无业务单据的流程（或子流程结束事件）：无镜像办结、无业务收尾，属正常跳过
                    return;
                }
                // 事件可能脱离发起请求的租户上下文（作业线程 / 异步收尾链路）。镜像表（wf_instance/
                // wf_task/wf_task_history）都带 tenant_id，缺上下文会被租户拦截器 fail-closed 拦下，
                // 表现就是"实例状态永远 RUNNING、幽灵待办清不掉"的静默漂移。故先忽略租户取回归属租户，
                // 再 callAs 建立上下文执行办结；取不到时保持原行为（由下游失败冒泡暴露）。
                WfInstance[] instHolder = new WfInstance[1];
                try {
                    TenantContext.runIgnoreTenant(
                            () -> instHolder[0] = wfQueryService.instanceByProcInstId(pi.getId()));
                } catch (Exception ex) {
                    log.warn("流程结束取租户失败 procInstId={}: {}", pi.getId(), ex.getMessage());
                }
                WfInstance inst = instHolder[0];
                Long tenantId = inst == null ? null : inst.getTenantId();
                runInTenant(tenantId, () -> {
                    // 流程结束记录：APPROVED/REJECTED（wf_task_history 镜像，失败即冒泡）。
                    // 定论行只记实例级结论本身，不能抄流程变量 comment —— 那是末位审批人刚键入的
                    // 那段意见，抄过来会让审计表里出现两行同样的签署内容：定论行与真审批行只差
                    // 一个 action，定论行的 oper_time 还早 1 秒，联查时无法分辨哪行才是"那一节的结论"。
                    // 驳回理由另有 REJECT 行为准，业务侧 exp_bill.reject_reason／WfProcessEndedEvent.comment
                    // 仍取原始变量，不受影响。两行的先后关系由读侧 causalOrder 排序键表达。
                    writeHistory(pi.getId(), null, null, null,
                            Boolean.TRUE.equals(approved) ? "APPROVED" : "REJECTED",
                            null, Boolean.TRUE.equals(approved)
                                    ? "流程定论：审批通过（末节办理意见见同实例 APPROVE 记录行）"
                                    : "流程定论：已驳回（驳回理由见同实例 REJECT 记录行）");
                    // 实例视图状态回写：以 bizType 非空为准，不能拿 bizId != null 一票拦截：
                    // FA_CER（资本性支出申请）没有业务单据表、bizId 恒为 null，那样它的
                    // wf_instance.status 会永远停在 RUNNING。
                    wfQueryService.updateInstanceStatus(pi.getId(),
                            Boolean.TRUE.equals(approved) ? "APPROVED" : "REJECTED",
                            LocalDateTime.now());
                    // 幽灵待办办结：实例结束后，会签/票签同节点的兄弟任务被引擎删掉，
                    // wf_task 里的 PENDING 行没有别的关闭通道，不在这里关就永远挂在待办列表上。
                    // 同时落节点级作废记录，否则这一节在审批链上会被判成「尚未到达」。
                    wfQueryService.closePendingTasksByInstance(pi.getId(),
                            Boolean.TRUE.equals(approved)
                                    ? "流程已办结，同节点未办理的会签/票签任务自动作废"
                                    : "流程已按驳回结束，未办理的在办任务作废");
                });
                if (bizId != null) {
                    // 业务收尾事件：与镜像办结同属"流程结束必须发生的事"，同样失败即冒泡。
                    // 吞进 catch 的话，收尾没跑成也没人知道。
                    WfProcessEndedEvent ended = new WfProcessEndedEvent(
                            bizType, bizId, approved, comment, rejectType, pi.getId());
                    ended.setTenantId(tenantId);
                    // 结束事件带出办结操作人，供业务收尾落追责列。
                    // 取值只来自 complete() 的 operUserId 形参（见 END_OPER_USER_ID 注释）；
                    // 机器路径 / 未经 complete() 的引擎自动结束 ⇒ 保持为空，不反查 wf_task_history
                    // （末节 APPROVE 行晚于本事件写入数据库，反查＝取到上一节的人＝伪造）。
                    ended.setEndOperUserId(currentEndOperator());
                    eventPublisher.publishEvent(ended);
                }
            }

            @Override
            public boolean isFailOnException() {
                // 返回 true：监听器里的异常必须抛出去。返回 false 会被 Flowable 静默吞掉，
                // 镜像办结与业务收尾"看起来跑了、其实没跑"；抛出后办结失败即回滚流程结束动作，
                // 界面可见、可重试（唯一例外是上面 getVariables 的不可恢复分支，已提前 return）。
                return true;
            }
        }, FlowableEngineEventType.PROCESS_COMPLETED);
        // 镜像投影化
        // 无条件注册：是否真正投影由监听器逐事件判定（projectionEnabled 开关 或 wf.process.mode=QUEUE）。
        // 不按开关注册的原因：「流程处理模式」是运行时参数，运营在系统设置里勾选后必须免重启生效；
        // 若在启动期决定是否注册，勾选后要重启才生效，等于配置界面向业务承诺了引擎没做的事。
        // 默认态（开关关 + 模式 SYNC）的代价仅是每个相关事件一次内存布尔判定（且事件类型过滤在前，
        // 无关事件零代价），不做任何解析、不读库、不写行——满足硬约束：默认行为零变化。
        runtimeService.addEventListener(mirrorProjectionListener,
                FlowableEngineEventType.PROCESS_STARTED,
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_ASSIGNED,
                FlowableEngineEventType.TASK_COMPLETED,
                // Flowable 8 无 TASK_DELETED：任务删除走通用 ENTITY_DELETED（监听器内按实体类型过滤）
                FlowableEngineEventType.ENTITY_DELETED,
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.TIMER_FIRED);
        log.info("镜像投影监听器已注册（逐事件判定）：投影开启条件 = fssc.wf.mirror.projection-enabled=true"
                + " 或 sys_config.wf.process.mode=QUEUE。两者皆默认关闭时，门面照旧手工写 wf_instance/wf_task，"
                + "wf_task_history 双写标记 {}。", HISTORY_DUAL_WRITE_MARK);
        // 流程处理详细日志
        // 无条件注册：是否真正写入数据库由 sys_config.wf.log.detailed.enabled 逐事件判定（运行时勾选即生效，
        // 不需重启）。若像镜像投影那样在启动期决定是否注册，运营勾选后必须重启才生效——
        // 那样配置界面就向业务承诺了引擎没做的事。
        // 与投影监听器同一规则挂全局事件：门面插桩会漏记两条引擎旁路（SKIP / AUTO_PASS 直连
        // taskService），而卡住的单子经常死在旁路上。失败不冒泡（观测增强不得升级为审批失败）。
        runtimeService.addEventListener(processLogListener,
                FlowableEngineEventType.PROCESS_STARTED,
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_ASSIGNED,
                FlowableEngineEventType.TASK_COMPLETED,
                FlowableEngineEventType.ENTITY_DELETED,
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.TIMER_FIRED);
        deployDefault();
        deployBizFlows();
        deploySubprocesses();
    }

    /**
     * 门面是否仍手工写镜像。
     *
     * <p>返回 {@code !projectionEnabled && !queueModeEnabled()}：两个开关都在默认态
     * （投影关、模式 SYNC）时返回 true，门面照旧手工写；任一打开则跳过 {@code wf_instance} /
     * {@code wf_task} 的手工写，交给投影（内联或经发件箱异步）。
     * 旧写路径<b>保留不删</b>，靠开关切换 —— 回滚时只需要改配置。
     */
    private boolean mirrorByFacade() {
        // QUEUE 模式下投影由发件箱消费者执行，门面同样必须停写——
        // 否则发件箱与门面会同时投影同一事件，两个写入者争抢同一行。
        return !projectionEnabled && !wfProcessOutboxService.queueModeEnabled();
    }

    /**
     * 给过渡期双写的 {@code wf_task_history} 行附加来源标记。
     *
     * <p>开关打开时门面与监听器都会写同一节点的历史行，两行 action 相同、内容相近，
     * 分不出来源就没法做双写比对。不执行任何 DDL，标记只能落在 comment 上。
     */
    private static String markDualWrite(String comment) {
        return (comment == null || comment.isBlank())
                ? HISTORY_DUAL_WRITE_MARK : comment + HISTORY_DUAL_WRITE_MARK;
    }

    /** 在指定租户上下文中执行；tenantId 为 null 时按原线程上下文执行（不新建上下文）。 */
    private void runInTenant(Long tenantId, Runnable action) {
        if (tenantId == null) {
            action.run();
            return;
        }
        TenantContext.callAs(tenantId, action);
    }

    /**
     * 部署业务审批流模板（wf_business_flow → BPMN，key=bizType）。
     *
     * <p>三条规则都是有来由的：</p>
     * <ul>
     *   <li>同 key 只部署一次：{@code wf_business_flow.uk_biz(tenant_id,biz_type)} 并非唯一索引
     *       （NON_UNIQUE=1），同一 biz_type 可以有第二行；重复行只记 WARN 跳过，不再二次 deploy。</li>
     *   <li>开 {@code enableDuplicateFiltering()}：BPMN 内容与上一版一致时 Flowable 复用既有部署、
     *       不产生新版本，否则每次启动都涨一个版本号（同一 key 八天涨过 31 版，历史 key 已到 235 版）；
     *       node_json 一改即出新版本，「以 node_json 为唯一事实源」的语义不变。</li>
     *   <li>try/catch 下沉到单枚：整批共用一个 catch 时，一枚冲突就把后面的流全部吞掉 ——
     *       起服日志里只剩 0 条部署成功记录，其余业务审批流静默未部署。</li>
     * </ul>
     */
    public void deployBizFlows() {
        try {
            // 应用启动阶段无租户上下文；整个「读取模板 + build() 生成 BPMN + 部署」须包在 runIgnoreTenant 内。
            // 否则 build() 内按 node_key 读取 wf_node_config.multi_type（多实例会签/票签）时，会因无租户上下文
            // 触发 FsscTenantLineHandler.getTenantId() fail-closed 抛异常被吞，导致多实例节点无法生成
            // （现象：BPMN 退化为单办理人 userTask，流程无会签）。
            TenantContext.runIgnoreTenant(() -> {
                List<WfBusinessFlow> flows =
                        wfBusinessFlowMapper.selectList(null);
                Set<String> deployedKeys = new HashSet<>();
                for (WfBusinessFlow f : flows) {
                    if (f.getNodeJson() == null || f.getNodeJson().isBlank()) continue;
                    // 停用流程（status=DISABLED）不部署 BPMN——保留定义、不产生流程定义版本；
                    // 「启用」操作 = UPDATE status='NORMAL' 后重启（或调用本方法）完成 BPMN 部署。
                    // 历史行为兼容：既有行均为 NORMAL，不受影响。
                    if ("DISABLED".equalsIgnoreCase(f.getStatus())) {
                        log.info("业务审批流 {} ({}) 已停用(DISABLED)，跳过 BPMN 部署", f.getFlowCode(), f.getBizType());
                        continue;
                    }
                    String key = f.getBizType();
                    if (!deployedKeys.add(key)) {
                        log.warn("业务审批流 {} 与已部署的行同 biz_type={}，本行跳过（同 key 一轮启动只部署一次）",
                                f.getFlowCode(), key);
                        continue;
                    }
                    // 以 wf_business_flow.node_json 为唯一事实源生成 BPMN；内容变化即出新版本，
                    // 内容未变则复用既有部署（enableDuplicateFiltering），不再每启动递增版本。
                    try {
                        String flowName = f.getFlowName() == null ? key : f.getFlowName();
                        String xml = bpmnTemplateService.build(flowName, key, f.getNodeJson());
                        Deployment dep = repositoryService.createDeployment()
                                .addString(key + ".bpmn20.xml", xml)
                                .name(flowName)
                                .key(key)
                                .enableDuplicateFiltering()
                                .deploy();
                        log.info("业务审批流部署完成: key={}, name={}, deploymentId={}", key, flowName, dep.getId());
                    } catch (Exception e) {
                        log.error("业务审批流 {} (key={}) 部署失败，跳过该枚，其余流程继续部署", f.getFlowCode(), key, e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("业务审批流部署失败", e);
        }
    }

    /** 部署公共子流程（wf_model source=SUBPROCESS，供主流程 callActivity 引用；已部署则跳过；UC12） */
    public void deploySubprocesses() {
        try {
            List<WfModel>[] holder = new List[1];
            TenantContext.runIgnoreTenant(() ->
                    holder[0] = wfModelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                            .eq(WfModel::getSource, "SUBPROCESS")));
            List<WfModel> subs = holder[0];
            if (subs == null) return;
            for (WfModel s : subs) {
                if (s.getBpmnXml() == null || s.getBpmnXml().isBlank()) continue;
                long count = repositoryService.createProcessDefinitionQuery().processDefinitionKey(s.getModelKey()).count();
                if (count > 0) continue;
                Deployment dep = repositoryService.createDeployment()
                        .addString(s.getModelKey() + ".bpmn20.xml", s.getBpmnXml())
                        .name(s.getModelName() == null ? s.getModelKey() : s.getModelName())
                        .deploy();
                log.info("已部署公共子流程: key={}, name={}, deploymentId={}", s.getModelKey(), s.getModelName(), dep.getId());
            }
        } catch (Exception e) {
            log.error("公共子流程部署失败", e);
        }
    }

    /** 部署内置流程（已部署则跳过） */
    public void deployDefault() {
        long count = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROC_EXPENSE_CLAIM).count();
        if (count > 0) {
            return;
        }
        Deployment dep = repositoryService.createDeployment()
                .addClasspathResource("processes/expense-claim.bpmn20.xml")
                .name("费用报销审批")
                .deploy();
        log.info("已部署流程定义: {}, deploymentId={}", PROC_EXPENSE_CLAIM, dep.getId());
    }

    /**
     * 发起流程。
     *
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     * @param approver 审批人用户ID（当前节点 assignee）
     * @param title 流程标题
     * @return 流程实例ID
     */
    public String start(String bizType, Long bizId, Long approver, String title) {
        return start(bizType, bizId, approver, title, null, null);
    }

    public String start(String bizType, Long bizId, Long approver, String title, BigDecimal amount) {
        return start(bizType, bizId, approver, title, amount, null);
    }

    public String start(String bizType, Long bizId, Long approver, String title, BigDecimal amount, String subType) {
        return start(bizType, bizId, approver, title, amount, subType, null);
    }

    /** 带附加流程变量的启动（如跨法人调拨注入 targetCompanyId），供审批完成事件回调使用 */
    public String start(String bizType, Long bizId, Long approver, String title, BigDecimal amount,
                        String subType, Map<String, Object> extraVars) {
        return start(bizType, bizId, approver, title, amount, subType, extraVars, null);
    }

    /**
     * 服务端"不可再发起"的流程配置状态集。
     *
     * <p>{@code DISABLED} 才是本系统真正写出来的停用语义——界面启停走 {@code WfModelService#enable}。
     * 发起校验如果只比对 {@code OFFLINE} 就形同虚设：{@code wf_model.status} 现网取值只有
     * ACTIVE/DEPLOYED，两个值都不在拦截集里，于是"界面上停了、单据照样提"。
     * {@code OFFLINE} 一并保留，用于兼容历史/外部写入的值。
     */
    private static final Set<String> START_BLOCKED_MODEL_STATUS =
            Set.of("OFFLINE", "DISABLED");

    /**
     * 发起侧的"模型已下线/已停用"拦截。
     *
     * <p>在此之前 {@code wf_model.status} 只有界面读（列表标签 / 按状态筛选），发起走的是
     * {@code startProcessInstanceByKey}——引擎只认 ACT_RE 里有没有可用定义，不认这张配置表，
     * 所以把模型置成停用属于"看着下线了、照样能提单"的假开关。fail-open：查不到、
     * 为 NULL、抛异常一律放行，只拦 {@link #START_BLOCKED_MODEL_STATUS} 里的显式取值。
     *
     * <p>只管新发起：已在途的实例不受启停位影响——「启用流程／禁用流程」管的是后续单据，
     * 在途实例仍可干预办理。
     */
    private void rejectIfModelOffline(String processKey) {
        if (processKey == null || processKey.isBlank()) {
            return;
        }
        try {
            WfModel m = wfModelMapper.selectOne(
                    new LambdaQueryWrapper<WfModel>()
                            .eq(WfModel::getModelKey, processKey)
                            .last("LIMIT 1"));
            if (m != null && m.getStatus() != null
                    && START_BLOCKED_MODEL_STATUS.contains(m.getStatus().trim().toUpperCase())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "流程【" + processKey
                        + "】已停用（配置状态 " + m.getStatus() + "），不能再发起新单据；如需恢复请联系流程管理员");
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception ex) {
            log.warn("模型下线状态读取失败，按放行处理 modelKey={}: {}", processKey, ex.getMessage());
        }
    }

    /**
     * 提单时指定首审环节办理人（方案）。
     *
     * @param pickedFirstAssignee 提单人点名的首审办理人；仅当首审节点配成 PICK 档且灰度放行时接受，
     *                            否则抛业务异常——静默丢弃等于让用户以为"我选的人收到了"
     */
    public String start(String bizType, Long bizId, Long approver, String title, BigDecimal amount,
                        String subType, Map<String, Object> extraVars, Long pickedFirstAssignee) {
        // 优先使用配置的业务审批流（按 业务类型+子类型 解析流程 key），未部署则回退内置报销流程
        String processKey = resolveStartProcessKey(bizType, bizId, subType, amount);
        rejectIfModelOffline(processKey);
        Map<String, Object> vars = new HashMap<>();
        vars.put("bizType", bizType);
        vars.put("bizId", bizId);
        // approver 为空时不得产生 "null" 字符串——动态指派节点（assignee=${approver}）
        // 会派给不存在的 "null" 用户，导致无人可审。统一回退到发起人（与 wf_instance.start_user_id 一致）。
        // 调用方传入的 approverUserId 也不能直入 ${approver}：零角色/停用用户被指派为办理人后，
        // 其 JWT 无 per 声明且待办不可见，节点永久卡死。这里统一校验用户存在、NORMAL、未删除、
        // 且至少持有一个角色（零角色用户连候选组通道都进不去），否则回退发起人。
        vars.put("approver", String.valueOf(sanitizeApprover(approver)));
        vars.put("title", title);
        // R1 金额分档健壮性：金额分档流程（node_json 含 金额>/原值>）若调用方未传 amount，将按 0 执行，
        // 金额分档网关走默认流「金额不足→跳过连续分档→直达下个必办/直接结束」，可能静默绕过高级审批。
        // 这里显式告警暴露风险，便于核查调用方是否漏传单据真实申请金额；行为保持（空金额=0），不破坏现有调用。
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        if (amount == null && isAmountTieredFlow(processKey)) {
            log.warn("金额分档流程[{}]发起时未传入 amount(bizId={})，将按金额=0 执行并可能跳过分档审批节点，"
                    + "请调用方传入单据真实申请金额，避免静默绕过高级审批", processKey, bizId);
        }
        vars.put("amount", amt);
        // LOAN 借款流程在排他网关 LOAN_G1 依据 orgType 分支（BASE=基地链 / 其他=板块链）。
        // 导入的 LOAN BPMN 依赖该变量，若缺失引擎会抛 "condition returns non-Boolean"。
        // 生产应从提交人所属公司的组织类型驱动；此处按公司层级推断：有上级公司=基地，否则=板块。
        vars.put("orgType", isBaseCompany() ? "BASE" : "GROUP");
        // CLAIM 报销流程在排他网关 Gw_ENT 依据 expenseType 分支（ENTERTAIN=招待费特批链 / 其他=普通链）。
        // 特批链由 CLAIM 模型内网关承载，不再有 CLAIM_G1 独立模型与硬编码改写；
        // 变量缺失会抛 "Unknown property"，故仍按单据费用明细是否为招待费(07)判定并注入。
        String expenseType = resolveExpenseType(processKey, bizId);
        if (expenseType != null) {
            vars.put("expenseType", expenseType);
        }
        // 导入的 TRAVEL/TRAIN/PREPAY 模型在排他网关依据以下分支变量判断，缺失会抛
        // "Cannot resolve identifier"；统一注入默认值（布尔分支默认 false，走默认序流）：
        //  TRAVEL/TRAIN_G1: hasLoan；G3/G5: overStandard/abroad(outOfPlan)；PREPAY_G1: billType。
        // overStandard 必须按明细行实算，不能硬编码 FALSE —— 那样报销单的差旅费用项目就从不参与超标判定。
        // 算的时候 over_std 显式标记优先，否则拿行金额与费用项目同名标准族的最高国内 HARD 标准比对。
        vars.put("overStandard", resolveOverStandard(processKey, bizId));
        vars.put("abroad", resolveAbroad(processKey, bizId));
        // 以下 4 个分支变量必须经解析器真实计算：写死 Boolean.FALSE 的话，以它们为条件的排他网关
        // 永远走默认序流，配置看着能配、实际不生效。解析失败或域未建模时 fail-closed 返回 FALSE 并告警；
        // 发布期/试算/预演三类治理点会显式暴露未取值变量（见 WfBpmnChecker 等）。
        vars.put("hasLoan", resolveHasLoan(processKey, bizId));
        vars.put("outOfPlan", resolveOutOfPlan(processKey, bizId));
        vars.put("overLimit", resolveOverLimit(processKey, bizId));
        vars.put("hasContract", resolveHasContract(processKey, bizId));
        // billType 的取值域必须与 BPMN 条件的字面量同源。现网 {@code ${billType == '…'}}
        // 条件共 11 条，除 SETTLE 的 {@code PREPAY} 一条用的是子类型，其余全部写在 bizType 域
        // （＝wf_process_config.biz_type 登记值、＝wf_instance.biz_type 落库值）。
        // 所以 billType 一律取 bizType；单据子类型另用下面的 subType 变量承载——
        // 设计器分支面板早就把 subType 列为可用条件变量，门面必须真的注入，
        // 否则用到它的条件会报 "Cannot resolve identifier"。
        // 若拿 exp_bill.bill_sub_type（REPAY/LOAN/TRAVEL/SETTLE/PREPAY）去覆盖 billType，
        // 两域不重合，条件会恒为 false：还款单（bizType=EXP_REPAY、subType=REPAY）在 GW_LN 的
        // 「还款反向档」永不命中，「出纳确认还款到账」「还款核销审核」两节整体被跳过，
        // 单据就绕过出纳与财务审核直接定了论。
        // 注：归一之后还款反向档（RP_1/RP_2）是可达的，排查该链路时不要当成死配置跳过。
        // 仍待改的一处：SETTLE 模型 GW_ST 的「预付核销档」写着 {@code ${billType == 'PREPAY'}}，
        // 需改为 {@code ${subType == 'PREPAY'}}，否则预付单（bizType=EXP_PAY + bill_sub_type=PREPAY）
        // 会掉进默认流、漏掉「预付核销核对／预付复核」两节。这一步要改模型数据并重新部署，
        // 不在引擎面，已以注释态 SQL 移交发布窗口。
        vars.put("billType", bizType == null || bizType.isBlank() ? "" : bizType);
        vars.put("subType", subType == null || subType.isBlank() ? "" : subType);
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        // 镜像列 wf_instance.amount 的取值范围＝入参优先，其次认 extraVars 里显式给的
        // amount 变量。上面 {@code vars.put("amount", amt)} 先落的是"缺参补 0"的执行值（网关判定用），
        // 而 extraVars 在它之后 putAll，可把该键覆盖成单据真实金额——只认 {@code amount} 形参就会把
        // 这类调用（跨法人调拨、批量起流等走 extraVars 传金额的）继续漏成 NULL。
        // 只在入参为空时兜这一步，且取不到数值就保持 NULL（不写 0，避免与"金额=0"档混淆）。
        BigDecimal mirrorAmount = amount;
        if (mirrorAmount == null && extraVars != null && extraVars.get("amount") != null) {
            mirrorAmount = toAmountOrNull(extraVars.get("amount"));
        }
        // 多实例（会签 ALL / 顺序会签 SEQ / 票签 VOTE）：从 wf_node_config 取本流程所有
        // multiType∈{ALL,SEQ,VOTE} 的节点，把在办候选用户 ID 集合注入 <nodeKey>_assignees，
        // 供 BPMN multiInstanceLoopCharacteristics 的 flowable:collection 查询；
        // VOTE 节点额外注入 <nodeKey>_approvedCount / <nodeKey>_rejectedCount 计数初值 0（由 complete() 累加驱动提前办结）。
        // 未配置 multiType 的流程不会命中（selectList 为空），对这些流程零影响。
        injectMultiInstanceVars(processKey, vars, bizType, bizId, approver);
        // 「重新提交跳回本节点」跨实例接力
        // 单据被驳回后走 REJECTED → DRAFT → 重新 submit，会起一枚【新】流程实例；新实例默认不继承
        // 旧实例的流程变量，于是驳回时写入旧实例的 rejectOriginNode 只写不读。这里把同一
        // 业务单据（bizType+bizId）最近一个已驳回历史实例上的关联点取回，并入本次启动变量，供流程定义
        // 条件表达式/网关或后续实例修改消费。没有消费方时这些键对引擎完全惰性。
        // 本流程没配 resubmit_jump_back=1 节点时直接返回（不查 wf_instance、不注入任何键）；
        // 全程 try/catch，任何异常只降级为"不注入"，不影响提交/重新提交主链路。
        RejectAnchor jumpAnchor = injectResubmitJumpBackAnchor(processKey, bizType, bizId, vars);
        // 齐套校验·提单层：SUBMIT 档只告警不阻断，真正的缺件拦截在 NODE/CONFIRM 档办结时做。
        // 与下面的点名校验同理，必须排在 startProcessInstance 之前——本方法无事务包裹。
        billCompletenessGuard.assertComplete(processKey, BillCompletenessGuard.STAGE_SUBMIT, bizType, bizId);
        // 提单点名：校验必须在 startProcessInstance 之前——本门面方法无事务包裹，先启动再报错会留下
        // "单据已提交、流程已启动，但用户看到失败"的双态。
        enforcePickOnStart(bizType, bizId, processKey, vars, pickedFirstAssignee);
        // biz_no 必须同时进流程变量，不能只在门面内现算：镜像投影监听器是从变量取单号的，
        // 少了这个键，wf_task / wf_instance 的单号列会整列为空。多这一个键不改变既有分支——
        // 下面各处仍用同一次 resolveBizNo 的结果。
        String bizNo = resolveBizNo(bizType, bizId);
        vars.put("bizNo", bizNo);
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(processKey, vars);
        // 双写业务视图 wf_instance（查询/记录专用，流程门面统一保证）
        try {
            WfInstance inst = new WfInstance();
            inst.setTenantId(TenantContext.getTenantId());
            inst.setProcInstId(pi.getId());
            inst.setProcDefId(pi.getProcessDefinitionId());
            inst.setModelKey(processKey);
            inst.setBizType(bizType);
            inst.setBizId(bizId);
            // 发起即回写 biz_no：流程实例自己带业务主键，待办联查、单据追踪、报表反查
            // 就不必在查询侧再回填一次。
            inst.setBizNo(bizNo);
            inst.setTitle(title);
            // 调用方传入的单据真实金额必须落镜像，不能只写引擎变量：wf_instance.amount 为 NULL 时，
            // 待办视图补账（recordTaskMirror 以 inst.getAmount() 为源）跟着空 ⇒ 审批工作台「申请金额」显「-」，
            // 按金额取数的 DOA/分级/超时策略在镜像侧全部失效。amount 为空就保持 NULL，
            // 不写 0——0 是「金额为零」这个档位，两者不能混。TR_PAY 起流传的 op_pay_batch.total_amount
            // 与经 extraVars 传入的金额都在 mirrorAmount 里汇合，口径见该字段的注释。
            inst.setAmount(mirrorAmount);
            inst.setStartUserId(approverUserIdOrDefault());
            inst.setStartTime(LocalDateTime.now());
            inst.setStatus("RUNNING");
            // 当前节点：首个人工任务。并联会审网关会在启动瞬间扇出多条任务，逐条解析取人
            List<Task> firstTasks = openTasks(pi.getId());
            if (!firstTasks.isEmpty()) {
                inst.setCurrentNode(joinNodeNames(firstTasks));
                inst.setCurrentAssignee(String.valueOf(approver));
            }
            // 镜像开关打开时门面不再写 wf_instance，改由 WfMirrorProjectionListener 的 PROCESS_STARTED
            // 投影建行（它在 startProcessInstanceByKey 内同步执行，故下面依赖 wf_instance 的
            // nodeOrgResolver.applyResolution 仍能读到该行）。门面这条写路径保留，作为回滚开关。
            // 这一行必须排在 applyNodeConfig 之前。首节点的 POOL 智能推送在 applyNodeConfig 内同步发生，
            // 它经 listOperableUserIds → listRoleCandidates 反查 wf_instance 取单据公司；实例行还没落库时
            // companyIdOfBill 恒为 null，会连带两处走样：
            // ① COMPANY 档角色（FUND_STAFF / FA_ASSET_ADMIN）候选人取空，日志落「无可用候选人…保持抢单池」，
            //    这批首节全部退化成人工领取；
            // ② GROUP 档丢掉「本公司优先」重排（companyFirst 在 companyId == null 时原样返回），
            //    推送结果会落到别家公司的人头上。
            // 写入内容与原来完全一致——inst 各列在上面已填完，applyNodeConfig 也不改 inst——只是提前可见。
            if (mirrorByFacade()) {
                wfQueryService.recordInstance(inst);
            }
            if (!firstTasks.isEmpty()) {
                // 节点配置覆盖：按 model_key+node_key 重设首任务审批人
                for (Task t : firstTasks) {
                    applyNodeConfig(processKey, t);
                }
            }
            // 审批、转办、撤回都有记录，唯独「提单」这一段从不落 wf_task_history，
            // 于是从提单到归档的时间线天生缺第一段，联查只能从第一个审批节点开始。发起动作必须自己记账。
            writeHistory(pi.getId(), null, "SUBMIT", "提单", "SUBMIT", inst.getStartUserId(), title);
            // 多公司路由：首任务办理人解析（须在 recordInstance 之后，解析器依赖 wf_instance）
            boolean firstNodeSkipped = false;
            for (Task t : firstTasks) {
                // 必须重查任务的活体状态，不能用 firstTasks 里的旧快照：applyNodeConfig 刚按节点配置
                // 重设过首任务办理人，而 firstTasks 是重设<b>之前</b>查的，getAssignee() 仍为 null。
                // 拿它交给 applyResolution，「已有 assignee 就跳过」这道守卫就失效了，配置指定的人会被
                // 候选组的首个持有人覆盖掉——表面症状就是「指定审批人配了不生效」。
                Task first = t.getId() == null ? t
                        : taskService.createTaskQuery().taskId(t.getId()).singleResult();
                if (first == null) {
                    continue;
                }
                nodeOrgResolver.applyResolution(first);
                // 空策略兜底：首节点若在本单据公司内无候选办理人，按 wf_node_config.empty_strategy
                // 转管理员/跳过/报错，避免发起即静默滞留。首节点通常为 INITIATOR（已带 assignee），此处多为空操作。
                String empty = nodeOrgResolver.applyEmptyStrategy(processKey, first);
                recordEmptyStrategy(first, empty);
                firstNodeSkipped = firstNodeSkipped || "SKIP".equals(empty);
            }
            // 待办视图双写 wf_task（PENDING，处理人=解析后的真实办理人）
            if (!firstTasks.isEmpty() && !firstNodeSkipped) {
                List<String> openAssignees = new ArrayList<>();
                // 自确认节点自动跳过
                // 首节点（提交确认类）审批人=提交人时，流程启动即自动通过首节点（提交即通过）：
                // 不通知、不产生人工待办（wf_task 随 complete 置 DONE）；仅跳过首节点，后续节点正常流转。
                // 复用 complete() 审批通道且与 start 同事务执行，避免 ACT_RU_TASK 与 wf_* 视图状态不一致。
                // 注意：须在 recordTask 之后执行，complete→markTaskDone 才能把本条待办置 DONE。
                for (Task t : firstTasks) {
                    // 镜像一律按重查到的实时任务写：①②级解析改的是引擎侧 ASSIGNEE_，内存里的任务对象
                    // 还是解析前的快照。沿用 approver 写镜像，两本账从第一个节点起就会叉开，而催办、看板、
                    // 共享中心待办全部按镜像找人——单子在真人手里却催不到。
                    Task live = taskService.createTaskQuery().taskId(t.getId()).singleResult();
                    if (live == null) {
                        continue;   // 已被空策略处置掉，给消失的 taskId 写 PENDING＝永远办不掉的幽灵待办
                    }
                    // 镜像开关打开时停写 wf_task，改由监听器 TASK_CREATED 投影建行
                    if (mirrorByFacade()) {
                        recordTaskMirror(processKey, live);
                    }
                    Long liveAssignee = toUserIdOrNull(live.getAssignee());
                    if (liveAssignee != null) {
                        openAssignees.add(String.valueOf(liveAssignee));
                    }
                    boolean autoSkipped = autoSkipSelfConfirmFirstNode(processKey, live);
                    // 消息多渠道：通知解析后的真实办理人（站内信 + 企微/邮件按配置）；该支已自动跳过则不产生待办通知
                    // 候选组/POOL 态不回落 approver（＝提单人/首选审批人，
                    // 甚至被 deny_self 禁办的人）——按 todoNotifyTargets 发全体候选人，封顶 20。
                    if (!autoSkipped) {
                        for (Long target : todoNotifyTargets(live)) {
                            try {
                                notifyService.notifyUser(target, "APPROVAL", "审批待办提醒",
                                        "您有一条新的审批任务：【" + title + "】（流程：" + live.getName() + "）",
                                        bizType, bizId, "/approval");
                            } catch (Exception ex2) {
                                log.warn("待办多渠道通知失败 user={} task={}", target, live.getId(), ex2);
                            }
                        }
                    }
                }
                // 双账对齐回写：镜像写的是解析后的真人，实例视图"当前办理人"必须跟上一致；
                // 并联扇出时＝各支在办人合集。
                // 无人认领（候选组/POOL）时不能沿用 recordInstance 写入的提单人：提单人那一环节早已办结，
                // 列表/看板/大屏的"当前在办人"会把单据挂在跟本环节无关的人身上。
                // 规则与 {@code syncInstanceAfterAdvance} 一致：按重查到的活体任务算，算不出人就是空串。
                if (mirrorByFacade()) {
                    List<Task> liveOpen = openTasks(pi.getId());
                    if (liveOpen.isEmpty() && !openAssignees.isEmpty()) {
                        // 首任务全被自动跳过或空策略吃掉：沿用原规则（实例视图此刻已由推进路径同步过）
                        WfInstance iupd = new WfInstance();
                        iupd.setCurrentAssignee(String.join(",", openAssignees));
                        wfQueryService.updateInstance(iupd, pi.getId());
                    } else if (!liveOpen.isEmpty()) {
                        WfInstance iupd = new WfInstance();
                        iupd.setCurrentAssignee(liveOpen.stream()
                                .map(t -> t.getAssignee() == null ? "" : t.getAssignee())
                                .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "," + b).orElse(""));
                        wfQueryService.updateInstance(iupd, pi.getId());
                    }
                }
            } else if (!firstTasks.isEmpty()) {
                // 空策略 SKIP 已把首任务完成：给消失的 taskId 写 PENDING＝永远办不掉的幽灵待办，
                // 改由三级解析统一接管推进后的节点（内部含镜像补账）。
                assignApprovers(processKey, pi.getId());
                if (mirrorByFacade()) {
                    List<Task> after = openTasks(pi.getId());
                    WfInstance iupd = new WfInstance();
                    iupd.setCurrentNode(joinNodeNames(after));
                    iupd.setCurrentAssignee(after.stream()
                            .map(t -> t.getAssignee() == null ? "" : t.getAssignee())
                            .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "," + b).orElse(""));
                    wfQueryService.updateInstance(iupd, pi.getId());
                }
            }
            // 重提跳回必须排在上面首任务镜像/通知<b>之后</b>：搬运执行会把这条刚建好的首任务连同镜像
            // 一起收尾，先搬后写等于给已消失的 taskId 写一行 PENDING 幽灵待办。
            // 总开关缺省关（wf.resubmit.jumpback.enabled 未置 1）时这里直接 return，不发生任何搬运。
            consumeRejectAnchor(processKey, pi.getId(), amount, jumpAnchor);
        } catch (Exception ex) {
            // 整个 try 块写的都是引擎之外的第二本账（wf_instance / wf_task / wf_task_history）。
            // 这部分失败只打一行 log 就返回 pi.getId() 的话，调用方以为提单成功，而待办/实例视图
            // 可能压根没写进去。所以先记缺陷再原样冒泡：引擎侧流程实例已启动、不回滚是既定行为，
            // 但提单界面必须看见失败，并拿到错误码与可对账的 procInstId。
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED, "start",
                    null, "bizType=" + bizType + " bizId=" + bizId + " procInstId=" + pi.getId(), ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "流程已启动，但流程/待办视图保存失败[" + WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED
                            + "]，请刷新列表；管理员可查系统日志与双账对账报表：procInstId=" + pi.getId());
        }
        return pi.getId();
    }

    /**
     * 自确认节点自动跳过：流程启动后检查首任务办理人（ACT_RU_TASK.ASSIGNEE_）
     * 或候选（候选人 / 候选组=发起人角色）是否包含发起人；命中则复用
     * {@link #complete(String, boolean, String, Long)} 审批通道自动通过首节点（提交即通过），
     * 不产生待办与通知。仅处理首节点，后续节点正常流转。
     * <ul>
     *  <li>发起人取当前登录用户（与 wf_instance.start_user_id 一致）；无用户上下文（系统调用）不跳过；</li>
     *  <li>指派覆盖（applyNodeConfig / nodeOrgResolver）可能已改派首任务，判定前重查任务最新办理人；</li>
     *  <li>复用 complete() 通道：待办视图置 DONE、wf_task_history 记录、wf_instance 推进到下一节点，与人工审批一致；
     *      自审禁止（approve.deny_self）对"无历史任务的首节点"豁免，不会被误拦；</li>
     *  <li>与本方法同一事务（调用方事务）内完成，避免引擎与视图状态不一致；</li>
     *  <li>自动跳过失败（如权限校验异常）仅记录 error，首任务保留待办走人工审批，不阻断单据提交。</li>
     *  <li><b>只看显式属性</b>："提单即完成"由 {@code wf_node_config.first_node_auto_pass}
     *      承载，节点名不参与判定——名称是给人看的，改个名就换行为不算配置
     *      （判断见 {@link #isFirstNodeAutoPass}）。</li>
     * </ul>
     *
     * @return true=首节点已自动通过
     */
    private boolean autoSkipSelfConfirmFirstNode(String processKey, Task firstTask) {
        try {
            // POOL 作业池档一律不自动跳过：任务池的语义是"池内谁做、记录到人"，
            // 而本方法下面的两处判断都会把发起人自己算成命中——
            //   ① 节点名含"提交"即无条件自动完成；② firstNodeAssignedTo 除 assignee==发起人外，
            //      还认「候选组里含发起人自己的角色」。
            // ⇒ 提单人若恰好持有首节点作业岗的角色（例如 FA_ADD.Task_1 建档池（FA_ASSET_ADMIN）、
            //    GL_SALARY_PAY.Task_1 工资发放数据计算（FIN_GL），均为 sort=1 的 POOL 链首），
            //    作业节点<b>不产生待办、原地自动通过</b>，
            //    记录写"自确认节点自动跳过"——作业量从台账上消失，共享中心的抢单/派单规则也失真。
            // 池内节点必须真实产生一次"领取→办理"，扣子就留在池档这一侧。
            if (nodeOrgResolver.isPoolTask(firstTask)) {
                log.info(" 流程 {} 首节点[{}]为 POOL 任务池档，不自确认跳过（作业须留痕到人）: procInstId={}, taskId={}",
                        processKey, firstTask.getName(), firstTask.getProcessInstanceId(), firstTask.getId());
                return false;
            }
            // 首节点"提单即自动通过"只认节点级显式属性，名称不参与判定：
            // 语义挂在 wf_node_config.first_node_auto_pass 上，这类跳过一律做成节点级勾选、且默认不勾选。
            // 靠名称命中等于改个节点名就改了控制流，既不可测试也不可审计。
            // "参与人为空时跳过"另有节点级列 empty_strategy=SKIP 承载，与本判断分列、互不代偿。
            WfNodeConfig firstNodeCfg = wfNodeConfigResolver.resolve(processKey,
                    firstTask == null ? null : firstTask.getTaskDefinitionKey());
            if (isFirstNodeAutoPass(firstNodeCfg)) {
                log.info(" 流程 {} 首节点[{}]已勾选「提单即自动通过」（first_node_auto_pass=1），自动完成: procInstId={}, taskId={}",
                        processKey, firstTask.getName(), firstTask.getProcessInstanceId(), firstTask.getId());
                // 提交节点自动通过前必须把办理人归位到发起人本人。角色取人（如 LOAN 首节点
                // assign_type=ROLE/COMMON_EMP）会把首任务派给角色下 sys_user.id 最小的持有人，
                // 而不是申请人；机器办结后该持有人就在审批历史里留下"办过本实例一个节点"，
                // 轮到他自己的节点时会被连办校验 approve.deny_serial_same_handler 误拦
                // （403 禁止同一人连续办理多个节点），只能转办才走得完。
                // 「提单即自动通过」的语义是发起人的提交动作本身，办结人理应记发起人；
                // 归位后镜像 wf_task 与实例视图同步更新，两本账一致。
                Long initiator = approverUserIdOrDefault();
                Long passOper = null;
                if (initiator != null && initiator > 0) {
                    passOper = initiator;
                    if (!String.valueOf(initiator).equals(firstTask.getAssignee())) {
                        String rolePicked = firstTask.getAssignee();
                        taskService.setAssignee(firstTask.getId(), String.valueOf(initiator));
                        syncTaskViewAfterAssigneeChange(firstTask, initiator, "提交节点归位发起人");
                        log.info(" 流程 {} 提交节点[{}]办理人归位发起人({})（原角色取人={}）: procInstId={}, taskId={}",
                                processKey, firstTask.getName(), initiator, rolePicked,
                                firstTask.getProcessInstanceId(), firstTask.getId());
                    }
                }
                autoPassByTimeout(firstTask.getId(), passOper, "提交节点自动跳过（提单即触发）");
                log.info(" 流程 {} 提交节点已自动通过（配置 first_node_auto_pass=1，机器通道）: procInstId={}, taskId={}",
                        processKey, firstTask.getProcessInstanceId(), firstTask.getId());
                return true;
            }
            Long initiator = approverUserIdOrDefault();
            if (initiator == null || initiator <= 0) {
                return false;
            }
            // 指派覆盖可能已改派：重查 ACT_RU_TASK 最新 ASSIGNEE_/候选后再判定
            Task task = taskService.createTaskQuery().taskId(firstTask.getId()).singleResult();
            if (task == null || !firstNodeAssignedTo(task, initiator)) {
                return false;
            }
            log.info(" 流程 {} 首节点[{}]审批人=提交人({})，自动跳过自确认节点: procInstId={}, taskId={}",
                    processKey, task.getName(), initiator, task.getProcessInstanceId(), task.getId());
            // 复用现有审批通道完成首节点（同事务）：待办 DONE + 审批历史记录 + 实例推进到下一节点
            complete(task.getId(), true, "自确认节点自动跳过（提交人=首节点审批人）", initiator);
            log.info(" 流程 {} 首节点已自动通过（提交即通过，无人工待办）: procInstId={}, taskId={}",
                    processKey, task.getProcessInstanceId(), task.getId());
            return true;
        } catch (Exception ex) {
            log.error(" 流程 {} 首节点自动跳过失败，保留人工待办: procInstId={}, taskId={}",
                    processKey, firstTask == null ? null : firstTask.getProcessInstanceId(), firstTask == null ? null : firstTask.getId(), ex);
            return false;
        }
    }

    /**
     * 首节点是否"提单即自动通过"——全局只有这一处判断。
     *
     * <p>只认节点级显式属性 {@code wf_node_config.first_node_auto_pass = 1}，<b>未配置＝不跳过</b>。
     * 这类跳过一律做成节点级勾选、默认不勾选，节点名从来不参与判断。
     *
     * <p>取值从"未配置且名称含『提交』则跳过"收紧为"只认显式勾选"之后，那些没勾选、
     * 名称里却带『提交』的首节点会从静默跳过变成产生真实人工待办 —— 这是有意的行为变化。
     * 「参与人为空时跳过」由 {@code empty_strategy=SKIP} 独立承载，不由本方法代偿。
     */
    private boolean isFirstNodeAutoPass(WfNodeConfig cfg) {
        return cfg != null && cfg.getFirstNodeAutoPass() != null && cfg.getFirstNodeAutoPass() == 1;
    }

    /** 首任务 ASSIGNEE_ 或候选（候选人 / 候选组=发起人角色）是否包含指定用户 */
    private boolean firstNodeAssignedTo(Task task, Long userId) {
        String uid = String.valueOf(userId);
        if (uid.equals(task.getAssignee())) {
            return true;
        }
        // 候选人
        if (taskService.createTaskQuery().taskId(task.getId()).taskCandidateUser(uid).count() > 0) {
            return true;
        }
        // 候选组（发起人角色码）
        List<String> roleCodes = roleCodesOfUser(userId);
        return !roleCodes.isEmpty()
                && taskService.createTaskQuery().taskId(task.getId()).taskCandidateGroupIn(roleCodes).count() > 0;
    }

    /** 用户角色码列表（候选组判定用），查询失败返回空列表 */
    private List<String> roleCodesOfUser(Long userId) {
        List<String> roleCodes = new ArrayList<>();
        try {
            List<SysUserRole> urs = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, userId));
            List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).toList();
            if (!roleIds.isEmpty()) {
                sysRoleMapper.selectBatchIds(roleIds).forEach(r -> {
                    if (r.getRoleCode() != null) roleCodes.add(r.getRoleCode());
                });
            }
        } catch (Exception ex) {
            log.warn(" 查询发起人角色失败 userId={}: {}", userId, ex.getMessage());
        }
        return roleCodes;
    }

    /**
     * 解析流程定义 key：<b>唯一权威＝流程绑定表 {@code wf_process_config}</b>。
     *
     * <p><b>为什么不留内置 switch</b>：若先查绑定表、未命中或查询异常时回退到 jar 内置的
     * {@code switch(bizType)} 硬编码映射，同一件事就有了两个真值来源 —— 界面能改的绑定表，
     * 与改不动的代码常量。二者的差集就是"配了也不生效"或"没配也能跑"的静默分叉，
     * 而回退路径只在日志里留一行 warn，没人会去看。
     * 现在 {@code wf_process_config} 是唯一路由权威：
     * <ul>
     *   <li>绑定表命中且目标已部署 → 返回；</li>
     *   <li>绑定表未命中 → {@link WfProcessConfigService#resolveModelKey} 自己抛
     *       {@code BizException}，原样冒泡；</li>
     *   <li>绑定表查询异常 → 原样冒泡（配置面坏了必须暴露，不能静默降级到硬编码）；</li>
     *   <li>绑定表命中但目标未部署 → 直接抛 {@code BizException}（提示已绑定的 key 未部署）。</li>
     * </ul>
     * 末尾"路由断链拒绝发起"保留：它是最后一道兜底，防止任何路径静默落到
     * jar 内置的孤儿流程 expenseClaim。
     */
    private String resolveProcessKey(String bizType, String subType, BigDecimal amount) {
        if (bizType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "业务类型为空，无法路由流程，已拒绝发起。");
        }
        // 绑定表精确匹配（同 bizType 多条按 priority 取金额区间命中的）
        Long companyId = null;
        LoginUser u = UserContext.get();
        if (u != null) {
            companyId = u.getCompanyId();
        }
        // 未命中时 resolveModelKey 自抛 BizException，异常原样冒泡，绝不降级到硬编码映射
        String bound = processConfigService.resolveModelKey(bizType, subType, amount, companyId, null);
        if (bound == null || bound.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "流程路由断链：业务类型 " + bizType + (subType == null ? "" : "/" + subType)
                            + " 的绑定规则未给出流程 key，已拒绝发起。"
                            + "请先在【单据类型流程绑定】配置流程后再提交。");
        }
        // 绑定表这条路径必须自己查部署存在性：内置映射在代码里查得到，绑定表里的一行却可能指向
        // 未部署或已删除的 key，那样发起会直接抛 Flowable 异常而不是回退。
        long boundDefs = repositoryService.createProcessDefinitionQuery().processDefinitionKey(bound).count();
        if (boundDefs > 0) {
            return bound;
        }
        // 绑定表命中但目标未部署：不回退 switch —— 绑定表说什么就是什么，断链即报错
        log.error("流程绑定表命中但目标未部署，拒绝发起: bizType={}, subType={}, boundKey={}", bizType, subType, bound);
        throw new BizException(ErrorCode.PARAM_INVALID,
                "流程路由断链：业务类型 " + bizType + (subType == null ? "" : "/" + subType)
                        + " 已绑定流程 key=" + bound + " 但该流程未部署，已拒绝发起。"
                        + "请先部署对应审批流或改绑其他已部署流程后再提交。");
    }

    /**
     * 发起用的流程 key＝{@link #resolveProcessKey} 的绑定表解析，没有任何按 key 的改写。
     *
     * <p>招待费特批链由 CLAIM 模型内部的 {@code expenseType} 排他网关承载
     * （Gw_ENT：{@code ${expenseType=='ENTERTAIN'}} 走特批分支，default 走普通链），
     * 与其余流程"绑定表命中＋流程内网关分档"的规则一致。若在这里做"明细含 07 招待费 ⇒
     * 改写 CLAIM_G1"的硬编码路由，就绕过了 {@code resolveProcessKey} 的 sub_type 面，
     * 并档后还会造成网关与硬编码双命中。CLAIM_G1 模型已转入只读留档，
     * 4 支历史实例的 proc_inst_id 保持不动。
     *
     * <p>单独留一个方法的原因：提单弹窗要先知道"这单会落到哪条流程的哪个节点"才能取候选人，
     * 弹窗与实际启动必须走同一个入口。
     */
    private String resolveStartProcessKey(String bizType, Long bizId, String subType, BigDecimal amount) {
        return resolveProcessKey(bizType, subType, amount);
    }

    /**
     * 路由试算（只读，不写库、不发起实例）：把 {@link #resolveProcessKey} 的三级链路拆开返回。
     *
     * <p>用途＝wf_process_config 镜像迁移前后的逐格比对：同一入参、同一登录人，落点必须一致。
     * 与真实发起的唯一差异是 bizId 传 null，因此招待费特批链的 CLAIM→CLAIM_G1 改写不会发生；
     * 那一步发生在 resolveProcessKey 之后，不影响这里要证的「绑定表 vs 内置映射」等价性。
     */
    public Map<String, Object> previewRouting(String bizType, String subType, BigDecimal amount) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bizType", bizType);
        r.put("subType", subType);
        r.put("amount", amount);
        Long companyId = null;
        LoginUser u = UserContext.get();
        if (u != null) {
            companyId = u.getCompanyId();
        }
        r.put("companyId", companyId);
        try {
            r.put("tableKey", processConfigService.resolveModelKey(bizType, subType, amount, companyId, null));
        } catch (Exception ex) {
            // 表诊断失败原因回填进诊断结果（该结果本身即错误呈现路径），不再上抛
            r.put("tableMiss", ex.getMessage());
        }
        String finalKey;
        try {
            finalKey = resolveProcessKey(bizType, subType, amount);
        } catch (BizException ex) {
            // 后断链会抛异常；试算端点仍须把"断链"作为可读数返回，供 110 格比对使用
            r.put("finalKey", null);
            r.put("brokenLink", true);
            r.put("brokenLinkReason", ex.getMessage());
            r.put("deployedDefs", 0);
            r.put("viaTable", false);
            return r;
        }
        r.put("finalKey", finalKey);
        r.put("deployedDefs", repositoryService.createProcessDefinitionQuery().processDefinitionKey(finalKey).count());
        r.put("viaTable", finalKey.equals(r.get("tableKey")));
        return r;
    }

    /**
     * 判定流程是否真的按金额分档。
     *
     * <p>判断不能读 {@code wf_business_flow.node_json} 里的「金额>／原值>」文案：那是导入期的<b>设计说明</b>，
     * 与网关实际条件没有约束关系。BPMN 结构重造之后只改 XML 不改 node_json，告警就会双向失真
     * （XML 里新加了金额网关这里不响，静默绕过高级审批；node_json 残留旧文案则一直空响）。</p>
     *
     * <p>所以与发布校验同源：取该 key 最新已部署定义的序列流条件，命中 {@code amount} 词元即算分档
     * （判断在 {@link WfBpmnChecker#referencesAmount}，只写一遍）。缓存键用 procDefId——
     * 重发布必然升版本，不会拿着上一版的结论。</p>
     */
    private boolean isAmountTieredFlow(String processKey) {
        if (processKey == null || processKey.isBlank()) return false;
        try {
            ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processKey).latestVersion().singleResult();
            if (def == null) return false;
            Boolean cached = tieredCache.get(def.getId());
            if (cached != null) return cached;
            boolean tiered = hasAmountBranch(def.getId());
            tieredCache.put(def.getId(), tiered);
            return tiered;
        } catch (Exception ex) {
            log.debug("金额分档判定失败，按非分档处理 processKey={}", processKey, ex);
            return false;
        }
    }

    /** 该已部署定义的任意序列流（含子流程内）条件引用了 amount */
    private boolean hasAmountBranch(String procDefId) {
        Process p = mainProcess(procDefId);
        if (p == null) return false;
        for (SequenceFlow fl : p.findFlowElementsOfType(SequenceFlow.class)) {
            if (WfBpmnChecker.referencesAmount(fl.getConditionExpression())) {
                return true;
            }
        }
        return false;
    }

    /** 当前登录用户ID，无则取 0 */
    private Long approverUserIdOrDefault() {
        LoginUser u = UserContext.get();
        return u == null || u.getUserId() == null ? 0L : u.getUserId();
    }

    /**
     * 人工指派的合法性校验（转办 L1 / 改派 L2 共用）：目标必须是
     * 「存在 + NORMAL + 至少持一个角色 + 非 admin」。
     *
     * <p>与 {@link #sanitizeApprover} 同一规则，唯一区别是这里<b>抛错而非回退</b>——
     * 那两个动作都是人手动点的，静默回退等于"我明明选了张三，单子却给了别人"。
     * admin 单列一条：铁律要求任何流程节点不得由 admin 办理，人工指派通道不能成为例外。
     */
    private void assertAssignableUser(Long userId, String actionLabel) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, actionLabel + "目标用户必填");
        }
        SysUser su = sysUserMapper.selectById(userId);
        if (su == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, actionLabel + "目标用户不存在（ID " + userId + "）");
        }
        if ("admin".equals(su.getUserName()) || Integer.valueOf(1).equals(su.getIsAdmin())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    actionLabel + "目标不能是系统管理员 admin —— 任何流程节点都不允许 admin 办理");
        }
        if (!"NORMAL".equals(su.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    actionLabel + "目标 " + su.getNickName() + "（" + su.getUserName() + "）状态为 "
                            + su.getStatus() + "，指派后无人可办，单据会卡在节点上");
        }
        Long roleCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId));
        if (roleCount == null || roleCount == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    actionLabel + "目标 " + su.getNickName() + "（" + su.getUserName() + "）未持有任何角色，"
                            + "其待办不可见、节点会永久卡死；请先在用户管理为其定岗");
        }
    }

    /**
     * 缺陷D2(20260919)：动态指派人合法性校验。
     * 首节点 {@code assignee=${approver}} 的办理人必须「存在 + NORMAL + 未删除 + 至少持一角色」；
     * 任一不满足（调用方传了零角色/停用/不存在用户）时回退发起人并告警。
     * 零角色用户若被指派：其 JWT 的 per 声明为空、待办对任何真实用户不可见，节点将永久卡死。
     */
    private Long sanitizeApprover(Long approver) {
        Long fallback = approverUserIdOrDefault();
        if (approver == null) {
            return fallback;
        }
        try {
            SysUser su = sysUserMapper.selectById(approver);
            boolean usable = su != null
                    && "NORMAL".equals(su.getStatus())
                    && sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, approver)) > 0;
            if (!usable) {
                log.warn("动态指派人不合法(不存在/停用/零角色) approver={}，回退发起人={}", approver, fallback);
                return fallback;
            }
            return approver;
        } catch (Exception ex) {
            log.warn("动态指派人校验异常 approver={}：{}，回退发起人={}", approver, ex.getMessage(), fallback);
            return fallback;
        }
    }

    /**
     * 是否为基地公司（用于借款流程 orgType 分支）。
     * 约定：所属公司存在上级公司(parent_id>0)视为基地，否则视为板块/集团。
     */
    private boolean isBaseCompany() {
        try {
            LoginUser u = UserContext.get();
            if (u == null || u.getCompanyId() == null) {
                return false;
            }
            SysCompany c = sysCompanyMapper.selectById(u.getCompanyId());
            return c != null && c.getParentId() != null && c.getParentId() > 0;
        } catch (Exception ex) {
            log.warn("解析提交公司组织类型失败，按板块处理: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 解析报销单的 expenseType（CLAIM_G1 网关分支变量）。
     * 仅对报销类流程（processKey 为 CLAIM/CLAIM_TRAVEL）有意义：若费用明细含招待费（item_code 以 07 开头）则返回
     * ENTERTAIN，否则返回 NORMAL；非报销类流程返回 null（不注入）。
     */
    private String resolveExpenseType(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return null;
        if (!"CLAIM".equals(processKey) && !"CLAIM_TRAVEL".equals(processKey)
                && !"CLAIM_G1".equals(processKey) && !"CLAIM_NC".equals(processKey)) return null;
        try {
            List<ExpBillLine> lines = expBillLineMapper.selectList(
                    new LambdaQueryWrapper<ExpBillLine>()
                            .eq(ExpBillLine::getBillId, bizId));
            if (lines == null || lines.isEmpty()) return "NORMAL";
            // N+1 → 一次性收集所有 itemId 批量查询，内存匹配
            List<Long> itemIds = lines.stream()
                    .map(ExpBillLine::getItemId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (itemIds.isEmpty()) return "NORMAL";
            Map<Long, MdExpenseItem> itemMap =
                    mdExpenseItemMapper.selectBatchIds(itemIds).stream()
                            .collect(Collectors.toMap(
                                    MdExpenseItem::getId, Function.identity()));
            for (ExpBillLine line : lines) {
                if (line.getItemId() == null) continue;
                MdExpenseItem item = itemMap.get(line.getItemId());
                if (item != null && item.getItemCode() != null && item.getItemCode().startsWith("07")) {
                    return "ENTERTAIN";
                }
            }
            return "NORMAL";
        } catch (Exception ex) {
            log.warn("解析报销单 expenseType 失败 bizId={}: {}", bizId, ex.getMessage());
            return "NORMAL";
        }
    }

    /**
     * 解析报销单的超标准标记（G3 网关分支变量 overStandard）。
     * 仅对报销类流程（processKey 为 CLAIM/CLAIM_TRAVEL）有意义。判定规则（保守）：
     * ①明细行显式 over_std=1 标记优先；②无标记时，费用项目名命中标准族关键字（住宿/餐补/交通），
     * 且行金额 &gt; 该族国内（HARD、CITY_*）最高标准 → 判超标。非报销类流程返回 false。
     * 主数据说明：md_expense_standard.item_id 存在孤儿引用（240/241 无对应费用项目），故按 std_name 关键字
     * 匹配标准族（与前端差旅标准自动计算 autoCalcTravel 的 stdName 匹配规则一致），不依赖 item_id。
     */
    private boolean resolveOverStandard(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return false;
        if (!"CLAIM".equals(processKey) && !"CLAIM_TRAVEL".equals(processKey)) return false;
        try {
            List<ExpBillLine> lines = expBillLineMapper.selectList(
                    new LambdaQueryWrapper<ExpBillLine>()
                            .eq(ExpBillLine::getBillId, bizId));
            if (lines == null || lines.isEmpty()) return false;
            List<Long> itemIds = lines.stream()
                    .map(ExpBillLine::getItemId)
                    .filter(Objects::nonNull).distinct()
                    .collect(Collectors.toList());
            Map<Long, MdExpenseItem> itemMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : mdExpenseItemMapper.selectBatchIds(itemIds).stream().collect(Collectors.toMap(
                            MdExpenseItem::getId, Function.identity()));
            Map<String, BigDecimal> familyMaxStd = new HashMap<>();
            for (ExpBillLine line : lines) {
                if (line.getOverStd() != null && line.getOverStd() == 1) {
                    return true;
                }
                MdExpenseItem item = line.getItemId() == null ? null : itemMap.get(line.getItemId());
                if (item == null || item.getItemName() == null) continue;
                String family = null;
                for (String kw : new String[]{"住宿", "餐补", "交通"}) {
                    if (item.getItemName().contains(kw)) { family = kw; break; }
                }
                if (family == null) continue;
                BigDecimal maxStd = familyMaxStd.computeIfAbsent(family, k -> mdExpenseStandardMapper.selectList(
                                new LambdaQueryWrapper<MdExpenseStandard>()
                                        .eq(MdExpenseStandard::getControlType, "HARD")
                                        .like(MdExpenseStandard::getStdName, k)
                                        .likeRight(MdExpenseStandard::getCityLevel, "CITY")
                                        .and(w -> w.isNull(MdExpenseStandard::getExpireDate)
                                                .or().ge(MdExpenseStandard::getExpireDate, LocalDate.now())))
                                .stream().map(MdExpenseStandard::getAmount)
                                .filter(Objects::nonNull)
                                .max(BigDecimal::compareTo).orElse(null));
                if (maxStd != null && line.getAmount() != null && line.getAmount().compareTo(maxStd) > 0) {
                    log.info("报销单[{}]明细[{}:{}]金额{}超过标准族[{}]最高国内标准{}，判定超标",
                            bizId, item.getItemName(), line.getId(), line.getAmount(), family, maxStd);
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("解析超标准标记失败 bizId={}: {}", bizId, ex.getMessage());
            return false;
        }
    }

    /**
     * 解析 hasLoan（是否存在未结清借款）：报销/借款申请人名下 LOAN 型 exp_bill 的借款总额
     * 减去已冲抵(exp_loan_offset.offset_amount) 后仍大于 0，即判定有借款。
     * 替代 WfEngineFacade 原写死的 Boolean.FALSE，使 TRAVEL/TRAIN 等模型的 hasLoan 分支真实生效。
     * 失败 fail-closed 返回 FALSE 并告警（与 resolveOverStandard 同策略）。
     */
    private Boolean resolveHasLoan(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return Boolean.FALSE;
        try {
            ExpBill bill = expBillMapper.selectById(bizId);
            if (bill == null) return Boolean.FALSE;
            Long applicant = bill.getCreateBy();
            Long companyId = bill.getCompanyId();
            if (applicant == null || companyId == null) return Boolean.FALSE;
            List<ExpBill> loans = expBillMapper.selectList(
                    new LambdaQueryWrapper<ExpBill>()
                            .eq(ExpBill::getBillType, "LOAN")
                            .eq(ExpBill::getCreateBy, applicant)
                            .eq(ExpBill::getCompanyId, companyId)
                            .eq(ExpBill::getDelFlag, 0));
            if (loans == null || loans.isEmpty()) return Boolean.FALSE;
            BigDecimal loanTotal = loans.stream()
                    .map(ExpBill::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Long> loanIds = loans.stream()
                    .map(ExpBill::getId)
                    .collect(Collectors.toList());
            List<ExpLoanOffset> offsets = expLoanOffsetMapper.selectList(
                    new LambdaQueryWrapper<ExpLoanOffset>()
                            .in(ExpLoanOffset::getLoanBillId, loanIds));
            BigDecimal offsetTotal = offsets == null ? BigDecimal.ZERO :
                    offsets.stream().map(ExpLoanOffset::getOffsetAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean hasLoan = loanTotal.subtract(offsetTotal).compareTo(BigDecimal.ZERO) > 0;
            if (hasLoan) {
                log.info("报销单[{}]申请人[{}]存在未结清借款(借款{}−冲抵{})，hasLoan=true", bizId, applicant, loanTotal, offsetTotal);
            }
            return hasLoan;
        } catch (Exception ex) {
            log.warn("解析 hasLoan 失败 bizId={}: {}", bizId, ex.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * 解析 overLimit（是否超标准/超限）：报销/差旅明细任一金额超过其费用项目的 HARD 国内标准即判定超限。
     * 沿用 resolveOverStandard 的标准比对范式，但覆盖全部费用项目、不限于住宿/餐补/交通三族；
     * 借款/预付款等其它流程暂未建模限额比对，一律 fail-closed 返回 FALSE。
     */
    private Boolean resolveOverLimit(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return Boolean.FALSE;
        try {
            if ("CLAIM".equals(processKey) || "CLAIM_TRAVEL".equals(processKey)
                    || "TRAVEL".equals(processKey) || "TRAIN".equals(processKey)) {
                List<ExpBillLine> lines = expBillLineMapper.selectList(
                        new LambdaQueryWrapper<ExpBillLine>()
                                .eq(ExpBillLine::getBillId, bizId));
                if (lines == null || lines.isEmpty()) return Boolean.FALSE;
                List<Long> itemIds = lines.stream()
                        .map(ExpBillLine::getItemId)
                        .filter(Objects::nonNull).distinct()
                        .collect(Collectors.toList());
                Map<Long, MdExpenseItem> itemMap = itemIds.isEmpty()
                        ? Collections.emptyMap()
                        : mdExpenseItemMapper.selectBatchIds(itemIds).stream().collect(Collectors.toMap(
                                MdExpenseItem::getId, Function.identity()));
                for (ExpBillLine line : lines) {
                    if (line.getOverStd() != null && line.getOverStd() == 1) return Boolean.TRUE;
                    MdExpenseItem item = line.getItemId() == null ? null : itemMap.get(line.getItemId());
                    if (item == null || item.getItemName() == null) continue;
                    BigDecimal maxStd = mdExpenseStandardMapper.selectList(
                                    new LambdaQueryWrapper<MdExpenseStandard>()
                                            .eq(MdExpenseStandard::getControlType, "HARD")
                                            .like(MdExpenseStandard::getStdName, item.getItemName())
                                            .and(w -> w.isNull(MdExpenseStandard::getExpireDate)
                                                    .or().ge(MdExpenseStandard::getExpireDate, LocalDate.now())))
                            .stream().map(MdExpenseStandard::getAmount)
                            .filter(Objects::nonNull)
                            .max(BigDecimal::compareTo).orElse(null);
                    if (maxStd != null && line.getAmount() != null && line.getAmount().compareTo(maxStd) > 0) {
                        log.info("报销单[{}]明细[{}:{}]金额{}超过标准{}，判定超限", bizId, item.getItemName(), line.getId(), line.getAmount(), maxStd);
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
            log.debug("overLimit 暂未对流程[{}]建模限额比对，fail-closed=FALSE", processKey);
            return Boolean.FALSE;
        } catch (Exception ex) {
            log.warn("解析 overLimit 失败 bizId={}: {}", bizId, ex.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * 解析 outOfPlan（是否超预算/无可用预算）：报销单据的费用明细映射到预算科目后，逐行取可用额度
     * （{@code total_amount + adjust_amount − occupy_amount − used_amount}）与行金额比对，
     * 任一行金额 &gt; 可用额即判定超计划。
     *
     * <p>规则决策（有意为之，勿当疏漏）：费用项目在预算域<b>命中不到预算行</b>时判 FALSE 并记 debug——
     * "未纳入预算管控"与"超预算"是两件事；前者若判 TRUE，会让所有未配预算的公司/科目单据凭空走超预算审批链，
     * 属误伤。只有"确实命中预算行且额度不足"才是真实超计划。跨主体且重定位不到时同样跳过
     * （{@code resolveOccupyItem(..., strict=false)} 返回 null，不抛异常阻断发起，也不把钱记到别人公司）。
     * 解析异常 fail-closed 返回 FALSE 并告警（沿用 resolveOverStandard 策略）。
     */
    private Boolean resolveOutOfPlan(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return Boolean.FALSE;
        try {
            if (!"CLAIM".equals(processKey) && !"CLAIM_TRAVEL".equals(processKey)) return Boolean.FALSE;
            ExpBill bill = expBillMapper.selectById(bizId);
            if (bill == null) return Boolean.FALSE;
            List<ExpBillLine> lines = expBillLineMapper.selectList(
                    new LambdaQueryWrapper<ExpBillLine>()
                            .eq(ExpBillLine::getBillId, bizId));
            if (lines == null || lines.isEmpty()) return Boolean.FALSE;
            for (ExpBillLine line : lines) {
                if (line.getItemId() == null || line.getAmount() == null) continue;
                Long budgetItemId = budgetService.resolveOccupyItem(null, line.getItemId(),
                        bill.getCompanyId(), bill.getDeptId(), bill.getPeriodYear(), false);
                if (budgetItemId == null) {
                    log.debug("outOfPlan：费用项 {} 未命中预算行（未纳入预算管控），跳过 bizId={}", line.getItemId(), bizId);
                    continue;
                }
                BgItem bg = bgItemMapper.selectById(budgetItemId);
                if (bg == null) continue;
                BigDecimal avail = budgetService.available(bg);
                if (avail != null && line.getAmount().compareTo(avail) > 0) {
                    log.info("报销单[{}]明细[{}]金额{} 超过预算行[{}]可用额{}，判定超计划",
                            bizId, line.getId(), line.getAmount(), budgetItemId, avail);
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        } catch (Exception ex) {
            log.warn("解析 outOfPlan 失败 bizId={}: {}", bizId, ex.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * 解析 hasContract（是否关联合同）：以单据头真实字段 {@code exp_bill.contract_id} 非空为准。
     * 合同类流程（processKey 含 CONTRACT）审批对象本身就是合同，保持恒 TRUE 以不破坏既有语义，
     * 其余流程一律 fail-closed 返回 FALSE。
     *
     * <p>待接线项：退货单（PUR_RETURN/SALE_RETURN）的合同要经 源单→采购订单→合同 二级追溯，域耦合重，
     * 仍 fail-closed 返回 FALSE。目前全库 {@code exp_bill.contract_id} 尚无填充，故实际取值以 FALSE 为主——
     * 合同域接上之后本判定自动生效，不需要再改这个方法。
     */
    private Boolean resolveHasContract(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return Boolean.FALSE;
        try {
            ExpBill bill = expBillMapper.selectById(bizId);
            if (bill != null && bill.getContractId() != null) {
                log.info("单据[{}]关联合同[{}]，hasContract=true", bizId, bill.getContractId());
                return Boolean.TRUE;
            }
            if (processKey.toUpperCase().contains("CONTRACT")) return Boolean.TRUE;
            log.debug("hasContract：单据未关联合同（contract_id 为空）processKey={} bizId={} → FALSE", processKey, bizId);
            return Boolean.FALSE;
        } catch (Exception ex) {
            log.warn("解析 hasContract 失败 processKey={} bizId={}: {}", processKey, bizId, ex.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * 分支变量实算透出（预演，只读）：返回 {@code start()} 会注入引擎的全部分支变量真值，
     * 供运维/设计器"预演"某张单会走哪条分支（有未结清借款→hasLoan=true、明细超标准→overLimit=true、
     * 脱离预算计划→outOfPlan=true 等）。解析器 fail-closed 返回 FALSE 时对应条目同样入参（值=FALSE），
     * 调用方可据 processKey 判断该变量是否被建模。
     */
    public Map<String, Object> resolveBranchVars(String bizType, Long bizId, String subType) {
        Map<String, Object> out = new LinkedHashMap<>();
        String processKey = resolveStartProcessKey(bizType, bizId, subType, null);
        out.put("processKey", processKey);
        BigDecimal amt = BigDecimal.ZERO;
        try {
            ExpBill b = expBillMapper.selectById(bizId);
            if (b != null && b.getTotalAmount() != null) amt = b.getTotalAmount();
        } catch (Exception ignored) { /* 预演取金额失败按 0 处理：预演是只读旁路，不因查询失败中断 */ }
        out.put("amount", amt);
        out.put("orgType", isBaseCompany() ? "BASE" : "GROUP");
        String expenseType = resolveExpenseType(processKey, bizId);
        out.put("expenseType", expenseType == null ? "" : expenseType);
        out.put("overStandard", resolveOverStandard(processKey, bizId));
        out.put("abroad", resolveAbroad(processKey, bizId));
        out.put("hasLoan", resolveHasLoan(processKey, bizId));
        out.put("outOfPlan", resolveOutOfPlan(processKey, bizId));
        out.put("overLimit", resolveOverLimit(processKey, bizId));
        out.put("hasContract", resolveHasContract(processKey, bizId));
        // 预演必须与 start() 注入引擎的取值<b>逐字同源</b>，否则会出现"预演说走 A 档、运行时走了 B 档"。
        // 规则同 start()：billType＝bizType（BPMN 条件字面量所在域），子类型另列 subType。
        out.put("billType", bizType == null || bizType.isBlank() ? "" : bizType);
        out.put("subType", subType == null || subType.isBlank() ? "" : subType);
        return out;
    }

    /**
     * 解析出差事前申请的国外出差标记（TRAVEL_G1 网关分支变量 abroad）。
     * 数据来源：出差单主表 exp_bill.ext_json 中的 abroadFlag（前端出差事前申请表单勾选，布尔值）。
     * 仅出差事前申请流程（processKey=TRAVEL）有意义；其余流程返回 false。
     */
    private boolean resolveAbroad(String processKey, Long bizId) {
        if (processKey == null || bizId == null) return false;
        if (!"TRAVEL".equals(processKey)) return false;
        try {
            ExpBill bill = expBillMapper.selectById(bizId);
            if (bill == null || bill.getExtJson() == null || bill.getExtJson().isBlank()) {
                return false;
            }
            JsonNode root =
                    new ObjectMapper().readTree(bill.getExtJson());
            JsonNode f = root.path("abroadFlag");
            if (f.isBoolean()) return f.asBoolean();
            if (f.isTextual()) return Boolean.parseBoolean(f.asText());
            // 缺陷8(20260918)：出差申请页只有「出差类型」字段(extJson.travelType=DOMESTIC/ABROAD)，无独立 abroadFlag，
            // 导致 TRAVEL_G1 abroad=true 分支(国外出差→总裁特批)从页面永远无法触发。此处兼容 travelType=ABROAD。
            JsonNode tt = root.path("travelType");
            if (tt.isTextual() && "ABROAD".equalsIgnoreCase(tt.asText())) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("解析国外出差标识失败 bizId={}: {}", bizId, ex.getMessage());
            return false;
        }
    }

    /**
     * 节点配置覆盖：按 model_key + node_key 读 wf_node_config，
     * 把任务审批人重设为配置值（USER 指定人 / ROLE 角色 / DEPT_LEADER 部门负责人 / INITIATOR 发起人）。
     * 无配置则保持 BPMN 默认（candidateGroups 角色或 assignee）。节点配置 tab 由此真正生效。
     *
     * <p><b>为什么不能按 node_key 精确匹配</b>：运行时 taskDefinitionKey 是 BPMN 动态生成的
     * {@code Task_N}，而配置表存的是业务节点编码（{@code N0xx}/{@code CLAIM_T1} 等），
     * 两者不同形，交叉匹配一个都命中不了。命中不了就等于节点配置全表失效，
     * empty_strategy 空策略的兜底也一并失效。
     * 所以统一经 {@link WfNodeConfigResolver} 解析（精确键优先，补 {@code Task_N → 按 sort 第 N 个节点}
     * 兜底键），与超时链路（WfTimeoutJob）共用同一套映射，避免两处规则漂移。
     */
    private void applyNodeConfig(String processKey, Task task) {
        try {
            if (processKey == null || task == null) return;
            WfNodeConfig cfg =
                    wfNodeConfigResolver.resolve(processKey, task.getTaskDefinitionKey());
            if (cfg == null || cfg.getAssignType() == null || cfg.getAssignType().isBlank()) {
                return;
            }
            // 多实例节点（会签 ALL / 顺序会签 SEQ / 票签 VOTE）：每实例办理人由 BPMN 元素变量
            // ${assignee} 按 <nodeKey>_assignees 集合逐一分派。此处严禁再用"角色首个持有人"覆盖——
            // 否则所有实例被改成同一个人：SEQ 退化为同一人重复审批（元素变量已推进到 collection[i]，
            // 却被 tryResolveAssignee 改回首人），ALL 丢失"多人各一票"语义。候选集已在 start() 注入。
            String mt = cfg.getMultiType();
            if (mt != null && !mt.isBlank()) {
                String mu = mt.trim().toUpperCase();
                if ("ALL".equals(mu) || "SEQ".equals(mu) || "VOTE".equals(mu)) {
                    log.info("多实例节点跳过指派覆盖: key={} node={} multiType={}", processKey, cfg.getNodeName(), mu);
                    return;
                }
            }
            String value = cfg.getAssignValue() == null ? "" : cfg.getAssignValue().trim();
            switch (cfg.getAssignType()) {
                case "USER" -> {
                    if (!value.isBlank()) {
                        assignWithDelegateSubstitution(processKey, task, value.split(",")[0]);
                    }
                }
                case "ROLE" -> {
                    if (value.isBlank()) break;
                    // POOL 任务池档：不自动指派首持有人，任务保持"仅候选组"态——todo()/myTodo 全员可见，
                    // 由候选人「领取」锁定办理（参考任务池抢单）。超时/空策略仍按节点配置走。
                    if ("POOL".equalsIgnoreCase(trim(cfg.getHandlingMode()))) {
                        Set<String> have = new HashSet<>();
                        taskService.getIdentityLinksForTask(task.getId()).forEach(il -> {
                            if ("candidate".equals(il.getType()) && il.getGroupId() != null) have.add(il.getGroupId());
                        });
                        for (String rc : value.split(",")) {
                            String g = rc.trim();
                            if (!g.isBlank() && !have.contains(g)) taskService.addCandidateGroup(task.getId(), g);
                        }
                        // 智能推送（"优先分给处理中任务量最少的业务员"）：
                        // 开关 pool.autoDispatch=1 时，在候选人中按当前在办任务量最少自动指派；
                        // unclaim 时打了 pool_no_auto_assign 局部变量的任务不参与自动分配（回收后仅可人工领取）。
                        // 无候选人/推送失败 → 保持候选组抢单（兜底）。
                        Object noAuto = null;
                        try { noAuto = taskService.getVariableLocal(task.getId(), "pool_no_auto_assign"); } catch (Exception ignore) { /* 局部变量不存在/不可读按「未设置」处理（Flowable 缺变量会抛异常，属正常分支） */ }
                        if (noAuto == null && isPoolAutoDispatchEnabled()) {
                            try {
                                List<Long> cands = nodeOrgResolver.listOperableUserIds(task);
                                // 推送前做连办预检（连办校验本身一字未动，校验是 413 的必然化产物）。
                                // 智能推送只看"在办量最少"，不看"这人在本实例已经办过哪一节"。POOL 链一旦
                                // 落在 approve.deny_serial_same_handler 生效的域（现网＝GL_MONTH_CLOSE 月结链，
                                // 按角色铺人 + 禁 admin），把节点推给刚办过上一节的人就是<b>明知必拒还推</b>：
                                // 他打开就是"禁止同一人在同一审批流程中连续办理多个节点"，节点既办不掉也
                                // 领不回别人（抢单池已因 assignee 非空而不再对同池其他人开放）⇒ 节点自锁。
                                // 校验关时（缺省）本段零成本：不查 wf_instance、不查历史表，行为逐字不变。
                                final boolean serialPrecheck = isSerialSameHandlerDenied(bizTypeOfInstance(task.getProcessInstanceId()));
                                Long best = null; long bestLoad = Long.MAX_VALUE;
                                if (cands != null) for (Long uid : cands) {
                                    if (uid == null || uid <= 0) continue;
                                    SysUser su = sysUserMapper.selectById(uid);
                                    if (su == null || !"NORMAL".equals(su.getStatus())
                                            || Boolean.TRUE.equals(su.getIsAdmin()) || "admin".equals(su.getUserName())) continue;
                                    if (serialPrecheck && !isSystemAutoAuditor(uid)
                                            && handledNodesInInstance(task.getProcessInstanceId(), uid)) {
                                        log.info("智能推送跳过 userId={}：本链有连办闸且其已办理本实例节点（推给他必被拒）", uid);
                                        continue;
                                    }
                                    long load = taskService.createTaskQuery().taskAssignee(String.valueOf(uid)).count();
                                    if (load < bestLoad) { bestLoad = load; best = uid; }
                                }
                                if (best != null) {
                                    taskService.setAssignee(task.getId(), String.valueOf(best));
                                    log.info("智能推送: {} ← 候选中在办量最少者 userId={}（在办 {} 件）", cfg.getNodeName(), best, bestLoad);
                                } else {
                                    // 全员被连办校验预检筛掉＝这节本来就只能由"没办过本实例的人"抢：
                                    // 保持候选组抢单池（与无候选人同一兜底路径，人工领取不受影响）。
                                    log.info("智能推送: {} 无可用候选人（含连办闸预检筛除），保持抢单池", cfg.getNodeName());
                                }
                            } catch (Exception ex) {
                                log.warn("智能推送失败 {} 回退抢单池: {}", cfg.getNodeName(), ex.getMessage());
                            }
                        } else if (noAuto != null) {
                            log.info("任务 {} 已打回收标记，不参与自动分配，仅可人工领取", task.getId());
                        }
                        log.info("节点配置 {} 为 POOL 任务池：候选组={} 待领取", cfg.getNodeName(), value);
                        break;
                    }
                    // PICK 档：只认上一环节点名的人（流程变量 pick_assignee_{nodeKey}），且点名对象必须仍在候选集内。
                    // 点名为何要二次校验：变量可被绕开页面直接写入，候选集也会随人员异动变化。
                    // 点名缺失/失效时<b>不阻断</b>——超时 AUTO_PASS 之外的机器路径（批量审批、OA 代审）
                    // 无法弹窗征询，硬阻断会把已完成的审批变成"报错但流程已推进"的双态；
                    // 因此"必须选人"这条硬约束落在 UI 审批入口（见 enforcePickOnApprove），此处退回 AUTO 规则并告警。
                    Long picked = consumePickedAssignee(processKey, cfg, task);
                    if (picked != null) {
                        assignWithDelegateSubstitution(processKey, task, picked);
                        break;
                    }
                    // 动态解析：直线上级沿部门汇报线、COMPANY 角色解析本公司唯一持有人；
                    // 解析唯一则直接指派，否则维持候选组（todo 侧按公司过滤兜底）
                    Long resolved = nodeOrgResolver.tryResolveAssignee(
                            task.getProcessInstanceId(), value.split(",")[0].trim());
                    if (resolved != null) {
                        assignWithDelegateSubstitution(processKey, task, resolved);
                    } else {
                        for (String rc : value.split(",")) {
                            if (!rc.isBlank()) taskService.addCandidateGroup(task.getId(), rc.trim());
                        }
                    }
                }
                case "INITIATOR" -> {
                    // 发起人作为审批人（自审节点）：取提交时登录用户
                    Long initiator = approverUserIdOrDefault();
                    if (initiator != null && initiator > 0) {
                        assignWithDelegateSubstitution(processKey, task, initiator);
                    }
                }
                case "DEPT_LEADER", "DEPT_LEADER_UP" -> {
                    // 逐级上级：wf_node_config.up_level 就是「逐级上级时向上层数」。
                    // 这条分支不能缺：缺了 DEPT_LEADER_UP 会静默落到 default 保持 BPMN 默认，
                    // 配置者以为"按汇报线自动找 N 级上级"，实际取的是 BPMN 里写死的候选组。
                    // 取人复用 ESCALATE 那套"沿汇报线向上第 N 级"规则（跳过发起人本人、跳过 admin、
                    // 级数不足取已到最高级、再退单据公司组织负责人），单一实现避免两处规则漂移。
                    int lv = "DEPT_LEADER".equals(cfg.getAssignType()) ? 1
                            : (cfg.getUpLevel() == null ? 1 : cfg.getUpLevel());
                    Long leader = nodeOrgResolver.escalateTarget(task.getProcessInstanceId(), lv);
                    if (leader != null) {
                        assignWithDelegateSubstitution(processKey, task, leader);
                        log.info("逐级上级取人成功: key={} node={} upLevel={} -> userId={}",
                                processKey, cfg.getNodeName(), lv, leader);
                    } else {
                        log.warn("逐级上级取人失败（汇报线不足 {} 级且公司无组织负责人）: key={} node={}，保持候选组",
                                lv, processKey, cfg.getNodeName());
                    }
                }
                // 参与人解析：POST / SHARE_POOL / EXPRESSION 以及任何未识别类型，
                // 一律交给 WfAssigneeResolver 按「维度组合（并且/或者/排除）＋ 参与人方案（优先级＋条件）」取人。
                // 不接这段就是静默 no-op —— 配置者以为配了，实际一个人都没派。
                default -> applyAssigneeSchemes(processKey, cfg, task);
            }
            log.info("节点配置已应用: key={}, node={}, type={}, value={}", processKey, cfg.getNodeName(), cfg.getAssignType(), value);
        } catch (BizException be) {
            // 配了参与人方案却全部解析不出人 ⇒ 必须冒泡（不能静默留一个没人可办的候选组），
            // 更不得被下面的兜底 catch 吞成一行 log —— 那样等于把"配置错误"变成"单据永久滞留"。
            throw be;
        } catch (Exception ex) {
            log.warn("节点配置应用失败 key={} node={}: {}", processKey, task == null ? null : task.getTaskDefinitionKey(), ex.getMessage());
        }
    }

    /**
     * 参与人方案接线：{@code default} 分支（含 POST/SHARE_POOL/EXPRESSION）的取人出口。
     *
     * <p><b>三种情形的行为是刻意分开的：</b>
     * <ul>
     *   <li><b>完全没配方案</b> ⇒ 直接返回，保持默认零变化（也是回滚路径）；</li>
     *   <li><b>配了方案且解析出人</b> ⇒ 指派首个命中人（候选组仍保留，同角色同事依权可办，兜底不断流）；</li>
     *   <li><b>配了方案但全不中</b> ⇒ {@link WfAssigneeResolver#resolve} 抛 {@link BizException}，此处原样冒泡。</li>
     * </ul>
     * 解析器自身异常（如配置表不可读）由解析器内部按"无方案"兜住，不会把审批主链路打断。
     */
    private void applyAssigneeSchemes(String processKey, WfNodeConfig cfg, Task task) {
        WfAssigneeResolver.Resolution r = assigneeResolver.resolve(
                processKey, cfg.getNodeKey(), task.getProcessInstanceId(), task);
        if (!r.configured() || r.userIds().isEmpty()) {
            return; // 无方案 ⇒ 保持既有 no-op 行为（零变化）
        }
        assignWithDelegateSubstitution(processKey, task, r.userIds().get(0));
        log.info("参与人方案指派: key={} node={} -> userId={}", processKey, cfg.getNodeName(), r.userIds().get(0));
    }

    /**
     * 指派出口：所有"节点配置解析出的办理人 → 引擎 assignee"的唯一落点。
     *
     * <p>做一件事——委托语义为"改派"且该流程在灰度清单内、且这个人此刻确有生效委托时，
     * 把办理人换成受托人，并补一条 {@code DELEGATE_TAKEOVER} 记录（原办理人是谁、为什么换）。
     * 三个条件缺一个就按原办理人指派，不做替换。
     *
     * <p>为什么记录不能省：改派后 {@code wf_task_history.oper_user_id} 是实际办理人（受托人），
     * 没有这条"从谁改给谁"的行，跨公司/追责规则就只能靠猜。
     */
    private void assignWithDelegateSubstitution(String modelKey, Task task, String rawAssignee) {
        if (task == null || rawAssignee == null || rawAssignee.isBlank()) {
            return;
        }
        Long resolved;
        try {
            resolved = Long.valueOf(rawAssignee.trim());
        } catch (NumberFormatException nf) {
            // 历史/外部集成里 assign_value 允许写非数字（表达式、用户名）：原样交给引擎
            taskService.setAssignee(task.getId(), rawAssignee);
            return;
        }
        Long effective = nodeOrgResolver.delegateSubstitute(modelKey, resolved);
        taskService.setAssignee(task.getId(), String.valueOf(effective));
        if (effective.equals(resolved)) {
            return;
        }
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "DELEGATE_TAKEOVER", resolved, effective,
                "委托代办改派：本节点办理人 " + resolved + " 在委托期内，直接指派受托人 " + effective);
        log.info("委托改派生效(新任务取人): model={} node={} {} -> {}",
                modelKey, task.getTaskDefinitionKey(), resolved, effective);
    }

    /** 同 {@link #assignWithDelegateSubstitution(String, Task, String)}，入参已是用户 ID。 */
    private void assignWithDelegateSubstitution(String modelKey, Task task, Long resolvedUserId) {
        if (resolvedUserId == null) {
            return;
        }
        assignWithDelegateSubstitution(modelKey, task, String.valueOf(resolvedUserId));
    }

    /**
     * 委托生效时的历史改派：把委托人手上<b>已指派给他</b>（assignee=委托人）的
     * 在办待办改指受托人，走引擎通道 {@code taskService.setAssignee} + 镜像同步 + 记录，禁止直写 DB。
     *
     * <p>只改 assignee 态，不动"仅候选组"态：委托人角色候选的任务不属于他个人名下，
     * 把它 setAssignee 给受托人＝从同角色其他同事手上抢单，属越权。这类任务仍按"借资格"并入双方待办。
     *
     * @return 实际改派的条数（0＝无历史待办可改，或该流程不在灰度清单内）
     */
    public int takeOverDelegatedTasks(Long fromUserId, Long toUserId, Long delegateId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return 0;
        }
        List<Task> held;
        try {
            held = taskService.createTaskQuery()
                    .taskAssignee(String.valueOf(fromUserId)).active().list();
        } catch (Exception ex) {
            log.warn("委托改派取存量待办失败 from={}: {}", fromUserId, ex.getMessage());
            return 0;
        }
        int moved = 0;
        for (Task t : held) {
            String mk = modelKeyOf(t);
            if (!nodeOrgResolver.delegateReassignEnabled(mk)) {
                continue;
            }
            try {
                taskService.setAssignee(t.getId(), String.valueOf(toUserId));
                syncTaskViewAfterAssigneeChange(t, toUserId, "委托改派");
                writeHistory(t.getProcessInstanceId(), t.getId(), t.getTaskDefinitionKey(), t.getName(),
                        "DELEGATE_TAKEOVER", fromUserId, toUserId,
                        "委托#" + delegateId + " 生效改派：原办理人 " + fromUserId + " → 受托人 " + toUserId);
                try {
                    notifyService.notifyUser(toUserId, "APPROVAL", "代管待办提醒",
                            "【" + t.getName() + "】由您代办的待办已转至您名下，请及时处理", null, "/approval");
                    notifyService.notifyUser(fromUserId, "DELEGATE_TAKEOVER", "委托已改派",
                            "您委托的待办【" + t.getName() + "】已按委托规则转给受托人办理，委托期内该待办不再挂在您名下",
                            null, "/system/delegate");
                } catch (Exception ex) {
                    log.warn("委托改派通知失败 task={}: {}", t.getId(), ex.getMessage());
                }
                moved++;
                log.info("委托改派生效(存量): delegate={} task={} node={} {} -> {}",
                        delegateId, t.getId(), t.getTaskDefinitionKey(), fromUserId, toUserId);
            } catch (Exception ex) {
                log.error("委托改派单条失败 delegate={} task={}: {}", delegateId, t.getId(), ex);
            }
        }
        return moved;
    }

    /**
     * 委托到期 / 取消时的反向回跳：只回跳"仍未被办理"的任务——受托人已办的不动，
     * 期间被再次转办/改派走的（当前 assignee 已不是受托人）同样不动。
     *
     * <p>查询依据是 {@code DELEGATE_TAKEOVER} 记录行（委托人=操作人、受托人=target、时间落在委托区间内），
     * 因此不需要给 {@code wf_task} 加列即可回跳；"待办没有代办归属列"这一规则缺陷已在 登记，
     * 注释态 DDL 随本批 seed 一并交付（执行需授权）。
     *
     * @return 实际回跳的条数
     */
    public int revertDelegatedTasks(Long fromUserId, Long toUserId,
                                    LocalDateTime beginTime, LocalDateTime endTime) {
        if (fromUserId == null || toUserId == null) {
            return 0;
        }
        List<WfTaskHistory> marks;
        try {
            LambdaQueryWrapper<WfTaskHistory> qw =
                    new LambdaQueryWrapper<WfTaskHistory>()
                            .eq(WfTaskHistory::getAction, "DELEGATE_TAKEOVER")
                            .eq(WfTaskHistory::getOperUserId, fromUserId)
                            .eq(WfTaskHistory::getTargetUserId, toUserId);
            if (beginTime != null) {
                qw.ge(WfTaskHistory::getOperTime, beginTime.minusDays(1));
            }
            if (endTime != null) {
                qw.le(WfTaskHistory::getOperTime, endTime);
            }
            marks = wfQueryService.listHistory(qw);
        } catch (Exception ex) {
            log.warn("委托回跳取改派留痕失败 from={} to={}: {}", fromUserId, toUserId, ex.getMessage());
            return 0;
        }
        if (marks == null || marks.isEmpty()) {
            return 0;
        }
        Set<String> done = new HashSet<>();
        int moved = 0;
        for (WfTaskHistory h : marks) {
            String taskId = h.getTaskId();
            if (taskId == null || !done.add(taskId)) {
                continue;
            }
            Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (t == null || !String.valueOf(toUserId).equals(t.getAssignee())) {
                continue; // 已办结 / 已被再次转走：不动别人的既成事实
            }
            try {
                taskService.setAssignee(taskId, String.valueOf(fromUserId));
                syncTaskViewAfterAssigneeChange(t, fromUserId, "委托回跳");
                writeHistory(t.getProcessInstanceId(), taskId, t.getTaskDefinitionKey(), t.getName(),
                        "DELEGATE_REVERT", toUserId, fromUserId,
                        "委托到期/取消回跳：该待办此前按委托转给 " + toUserId + " 代办，现回到原办理人 " + fromUserId);
                moved++;
                log.info("委托回跳: task={} {} -> {}", taskId, toUserId, fromUserId);
            } catch (Exception ex) {
                log.error("委托回跳单条失败 task={}: {}", taskId, ex);
            }
        }
        return moved;
    }

    /**
     * 只读：某流程节点的候选办理人名单（待办可观测性用，<b>不参与指派</b）。
     * 与 {@link #applyNodeConfig} 的 ROLE 分支同源：同一个 {@code wfNodeConfigResolver} 解析同一份
     * {@code wf_node_config}，因此这里列出的就是引擎"本可以从这些人里挑"的名单。
     *
     * @return 非 ROLE 取人（USER/INITIATOR）、配置未命中或解析异常时为空列表，前端按"无候选名单"展示
     */
    public List<Long> listNodeCandidates(String modelKey, String nodeKey, String procInstId) {
        try {
            WfNodeConfig cfg = wfNodeConfigResolver.resolve(modelKey, nodeKey);
            if (cfg == null || !"ROLE".equals(cfg.getAssignType())
                    || cfg.getAssignValue() == null || cfg.getAssignValue().isBlank()) {
                return List.of();
            }
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (String rc : cfg.getAssignValue().split(",")) {
                if (!rc.isBlank()) {
                    ids.addAll(nodeOrgResolver.listRoleCandidates(procInstId, rc.trim()));
                }
            }
            return new ArrayList<>(ids);
        } catch (Exception ex) {
            log.warn("节点候选人解析失败 model={} node={}: {}", modelKey, nodeKey, ex.getMessage());
            return List.of();
        }
    }

    // 取人方式 PICK（节点指定处理人）

    /** 上一环节点名结果的流程变量前缀（按被点名的节点键存放，避免网关分流后张冠李戴） */
    private static final String PICK_VAR_PREFIX = "pick_assignee_";
    /** 点名操作人（记录用），与前缀同键 */
    private static final String PICK_BY_PREFIX = "pick_by_";

    /**
     * 灰度总开关：{@code sys_config.approve.pick_mode.enabled=1} <b>且</b> model_key 命中
     * {@code approve.pick_mode.models} 白名单才生效。读失败一律按"未生效"处理（fail-closed 到现状），
     * 与 {@code approve.deny_self} 的 fail-closed 方向相反——因为这里的"默认"是零行为变更。
     */
    public boolean isPickModeEnabled(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return false;
        }
        try {
            if (!"1".equals(readConfig("approve.pick_mode.enabled"))) {
                return false;
            }
            String models = readConfig("approve.pick_mode.models");
            if (models == null || models.isBlank()) {
                return false;
            }
            for (String m : models.split(",")) {
                if (modelKey.equalsIgnoreCase(m.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.error("读取 PICK 灰度开关异常，按未生效处理 model={}: {}", modelKey, ex.getMessage());
            return false;
        }
    }

    private String readConfig(String key) {
        SysConfig c = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key)
                        .last("limit 1"));
        return c == null || c.getConfigValue() == null ? null : c.getConfigValue().trim();
    }

    /**
     * 通用"总开关 + model 白名单"灰度判定（与 {@link #isPickModeEnabled} 同一规则，fail-closed 到不生效）：
     * 总开关配置值须为 '1'，且白名单逗号串包含 modelKey；读取异常按未生效处理。
     */
    private boolean isConfigGatedOn(String enabledKey, String modelsKey, String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return false;
        }
        try {
            if (!"1".equals(readConfig(enabledKey))) {
                return false;
            }
            String models = readConfig(modelsKey);
            if (models == null || models.isBlank()) {
                return false;
            }
            for (String m : models.split(",")) {
                if (modelKey.equalsIgnoreCase(m.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.error("读取灰度开关异常，按未生效处理 {}: {}", enabledKey, ex.getMessage());
            return false;
        }
    }

    /**
     * 附随开关：通过侧审批意见是否必填。总开关缺省关＝现网行为不变；
     * 白名单为空＝对全部模型生效（避免"翻了开关却没反应"）；读取异常按关闭处理。
     */
    private boolean isApproveCommentRequired(Task task) {
        try {
            if (!"1".equals(readConfig("approve.comment.pass.enabled"))) {
                return false;
            }
            String models = readConfig("approve.comment.pass.models");
            if (models == null || models.isBlank()) {
                return true;
            }
            String modelKey = modelKeyOf(task);
            if (modelKey == null || modelKey.isBlank()) {
                return false;
            }
            for (String m : models.split(",")) {
                if (modelKey.equalsIgnoreCase(m.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.error("读取通过侧意见开关异常，按未生效处理: {}", ex.getMessage());
            return false;
        }
    }

    /** 加签校验总开关：待办列表据此决定要不要下发 {@code addSignAllowed} 标注，缺省关＝不标注 */
    public boolean isAddSignGateEnabled() {
        try {
            return "1".equals(readConfig("approve.addsign_gate.enabled"));
        } catch (Exception ex) {
            log.error("读取加签闸口总开关异常，按未生效处理: {}", ex.getMessage());
            return false;
        }
    }

    /** 加签校验灰度模型清单（与 {@link #addSign} 读同一份 {@code approve.addsign_gate.models}） */
    public Set<String> addSignGateModels() {
        try {
            String raw = readConfig("approve.addsign_gate.models");
            if (raw == null || raw.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(raw.split("[,，;；\\s]+")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        } catch (Exception ex) {
            log.error("读取加签闸口灰度清单异常，按未生效处理: {}", ex.getMessage());
            return Set.of();
        }
    }

    /**
     * 节点是否 PICK 档（含灰度判定）：开关未放行时所有节点等价 AUTO，即不发生点名。
     *
     * <p>限定 {@code assign_type=ROLE}：点名值是在 {@code applyNodeConfig} 的 ROLE 分支里消费的，
     * USER/INITIATOR/DEPT_HEAD 等分支各按自己的规则指派。若把 PICK 配到那些节点上，就会出现
     * "弹窗强制你选人、选完没人消费"的假功能，所以这里连同 UI 一起把 PICK 限制在 ROLE 节点。
     */
    private boolean isPickNode(String modelKey, WfNodeConfig cfg) {
        return cfg != null && modelKey != null && "ROLE".equals(cfg.getAssignType())
                && "PICK".equalsIgnoreCase(trim(cfg.getHandlingMode()))
                && isPickModeEnabled(modelKey);
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /**
     * PICK/POOL 共用候选集：节点角色成员，规则与真正取人的 ROLE 分支同源
     * （GROUP 跨公司 / COMPANY 按单据公司 / 排除停用与非 admin 账号），另按自审禁止开关剔除发起人——
     * 把单派回发起人会造成"下一节点被 deny_self 拒绝、流程就地卡死"，这不是可选优化而是必须剔除。
     */
    private List<Long> pickCandidates(String modelKey, WfNodeConfig cfg, String procInstId) {
        Long company = null;
        Long starter = null;
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst != null) {
                company = nodeOrgResolver.companyIdOfBill(inst.getBizType(), inst.getBizId());
                starter = inst.getStartUserId();
            }
        } catch (Exception ex) {
            log.warn("候选集单据组织解析失败 proc={}: {}", procInstId, ex.getMessage());
        }
        return candidatesInScope(cfg, company, starter);
    }

    /**
     * 候选人集合的公共内核：按 (单据公司, 申请人) 组织规则展开节点角色，再按自审开关剔除申请人。
     * 与 {@link #pickCandidates} 的唯一区别是不依赖流程实例——提单态点名时实例还不存在。
     */
    private List<Long> candidatesInScope(WfNodeConfig cfg, Long companyId, Long applicant) {
        List<Long> ids = new ArrayList<>();
        try {
            for (String rc : trim(cfg.getAssignValue()).split(",")) {
                if (!rc.isBlank()) {
                    for (Long id : nodeOrgResolver.listRoleCandidatesInScope(companyId, applicant, rc.trim())) {
                        if (!ids.contains(id)) {
                            ids.add(id);
                        }
                    }
                }
            }
            if (applicant != null && isDenySelfEnabled()) {
                ids.removeIf(applicant::equals);
            }
        } catch (Exception ex) {
            log.warn("候选集展开失败 company={} assign={}: {}", companyId, cfg.getAssignValue(), ex.getMessage());
        }
        return ids;
    }

    /**
     * 多实例候选注入：把本流程所有「会签/顺序会签/票签」节点的在办候选人集合写入流程变量，
     * 供 BPMN {@code multiInstanceLoopCharacteristics.flowable:collection} 在启动/到达该节点时查询。
     * 仅依赖 wf_node_config（权威多签定义源），不解析 node_json，避免与并行会签(parallelGateway)分支混淆。
     */
    private void injectMultiInstanceVars(String processKey, Map<String, Object> vars,
                                         String bizType, Long bizId, Long approver) {
        try {
            List<WfNodeConfig> all = wfNodeConfigMapper.selectList(
                    new LambdaQueryWrapper<WfNodeConfig>()
                            .eq(WfNodeConfig::getModelKey, processKey));
            if (all == null || all.isEmpty()) {
                return;
            }
            Long company = nodeOrgResolver.companyIdOfBill(bizType, bizId);
            // 申请人＝提单登录人（与 complete() 自审禁止守卫同源，保证多签节点不会把单子派回本人）
            Long applicant = approverUserIdOrDefault();
            for (WfNodeConfig cfg : all) {
                String mt = cfg.getMultiType();
                if (mt == null) {
                    continue;
                }
                mt = mt.toUpperCase();
                if (!("ALL".equals(mt) || "SEQ".equals(mt) || "VOTE".equals(mt))) {
                    continue;
                }
                String nodeKey = cfg.getNodeKey();
                if (nodeKey == null || nodeKey.isBlank()) {
                    continue;
                }
                List<String> assignees = resolveMiAssignees(cfg, company, applicant);
                // 候选为空：保持变量存在但为空集合——Flowable 多实例集合为空会即时以 0 实例结束该节点，
                // 行为与「角色无成员」一致（宁可空过，不静默卡死/不静默绕过）。
                vars.put(nodeKey + "_assignees", assignees);
                // 应到人数快照（票签聚合判定用）：多实例办结后 nrOfInstances 已随执行树销毁、
                // 拿不到"应到几人"，而比例判定必须知道分母 ⇒ 发起时把候选人数固化下来。
                vars.put(nodeKey + "_miTotal", (long) assignees.size());
                // 多签类型快照：进度接口要如实回显 ALL/SEQ/VOTE（BPMN 里只有 isSequential 布尔，
                // 区分不出"并行会签"与"票签"），故把配置值一并固化，避免运行期反查 wf_node_config。
                vars.put(nodeKey + "_multiType", mt);
                if ("VOTE".equals(mt)) {
                    vars.put(nodeKey + "_approvedCount", 0L);
                    vars.put(nodeKey + "_rejectedCount", 0L);
                    // 通过阈值快照：BPMN completionCondition 里 ratio 是编译进 XML 的字面量，
                    // 运行期 Java 侧判定（票签办结）与进度展示都需要它，故一并注入。
                    vars.put(nodeKey + "_voteRatio", cfg.getVoteRatio() == null ? 50 : cfg.getVoteRatio());
                    // 票＋人双维 + 不达票兜底
                    // 为什么必须在这里固化：BPMN 的 completionCondition 是编译期字面量，
                    // 而办结判定发生在运行期（Java 侧），两边必须拿到同一套规则，否则
                    // "XML 判定办结了、Java 判定没达标"会互相打架。新列缺省时回落到遗留
                    // vote_ratio / BY_PERSON / REJECT —— 历史只配了 vote_ratio 的节点行为逐字不变。
                    String voteMode = cfg.getVoteMode() == null || cfg.getVoteMode().isBlank()
                            ? "RATIO" : cfg.getVoteMode().trim().toUpperCase(Locale.ROOT);
                    String voteBasis = cfg.getVoteBasis() == null || cfg.getVoteBasis().isBlank()
                            ? "BY_PERSON" : cfg.getVoteBasis().trim().toUpperCase(Locale.ROOT);
                    String voteFallback = cfg.getVoteFallback() == null || cfg.getVoteFallback().isBlank()
                            ? "REJECT" : cfg.getVoteFallback().trim().toUpperCase(Locale.ROOT);
                    vars.put(nodeKey + "_voteMode", voteMode);
                    vars.put(nodeKey + "_voteValue",
                            cfg.getVoteValue() != null ? cfg.getVoteValue()
                                    : (cfg.getVoteRatio() == null ? 50 : cfg.getVoteRatio()));
                    vars.put(nodeKey + "_voteBasis", voteBasis);
                    vars.put(nodeKey + "_voteFallback", voteFallback);
                }
                log.info("多实例候选注入 node={} multiType={} 候选数={}", nodeKey, mt, assignees.size());
            }
        } catch (Exception ex) {
            log.warn("多实例候选注入失败 processKey={}: {}", processKey, ex.getMessage());
        }
    }

    /**
     * 解析单个多实例节点的在办候选人（返回 user-id 字符串列表，供 Flowable assignee 逐实例查询）。
     * USER 型：assign_value 直接是用户 ID，经 sanitizeApprover 校验（存在/NORMAL/至少一角色/非 admin）；
     * 其余（ROLE/GROUP/POST/DEPT_LEADER…）：复用 candidatesInScope 按角色展开在办候选；
     * 末位再按自审开关剔除申请人。
     */
    private List<String> resolveMiAssignees(WfNodeConfig cfg, Long companyId, Long applicant) {
        List<Long> ids = new ArrayList<>();
        try {
            if ("USER".equalsIgnoreCase(cfg.getAssignType()) && cfg.getAssignValue() != null) {
                for (String tok : cfg.getAssignValue().split(",")) {
                    tok = tok.trim();
                    if (tok.isBlank()) continue;
                    Long uid;
                    try {
                        uid = Long.parseLong(tok);
                    } catch (NumberFormatException nf) {
                        // 非数字用户标识视为无效 → 降级为 null（按「无指派」处理）
                        uid = null;
                    }
                    Long sane = uid != null ? sanitizeApprover(uid) : null;
                    if (sane != null && !ids.contains(sane)) {
                        ids.add(sane);
                    }
                }
            } else {
                ids.addAll(candidatesInScope(cfg, companyId, applicant));
            }
            if (applicant != null && isDenySelfEnabled()) {
                ids.removeIf(applicant::equals);
            }
        } catch (Exception ex) {
            log.warn("多实例候选解析失败 node={}: {}", cfg.getNodeKey(), ex.getMessage());
        }
        return ids.stream().map(String::valueOf).toList();
    }

    /**
     * 票签计票：任务所属节点若为 VOTE 多实例（启动期注入的 {@code <nodeKey>_approvedCount} 变量存在即判定），
     * 按审批结果（approved=true 赞成 / false 反对）累加流程变量计数。该变量是 BPMN completionCondition 的命中条件，
     * 计满即由引擎提前终止其余未投票实例（提前办结），避免"3 人投票、第 4 人无需再投"仍需等人。
     */
    private void countVoteIfNeeded(Task task, boolean approved) {
        try {
            String miKey = task.getTaskDefinitionKey();
            if (miKey == null) {
                return;
            }
            String apprVar = miKey + "_approvedCount";
            String rejVar = miKey + "_rejectedCount";
            Object a0 = runtimeService.getVariable(task.getProcessInstanceId(), apprVar);
            if (a0 == null) {
                return; // 非票签节点：不计数
            }
            long a = ((Number) a0).longValue();
            long r = ((Number) runtimeService.getVariable(task.getProcessInstanceId(), rejVar)).longValue();
            if (approved) {
                a++;
            } else {
                r++;
            }
            runtimeService.setVariable(task.getProcessInstanceId(), apprVar, a);
            runtimeService.setVariable(task.getProcessInstanceId(), rejVar, r);
            log.info("票签计票 node={} 赞成={} 反对={}", miKey, a, r);
        } catch (Exception ex) {
            log.warn("票签计票失败 taskId={}: {}", task.getId(), ex.getMessage());
        }
    }

    /**
     * 判定任务是否属于票签(VOTE)多实例节点：以发起期注入的 {@code <nodeKey>_voteRatio} 为准；
     * 本次升级前发起的实例没有该变量，退化为看 {@code <nodeKey>_approvedCount} 是否注入。
     * 探测异常一律返回 false ⇒ 保持"驳回即终止"的旧行为，不因探测失败把票签语义误套到普通节点上。
     */
    private boolean isVoteNode(Task task) {
        try {
            String pinst = task.getProcessInstanceId();
            String key = task.getTaskDefinitionKey();
            if (pinst == null || key == null) {
                return false;
            }
            if (runtimeService.getVariable(pinst, key + "_voteRatio") != null) {
                return true;
            }
            return runtimeService.getVariable(pinst, key + "_approvedCount") != null;
        } catch (Exception ex) {
            log.warn("票签节点判定失败 taskId={}: {}", task.getId(), ex.getMessage());
            return false;
        }
    }

    /**
     * 票签(VOTE)节点办结判定：多实例全部实例已了结（同节点无在办任务）时给出节点结论。
     *
     * <p><b>票＋人双维 + 不达票兜底</b>。规则与 BPMN
     * {@code completionCondition}（{@code WfBpmnTemplateService.voteCompletionCondition}）
     * <b>逐字对齐</b>——XML 负责"什么时候办结"，Java 负责"办结后算不算通过"，两处若各算一遍必然漂移：
     * <ul>
     *   <li>{@code vote_mode=COUNT}：赞成票 ≥ {@code vote_value} 票即通过；</li>
     *   <li>{@code vote_mode=RATIO}：赞成 × 100 ≥ 百分比 × 分母，分母按 {@code vote_basis}
     *       取"应到人数"（{@code _miTotal}，默认）或"实到票数"（赞成+反对）；</li>
     *   <li>新列缺省时回落遗留 {@code vote_ratio} + 应到分母，历史行的判定结果不变。</li>
     * </ul>
     *
     * <p><b>不达票兜底</b>：多实例全部办完但未达阈值时，固定为
     * "落默认审批项 + 流程继续"，具体落哪一项由 {@code vote_fallback} 决定——
     * {@code REJECT}（默认）以驳回语义收尾（见 {@link #terminateAsRejected}），
     * {@code PASS} 则记一条 {@code VOTE_NOT_REACHED} 记录后放行。两种结果都会写
     * {@code voteResult} / {@code voteFallback} 流程变量，供进度展示与质量报表统计。
     *
     * <p><b>为什么必须在这里判</b>：{@code complete()} 的驳回兜底是「一票否决」语义（approved=false 即终止
     * 整个实例），对票签是错的——1 张反对票就把流程杀掉，比例判定根本没有生效机会。
     * 故票签节点在 {@code complete()} 中<b>跳过即时终止</b>，改为逐票累计、办结后统一判定。
     *
     * <p>未办结（同节点仍有在办任务）时直接返回，什么都不做——其余投票人继续投。
     *
     * @param operUserId 末票操作人（写 {@code VOTE_NOT_REACHED} 记录用；作业路径可为空）
     */
    private void resolveVoteOutcome(Task task, String comment, String rejectType, Long operUserId) {
        try {
            String pinst = task.getProcessInstanceId();
            String nodeKey = task.getTaskDefinitionKey();
            if (pinst == null || nodeKey == null) {
                return;
            }
            long open = taskService.createTaskQuery().processInstanceId(pinst)
                    .taskDefinitionKey(nodeKey).count();
            if (open > 0) {
                return; // 同节点仍有在办实例：未办结，等其余投票
            }
            long total = numberOr(runtimeService.getVariable(pinst, nodeKey + "_miTotal"), 0L);
            long ack = numberOr(runtimeService.getVariable(pinst, nodeKey + "_approvedCount"), 0L);
            long rjk = numberOr(runtimeService.getVariable(pinst, nodeKey + "_rejectedCount"), 0L);
            int ratio = (int) numberOr(runtimeService.getVariable(pinst, nodeKey + "_voteRatio"), 50L);
            String mode = voteVar(pinst, nodeKey + "_voteMode", "RATIO");
            String basis = voteVar(pinst, nodeKey + "_voteBasis", "BY_PERSON");
            String fallback = voteVar(pinst, nodeKey + "_voteFallback", "REJECT");
            Object valueObj = runtimeService.getVariable(pinst, nodeKey + "_voteValue");
            int value = valueObj instanceof Number vn ? vn.intValue() : ratio;
            // 分母：BY_VOTE＝已投票数（实到），BY_PERSON＝参与人总数（应到，升级前实例无 _miTotal 时退化为实到）
            long denom = "BY_VOTE".equals(basis) ? (ack + rjk) : (total > 0 ? total : ack + rjk);
            if (denom <= 0) {
                return; // 无任何实例信息可判（如空候选集）：不擅自判定（宁可漏判，不可错杀）
            }
            boolean pass;
            String needText;
            if ("COUNT".equals(mode)) {
                int need = Math.max(1, value);
                pass = ack >= need;
                needText = "需 ≥ " + need + " 票";
            } else {
                int pct = value < 1 ? 1 : Math.min(value, 100);
                pass = ack * 100 >= (long) pct * denom;
                needText = "需 ≥ " + pct + "%（分母 " + ("BY_VOTE".equals(basis) ? "实到" : "应到")
                        + " " + denom + "）";
            }
            runtimeService.setVariable(pinst, nodeKey + "_voteResult", pass);
            log.info("票签判定 node={} 赞成={} 反对={} 应到={} 口径={}/{} 阈值={} 结论={}",
                    nodeKey, ack, rjk, total, mode, basis, value, pass ? "通过" : "未达票");
            if (pass) {
                return;
            }
            // —— 不达票兜底：固定语义＝落默认审批项 + 流程继续（REJECT 时"默认审批项"即驳回）——
            runtimeService.setVariable(pinst, nodeKey + "_voteFallback", fallback);
            String reason = "票签未达通过阈值（赞成 " + ack + "/" + denom + "，" + needText + "）";
            if (comment != null && !comment.isBlank()) {
                reason = reason + "；末票意见：" + comment;
            }
            // 未达票必须有可查询的落点：VOTE_NOT_REACHED 这条记录就是它的解释与统计来源
            writeHistory(pinst, task.getId(), nodeKey, task.getName(), "VOTE_NOT_REACHED",
                    operUserId, reason + "；默认=" + fallback);
            if ("PASS".equals(fallback)) {
                log.info("票签不达票按兜底放行（流程继续）node={} {}", nodeKey, reason);
                return;
            }
            terminateAsRejected(pinst, reason, rejectType);
        } catch (Exception ex) {
            log.warn("票签判定失败 taskId={}: {}", task.getId(), ex.getMessage());
        }
    }

    /** 票签规则变量读取：空值/空串一律回落默认值（未配置即按默认口径判） */
    private String voteVar(String pinst, String name, String def) {
        Object v = runtimeService.getVariable(pinst, name);
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return String.valueOf(v).trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 以"驳回"语义收尾流程实例：终止实例 + wf_instance 置 REJECTED + 发布 WfProcessEndedEvent(成功=false)。
     *
     * <p>从 {@code complete()} 的驳回兜底抽出，供两条路径复用：① 普通节点驳回（approved=false）；
     * ② 票签节点办结后未达通过阈值。两条路径业务语义完全一致（"审批不通过"，业务监听置单据 REJECTED），
     * 因此合并为一处，避免"两条驳回路径的收尾动作日后各自漂移"。
     */
    private void terminateAsRejected(String pinst, String reason, String rejectType) {
        try {
            if (pinst == null
                    || runtimeService.createProcessInstanceQuery().processInstanceId(pinst).singleResult() == null) {
                return; // 实例已结束（并发下另一路已收尾）：不重复终止、不重复发事件
            }
            Long tenantId = null;
            try {
                WfInstance inst = wfQueryService.instanceByProcInstId(pinst);
                if (inst != null) tenantId = inst.getTenantId();
            } catch (Exception ignore) { /* 视图未写入数据库则交给总线兜底 */ }
            Map<String, Object> pv = runtimeService.getVariables(pinst);
            runtimeService.deleteProcessInstance(pinst, "审批驳回");
            // 驳回走的是 deleteProcessInstance，引擎只发 PROCESS_CANCELLED，不触发 PROCESS_COMPLETED
            // 侧的 wf_instance 状态回写监听，实例会一直停在 RUNNING。所以这里要随单据置 REJECTED
            // 同步把实例状态改掉。
            try {
                wfQueryService.updateInstanceStatus(pinst, "REJECTED", LocalDateTime.now());
                // 幽灵待办办结：驳回作废后，在办 wf_task 的 PENDING 行须同步关闭（引擎任务已随实例删除）
                // 关闭同时落作废记录——驳回单最常出现"链上凭空少几节、看不单子死在哪"
                wfQueryService.closePendingTasksByInstance(pinst, "审批驳回，流程终止；本节任务未办理即作废");
            } catch (Exception sex) {
                log.warn("驳回实例状态回写失败 procInstId={}", pinst);
            }
            WfProcessEndedEvent ended = new WfProcessEndedEvent(
                    (String) pv.get("bizType"), (Long) pv.get("bizId"), Boolean.FALSE,
                    (reason == null || reason.isBlank()) ? "审批驳回" : reason,
                    rejectType, pinst);
            ended.setTenantId(tenantId);
            eventPublisher.publishEvent(ended);
        } catch (Exception ex) {
            log.error("驳回终止失败 procInstId={}", pinst, ex);
        }
    }

    /** 变量查询容错：null / 非数值一律退化为默认值（票签判定不能因一个脏变量就抛 500） */
    private static long numberOr(Object v, long def) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return def;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 从 JDBC 结果行里按名取值（大小写不敏感）：MySQL 对列别名的回显大小写依驱动/配置而异，
     * 直接 {@code row.get("userId")} 在个别环境下取到 null，故统一走本方法。
     */
    private static Object pickIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object v = row.get(key);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 「审批项」读取（只读）：按任务反查其节点上启用的自定义审批结果，供审批弹窗渲染按钮。
     * 无配置返回空列表，前端据此不渲染自定义按钮。
     *
     * <p>与执行面 {@link #complete(String, boolean, String, String, Long, Long, String, String, String)}
     * <b>同源同一解析器</b>（{@link WfApprovalItemService#listEnabled}，{@code modelKey:nodeKey} 精确匹配），
     * 不允许两处各写一套映射——否则会出现"按钮看得见、点下去报审批项不存在"。
     */
    public List<WfApprovalItem> approvalItemsOf(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || task.getProcessDefinitionId() == null) {
            return Collections.emptyList();
        }
        String modelKey = task.getProcessDefinitionId().split(":")[0];
        return approvalItemService.listEnabled(modelKey, task.getTaskDefinitionKey());
    }

    /**
     * 票签/会签节点进度（只读，供审批页与流程痕迹展示"已投 x/n · 赞成 a 反对 r"）。
     * 非多实例节点返回 {@code multi=false}，调用方据此不渲染进度条。
     * 逐票明细直接复用 {@code wf_task_history}（每次 complete 已写 APPROVE/REJECT + 操作人 + 意见），
     * 不再另建计票表——避免"同一事实两处记录、日后必然漂移"。
     */
    public Map<String, Object> miProgress(String taskId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("multi", false);
        try {
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (task == null) {
                return out;
            }
            String pinst = task.getProcessInstanceId();
            String nodeKey = task.getTaskDefinitionKey();
            out.put("nodeKey", nodeKey);
            out.put("nodeName", task.getName());
            Object ratioVar = runtimeService.getVariable(pinst, nodeKey + "_voteRatio");
            boolean vote = isVoteNode(task);
            if (!vote && runtimeService.getVariable(pinst, nodeKey + "_assignees") == null) {
                return out; // 普通单办理人节点
            }
            long total = numberOr(runtimeService.getVariable(pinst, nodeKey + "_miTotal"), 0L);
            // 引擎计票变量只在票签(VOTE)节点注入并由 complete() 累加（见 injectMultiInstanceVars /
            // countVoteIfNeeded）；会签 ALL/SEQ 根本没有这两个变量 ⇒ 不能据此判"有人投过票"。
            boolean engineCounted = runtimeService.getVariable(pinst, nodeKey + "_approvedCount") != null;
            long ack = numberOr(runtimeService.getVariable(pinst, nodeKey + "_approvedCount"), 0L);
            long rjk = numberOr(runtimeService.getVariable(pinst, nodeKey + "_rejectedCount"), 0L);
            out.put("multi", true);
            // multiType 取发起期固化值（BPMN 只有 isSequential 布尔，区分不出"并行会签"与"票签"）
            Object mtVar = runtimeService.getVariable(pinst, nodeKey + "_multiType");
            out.put("multiType", mtVar == null ? (vote ? "VOTE" : "ALL") : String.valueOf(mtVar));
            out.put("voteRatio", ratioVar == null ? null : (int) numberOr(ratioVar, 50L));
            out.put("total", total);
            out.put("approved", ack);
            out.put("rejected", rjk);
            out.put("pending", Math.max(0L, total - ack - rjk));
            out.put("passThreshold", total <= 0 || ratioVar == null ? null
                    : (long) Math.ceil((int) numberOr(ratioVar, 50L) * total / 100.0));
            // 逐票明细：同一实例 + 同一节点 key 的历史办理记录（含"谁投了什么"）。
            // 键名按各驱动的大小写差异统一办结，前端只认小驼峰。
            List<Map<String, Object>> raw = new JdbcTemplate(dataSource).queryForList(
                    "SELECT oper_user_id AS userId, oper_user_name AS userName, action, "
                            + "IFNULL(comment,'') AS comment, oper_time AS operTime "
                            + "FROM wf_task_history WHERE proc_inst_id = ? AND node_key = ? "
                            + "AND action IN ('APPROVE','REJECT') ORDER BY oper_time, id",
                    pinst, nodeKey);
            List<Map<String, Object>> votes = new ArrayList<>(raw == null ? 0 : raw.size());
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("userId", pickIgnoreCase(r, "userId"));
                    v.put("userName", pickIgnoreCase(r, "userName"));
                    v.put("action", pickIgnoreCase(r, "action"));
                    v.put("comment", pickIgnoreCase(r, "comment"));
                    Object t = pickIgnoreCase(r, "operTime");
                    v.put("operTime", t == null ? "" : String.valueOf(t));
                    votes.add(v);
                }
            }
            out.put("votes", votes);
            // 会签（ALL/SEQ）没有上面那两个引擎计票变量，若"已投 x/n"另走一条路算，就会和同屏的
            // 票列对不上：第二人进弹窗看到"已投 0/2"，票列里却已经有同伴的赞同票。
            // 记录即事实——非票签节点按记录计。
            // 票签(VOTE)仍读引擎变量：它是 BPMN completionCondition 的判定依据，两处互换会做出第二套真值。
            if (!engineCounted) {
                long votedAck = 0L;
                long votedRjk = 0L;
                for (Map<String, Object> v : votes) {
                    String action = String.valueOf(v.get("action"));
                    if ("REJECT".equals(action)) {
                        votedRjk++;
                    } else if ("APPROVE".equals(action)) {
                        votedAck++;
                    }
                }
                ack = votedAck;
                rjk = votedRjk;
                // 分母兜底：升级前发起的实例没有 _miTotal 快照，按实到票数办结，避免"已投 1/0"
                if (total <= 0) {
                    total = ack + rjk;
                    out.put("total", total);
                }
                out.put("approved", ack);
                out.put("rejected", rjk);
                out.put("pending", Math.max(0L, total - ack - rjk));
            }
        } catch (Exception ex) {
            log.warn("多实例进度查询失败 taskId={}: {}", taskId, ex.getMessage());
        }
        return out;
    }

    /**
     * BPMN 图上从当前节点出发的<b>下一批人工节点</b>（不穿过任何 userTask，网关/事件透明穿过）。
     * 返回 0 个=后面无人工节点（走向结束）；1 个=唯一确定，可强制点名；>1 个=条件/并行分支未办结，
     * 此刻无法预知引擎实际创建哪个任务，只能维持 AUTO（这也是点名变量按 nodeKey 分键存放的原因）。
     */
    public List<String> nextUserTaskKeys(String procDefId, String currentNodeKey) {
        try {
            if (procDefId == null || currentNodeKey == null) {
                return List.of();
            }
            Process p = mainProcess(procDefId);
            FlowElement cur = p == null ? null : p.getFlowElement(currentNodeKey);
            if (!(cur instanceof FlowNode)) {
                return List.of();
            }
            return userTasksBeyond(p, (FlowNode) cur);
        } catch (Exception ex) {
            log.warn("下一人工节点解析失败 procDef={} node={}: {}", procDefId, currentNodeKey, ex.getMessage());
            return List.of();
        }
    }

    private Process mainProcess(String procDefId) {
        BpmnModel model = procDefId == null ? null : repositoryService.getBpmnModel(procDefId);
        return model == null ? null : model.getMainProcess();
    }

    /** 从某节点出发（不穿过任何 userTask，网关/事件透明穿过）可达的人工节点键 */
    private List<String> userTasksBeyond(Process p, FlowNode from) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        ArrayDeque<FlowElement> queue = new ArrayDeque<>();
        for (SequenceFlow f : from.getOutgoingFlows()) {
            queue.add(p.getFlowElement(f.getTargetRef()));
        }
        for (int guard = 0; !queue.isEmpty() && guard < 200; guard++) {
            FlowElement el = queue.poll();
            if (el == null) {
                continue;
            }
            if (el instanceof UserTask) {
                found.add(el.getId());
                continue;
            }
            if (el instanceof FlowNode fn) {
                for (SequenceFlow f : fn.getOutgoingFlows()) {
                    queue.add(p.getFlowElement(f.getTargetRef()));
                }
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * 提单时"第一个真正要审的人"所在的节点键（方案）：从开始事件沿图走，穿过网关，
     * 命中人工节点即停。
     *
     * <p>还要再往后跳一步：这些流程的首节点是"提交XX单"这类自确认节点（提单即由
     * {@link #autoSkipSelfConfirmFirstNode} 自动完成，不产生人工待办），把它当成"下一环节"会让提单人
     * 去点名一个根本不需要人的节点。判定规则与自动跳过保持一致：显式属性
     * {@code wf_node_config.first_node_auto_pass} 为 1，或该列未配置时回退
     * 节点名含"提交"，或取人方式为 {@code INITIATOR}（发起人自审）。
     *
     * @return 0 个=流程无人工审批节点；1 个=落点唯一，可点名；>1 个=分支未办结，不可点名
     */
    public List<String> firstApprovalNodeKeys(String procDefId, String modelKey) {
        try {
            Process p = mainProcess(procDefId);
            if (p == null) {
                return List.of();
            }
            FlowNode start = null;
            for (FlowElement el : p.getFlowElements()) {
                if (el instanceof StartEvent) {
                    start = (FlowNode) el;
                    break;
                }
            }
            if (start == null) {
                return List.of();
            }
            List<String> keys = userTasksBeyond(p, start);
            if (keys.size() != 1) {
                return keys;
            }
            String first = keys.get(0);
            FlowElement el = p.getFlowElement(first);
            WfNodeConfig cfg = modelKey == null ? null : wfNodeConfigResolver.resolve(modelKey, first);
            // 与 autoSkipSelfConfirmFirstNode 走同一个判断方法（只看显式属性，名称不参与）。
            // 两处必须同源，否则"点名的下一环节"与实际"自动跳过的首节点"会各算各的。
            boolean submitNode = isFirstNodeAutoPass(cfg);
            boolean selfConfirm = submitNode
                    || (cfg != null && "INITIATOR".equals(cfg.getAssignType()));
            if (!selfConfirm || !(el instanceof FlowNode)) {
                return keys;
            }
            List<String> beyond = userTasksBeyond(p, (FlowNode) el);
            return beyond.isEmpty() ? keys : beyond;
        } catch (Exception ex) {
            log.warn("首审节点解析失败 procDef={}: {}", procDefId, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 审批入口的点名硬约束：下一节点唯一且为 PICK 档时——
     * 无候选 → 阻断并提示补岗；未点名 → 阻断并提示需指定；点名不在候选集 → 阻断（防绕开下拉越权指派）。
     * 校验放在 {@code taskService.complete} <b>之前</b>：本门面没有事务包裹，complete 之后才报错会留下
     * "审批已生效但用户看到失败"的双态。
     */
    private void enforcePickOnApprove(Task task, WfInstance inst,
                                      Map<String, Object> vars, Long nextAssigneeUserId, Long operator) {
        if (inst == null || !isPickModeEnabled(inst.getModelKey())) {
            return;
        }
        List<String> next = nextUserTaskKeys(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (next.size() != 1) {
            return;
        }
        String nodeKey = next.get(0);
        WfNodeConfig cfg = wfNodeConfigResolver.resolve(inst.getModelKey(), nodeKey);
        if (!isPickNode(inst.getModelKey(), cfg)) {
            return;
        }
        String nodeName = cfg.getNodeName() == null || cfg.getNodeName().isBlank() ? nodeKey : cfg.getNodeName();
        List<Long> cands = pickCandidates(inst.getModelKey(), cfg, task.getProcessInstanceId());
        if (cands.isEmpty()) {
            // 零候选不等于必须在这里卡住：审批节点的办结规则由 empty_strategy 决定
            // （ESCALATE 升级 / SKIP 跳过），而这两档只有在任务被创建之后才由三级解析执行得到——
            // 在此早退让 complete 继续，下一节点任务落地后再按档处置。
            // 只有 ERROR 档（作业节点「请先补岗」）维持审批入口硬拦：早晚报都是同一个 400，早报省一次无效提交。
            String strategy = cfg.getEmptyStrategy() == null || cfg.getEmptyStrategy().isBlank()
                    ? "ERROR" : cfg.getEmptyStrategy().trim().toUpperCase();
            if ("ERROR".equals(strategy)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "下一节点「" + nodeName
                        + "」当前无可选办理人（节点角色在本单据所属公司下没有在职非管理员成员），请先在角色授权中补岗");
            }
            log.info("PICK 节点「{}」零候选，empty_strategy={} —— 交由节点三级解析收尾，不在审批入口阻断",
                    nodeName, strategy);
            return;
        }
        if (nextAssigneeUserId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "下一节点「" + nodeName + "」需指定办理人（"
                    + cands.size() + " 人可选）");
        }
        if (!cands.contains(nextAssigneeUserId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "指定的办理人不在「" + nodeName + "」节点的可办理范围内");
        }
        vars.put(PICK_VAR_PREFIX + nodeKey, nextAssigneeUserId);
        vars.put(PICK_BY_PREFIX + nodeKey, operator);
    }

    /**
     * 提单入口的点名硬约束（方案，与 {@link #enforcePickOnApprove} 同一规则）：首审节点唯一且为
     * PICK 档才接受点名，点名对象必须落在本单据公司范围下的候选集内。
     *
     * <p>拒绝而非忽略：忽略等于"用户以为自己选了人，实际单流向了他没见过的人"。唯一不拒绝的情形是
     * 灰度总开关关闭——那个档位本就不接受点名，此时告警后丢弃。
     */
    private void enforcePickOnStart(String bizType, Long bizId, String processKey,
                                    Map<String, Object> vars, Long picked) {
        if (picked == null) {
            return;
        }
        if (!isPickModeEnabled(processKey)) {
            log.warn("PICK 灰度未放行 {}，忽略提单点名 user={}", processKey, picked);
            return;
        }
        Long operator = UserContext.get() == null
                ? null : UserContext.get().getUserId();
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "提单指定办理人需登录态，机器发起的流程请按节点配置自动取人");
        }
        String nodeKey = soleStartApprovalNode(processKey);
        WfNodeConfig cfg = wfNodeConfigResolver.resolve(processKey, nodeKey);
        String nodeName = cfg == null || cfg.getNodeName() == null || cfg.getNodeName().isBlank()
                ? nodeKey : cfg.getNodeName();
        if (!isPickNode(processKey, cfg)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "首审环节「" + nodeName + "」按节点配置自动取人，不支持提单时指定办理人");
        }
        List<Long> cands = candidatesInScope(cfg, nodeOrgResolver.companyIdOfBill(bizType, bizId), operator);
        if (cands.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首审环节「" + nodeName
                    + "」当前无可选办理人（节点角色在本单据所属公司下没有在职非管理员成员），请先在角色授权中补岗");
        }
        if (!cands.contains(picked)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "指定的办理人不在「" + nodeName + "」环节的可办理范围内");
        }
        vars.put(PICK_VAR_PREFIX + nodeKey, picked);
        vars.put(PICK_BY_PREFIX + nodeKey, operator);
    }

    /** 首审节点定位：唯一才返回键，否则抛错说明原因（多分支/无人工节点都无法点名） */
    private String soleStartApprovalNode(String processKey) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey).latestVersion().singleResult();
        if (def == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程定义尚未部署，请联系管理员");
        }
        List<String> keys = firstApprovalNodeKeys(def.getId(), processKey);
        if (keys.size() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, keys.isEmpty()
                    ? "该流程无人工审批节点，无需指定办理人"
                    : "首审环节存在多个分支，无法在提单时指定办理人（请在审批时逐环节点名）");
        }
        return keys.get(0);
    }

    /**
     * 供提单页展示"第一道审批给谁"（方案 方案 B：配置驱动）：首审节点为 PICK 档才要求选人，
     * POOL 档只提示"任务池抢单"，其余（含 AUTO/USER/INITIATOR/分支未办结）返回 mode=AUTO 不渲染下拉。
     *
     * <p>与 {@link #nextCandidates} 的差别只有组织上下文来源：此刻还没有流程实例，公司从草稿单据
     * （{@code bizType}+{@code bizId}）解析、直线上级从提单人解析，其余候选人规则完全同源。
     */
    public Map<String, Object> startCandidates(String bizType, Long bizId, String subType,
                                               BigDecimal amount, Long operator) {
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "AUTO");
        out.put("nodeKey", null);
        out.put("nodeName", null);
        out.put("required", false);
        out.put("candidates", List.of());
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法查看提单可选办理人");
        }
        String modelKey = resolveStartProcessKey(bizType, bizId, subType, amount);
        out.put("modelKey", modelKey);
        out.put("pickEnabled", isPickModeEnabled(modelKey));
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(modelKey).latestVersion().singleResult();
        if (def == null) {
            out.put("nodeName", "（流程定义未部署，按默认流程启动）");
            return out;
        }
        List<String> keys = firstApprovalNodeKeys(def.getId(), modelKey);
        if (keys.size() != 1) {
            out.put("nodeName", keys.isEmpty() ? "（该流程无人工审批节点）" : "（首审存在多个分支，按节点配置自动取人）");
            return out;
        }
        String nodeKey = keys.get(0);
        WfNodeConfig cfg = wfNodeConfigResolver.resolve(modelKey, nodeKey);
        out.put("nodeKey", nodeKey);
        out.put("nodeName", cfg == null || cfg.getNodeName() == null || cfg.getNodeName().isBlank()
                ? nodeKey : cfg.getNodeName());
        boolean role = cfg != null && "ROLE".equals(cfg.getAssignType()) && !trim(cfg.getAssignValue()).isBlank();
        boolean pick = role && isPickNode(modelKey, cfg);
        boolean pool = role && "POOL".equalsIgnoreCase(trim(cfg.getHandlingMode()));
        out.put("mode", pick ? "PICK" : (pool ? "POOL" : "AUTO"));
        // 同族（提单态）：首审环节本身就是会签/票签时，"这一环节交给谁"的答案是<b>全体候选</b>
        // ——引擎按 <nodeKey>_assignees 逐人生成待办。且多签节点常配成 assign_type=USER（点名式会签），
        // 会被下面的 !role 早退成 0 候选，弹窗上"下一环节"整块空着。取人算法与派发同一个
        // {@link #resolveMiAssignees}，不新起规则；POOL 的 deny_self 豁免走的是另一支，不受这里影响。
        if (isMultiInstanceNode(cfg)) {
            Long miCompany = null;
            try {
                miCompany = nodeOrgResolver.companyIdOfBill(bizType, bizId);
            } catch (Exception ex) {
                log.warn("提单态会签候选的组织口径解析失败 bizType={} bizId={}: {}", bizType, bizId, ex.getMessage());
            }
            List<Long> mi = new ArrayList<>();
            for (String s : resolveMiAssignees(cfg, miCompany, operator)) {
                Long id = toUserIdOrNull(s);
                if (id != null && !mi.contains(id)) {
                    mi.add(id);
                }
            }
            out.put("multiInstance", true);
            out.put("multiType", cfg.getMultiType().trim().toUpperCase(Locale.ROOT));
            out.put("candidates", describeUsers(mi));
            return out;
        }
        out.put("multiInstance", false);
        if (!role) {
            return out;
        }
        Long company = nodeOrgResolver.companyIdOfBill(bizType, bizId);
        List<Long> ids = (pick || pool)
                ? candidatesInScope(cfg, company, operator)
                : firstHolderInScope(cfg, company, operator);
        out.put("candidates", describeUsers(ids));
        // required 与 enforcePickOnApprove / enforcePickOnStart 同一规则：PICK 档只要有人可点就必须点名。
        // 原写 ids.size() > 1，于是"本公司只有 1 个部门经理"时前端不标必填、提交却被服务端 400，直接卡单。
        out.put("required", pick && !ids.isEmpty());
        return out;
    }

    /** 提单态 AUTO 档的落点：与真正取人一致，取节点首个角色的第一个持有人 */
    private List<Long> firstHolderInScope(WfNodeConfig cfg, Long companyId, Long applicant) {
        String firstRole = trim(cfg.getAssignValue()).split(",")[0].trim();
        List<Long> all = nodeOrgResolver.listRoleCandidatesInScope(companyId, applicant, firstRole);
        return all.isEmpty() ? List.of() : List.of(all.get(0));
    }

    /**
     * 消费上一环节点名结果：命中则返回被点名用户并写 ASSIGN 记录；非 PICK 档/未点名/点名失效返回 null
     * 由调用方退回 AUTO 取人。
     */
    private Long consumePickedAssignee(String processKey, WfNodeConfig cfg, Task task) {
        if (!isPickNode(processKey, cfg)) {
            return null;
        }
        String nodeKey = task.getTaskDefinitionKey();
        Object rawAssignee;
        Object rawPicker;
        try {
            Map<String, Object> all = taskService.getVariables(task.getId());
            rawAssignee = all.get(PICK_VAR_PREFIX + nodeKey);
            rawPicker = all.get(PICK_BY_PREFIX + nodeKey);
        } catch (Exception ex) {
            log.warn("读取点名变量失败 task={}: {}", task.getId(), ex.getMessage());
            return null;
        }
        Long picked = toLong(rawAssignee);
        if (picked == null) {
            log.warn("PICK 节点未收到点名，退回 AUTO 取人 model={} node={} task={}", processKey, nodeKey, task.getId());
            return null;
        }
        List<Long> cands = pickCandidates(processKey, cfg, task.getProcessInstanceId());
        if (!cands.contains(picked)) {
            log.warn("PICK 点名对象已不在候选集（人员异动/公司变更），退回 AUTO 取人 node={} user={} task={}",
                    nodeKey, picked, task.getId());
            return null;
        }
        writeHistory(task.getProcessInstanceId(), task.getId(), nodeKey, cfg.getNodeName(),
                "ASSIGN", toLong(rawPicker), picked, "上一环节指定办理人");
        return picked;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 供审批弹窗展示"下一步给谁"：返回下一人工节点的取人方式与候选人（含姓名、部门、当前在办量）。
     *
     * <p>PICK 档只要有候选就 {@code required=true}（与 {@code enforcePickOnApprove} 同一规则，
     * 哪怕只有一人也必须点名，否则前端不标必填、提交被服务端 400 卡单）；POOL 档 {@code required=false}
     * 但候选人=整个池，提示"下一环节进任务池抢单"。分支未办结（下一节点不唯一）时 mode 回落 AUTO
     * 并给出各分支线索，不阻塞审批。
     *
     * <p>候选人名单含他人姓名与在办量，属越权信息，故校验与 {@code complete} 同一规则（{@code canOperate}）：
     * 非本任务办理人/候选人/生效受托人直接拒绝。
     */
    public Map<String, Object> nextCandidates(String taskId, Long operator) {
        Map<String, Object> out = new HashMap<>();
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法查看此任务的候选人");
        }
        if (!nodeOrgResolver.canOperate(task, operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, "非当前节点办理人/候选人，无权查看此任务候选人");
        }
        WfInstance inst = wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
        String modelKey = inst == null ? null : inst.getModelKey();
        out.put("currentNodeName", task.getName());
        out.put("pickEnabled", isPickModeEnabled(modelKey));
        List<String> next = nextUserTaskKeys(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (next.size() != 1) {
            out.put("nodeKey", null);
            out.put("nodeName", next.isEmpty() ? "（流程即将结束）" : "（分支尚未办结，按节点配置自动取人）");
            out.put("mode", "AUTO");
            out.put("required", false);
            out.put("candidates", List.of());
            return out;
        }
        String nodeKey = next.get(0);
        WfNodeConfig cfg = modelKey == null ? null : wfNodeConfigResolver.resolve(modelKey, nodeKey);
        out.put("nodeKey", nodeKey);
        out.put("nodeName", cfg == null || cfg.getNodeName() == null ? nodeKey : cfg.getNodeName());
        boolean pick = isPickNode(modelKey, cfg);
        // POOL 与 startCandidates 同一规则：任务池要如实报"池里有几个人可领"。只分 pick/非 pick
        // 两支的话，POOL 节点会落到 AUTO 档报成 1 个人，弹窗上看不出这是抢单节点。
        boolean pool = cfg != null && "ROLE".equals(cfg.getAssignType())
                && "POOL".equalsIgnoreCase(trim(cfg.getHandlingMode()));
        out.put("mode", pick ? "PICK" : (pool ? "POOL" : "AUTO"));
        List<Long> ids = previewCandidates(modelKey, cfg, task.getProcessInstanceId(), pick, pool);
        out.put("candidates", describeUsers(ids));
        // 会签/票签下一环节是"每人一条待办"，不是"流转给其中一个人"。
        // 把多签类型一并下发，前端据此说破"几人各生成一条"，而不是照单人选节点那样报"将流转给 X"。
        String miType = cfg == null || cfg.getMultiType() == null ? null : cfg.getMultiType().trim().toUpperCase();
        boolean mi = isMultiInstanceNode(cfg);
        out.put("multiInstance", mi);
        out.put("multiType", mi ? miType : null);
        // required 与 enforcePickOnApprove / enforcePickOnStart 同一规则：PICK 档只要有人可点就必须点名。
        // 原写 ids.size() > 1，于是"本公司只有 1 个部门经理"时前端不标必填、提交却被服务端 400，直接卡单。
        out.put("required", pick && !ids.isEmpty());
        return out;
    }

    /** 节点是否为多实例（会签 ALL / 顺序会签 SEQ / 票签 VOTE）：取值域与 {@link #injectMultiInstanceVars} 逐字一致 */
    private static boolean isMultiInstanceNode(WfNodeConfig cfg) {
        if (cfg == null || cfg.getMultiType() == null) {
            return false;
        }
        String mt = cfg.getMultiType().trim().toUpperCase(Locale.ROOT);
        return "ALL".equals(mt) || "SEQ".equals(mt) || "VOTE".equals(mt);
    }

    /**
     * 下一环节预览的候选人（预览与派发<b>同源</b>）。
     *
     * <p>多签节点不能走 AUTO 档的 {@link #firstHolderOnly}：那里只取节点角色<b>第一个</b>持有人，
     * 而引擎按 {@code <nodeKey>_assignees} 集合<b>逐人</b>生成待办，预览就会说"转给 1 个人"、
     * 实际生成好几条任务。ROLE 展开更糟：{@code assign_type=USER} 的会签节点会被那条
     * {@code !"ROLE".equals(assignType)} 的早退判成 0 候选，弹窗上"下一环节"整块空着，
     * 审批人以为没人会收到这张单。
     *
     * <p>所以多签节点优先读启动期注入的流程变量（那正是 Flowable 派发用的集合，读它＝与实发
     * 待办同源）；变量拿不到（老实例／键改过／非本引擎发起）时退回 {@link #resolveMiAssignees}
     * ——与注入时<b>同一个方法</b>重算，不新起一份候选算法。
     * PICK/POOL/AUTO 三档的非多签节点走原路径（POOL 的候选规则另有「POOL 不走 deny_self」一条，
     * 那一支不动）。
     */
    private List<Long> previewCandidates(String modelKey, WfNodeConfig cfg, String procInstId,
                                         boolean pick, boolean pool) {
        if (cfg == null) {
            return List.of();
        }
        if (isMultiInstanceNode(cfg)) {
            return miPreviewCandidates(cfg, procInstId);
        }
        if (trim(cfg.getAssignValue()).isBlank() || !"ROLE".equals(cfg.getAssignType())) {
            return List.of();
        }
        return (pick || pool) ? pickCandidates(modelKey, cfg, procInstId)
                : firstHolderOnly(modelKey, cfg, procInstId);
    }

    /**
     * 多实例节点的候选全集：先读派发真正使用的 {@code <nodeKey>_assignees} 变量，缺失才按同一算法重算。
     * 变量本身是字符串列表（Flowable 逐条塞给 assignee 表达式），解析失败即回落重算，绝不返回空集糊弄前端。
     */
    private List<Long> miPreviewCandidates(WfNodeConfig cfg, String procInstId) {
        try {
            Object raw = runtimeService.getVariable(procInstId, cfg.getNodeKey() + "_assignees");
            if (raw instanceof Collection<?> col && !col.isEmpty()) {
                List<Long> ids = new ArrayList<>();
                for (Object o : col) {
                    Long id = toUserIdOrNull(o == null ? null : String.valueOf(o));
                    if (id != null && !ids.contains(id)) {
                        ids.add(id);
                    }
                }
                if (!ids.isEmpty()) {
                    return ids;
                }
            }
        } catch (Exception ex) {
            log.warn("多实例候选变量读取失败，退回按派发算法重算 node={} proc={}: {}",
                    cfg.getNodeKey(), procInstId, ex.getMessage());
        }
        Long company = null;
        Long applicant = null;
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst != null) {
                company = nodeOrgResolver.companyIdOfBill(inst.getBizType(), inst.getBizId());
                applicant = inst.getStartUserId();
            }
        } catch (Exception ex) {
            log.warn("多实例候选重算的组织口径解析失败 proc={}: {}", procInstId, ex.getMessage());
        }
        List<Long> ids = new ArrayList<>();
        for (String s : resolveMiAssignees(cfg, company, applicant)) {
            Long id = toUserIdOrNull(s);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** AUTO 档给"实际会流到谁"这一个答案（与真正取人同一规则），避免弹窗显示一长串其实无意义的人 */
    private List<Long> firstHolderOnly(String modelKey, WfNodeConfig cfg, String procInstId) {
        Long only = nodeOrgResolver.tryResolveAssignee(procInstId, cfg.getAssignValue().split(",")[0].trim());
        return only == null ? List.of() : List.of(only);
    }

    /** 用户 ID → 展示对象（姓名/工号/部门/在办量），在办量只在候选不多时统计，避免大批量下 N+1 */
    private List<Map<String, Object>> describeUsers(List<Long> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        Map<Long, SysUser> users = new HashMap<>();
        try {
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                users.put(u.getId(), u);
            }
        } catch (Exception ex) {
            log.warn("候选人姓名查询失败: {}", ex.getMessage());
        }
        boolean withLoad = ids.size() <= 20;
        for (Long id : ids) {
            SysUser u = users.get(id);
            if (u == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            String nm = u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName();
            m.put("userId", id);
            m.put("name", u.getEmpNo() == null || u.getEmpNo().isBlank() ? nm : nm + "（" + u.getEmpNo() + "）");
            m.put("deptName", u.getDeptName());
            if (withLoad) {
                try {
                    m.put("pendingCount", taskService.createTaskQuery().taskAssignee(String.valueOf(id)).count());
                } catch (Exception ex) {
                    // 在办数查询失败置 null（统计展示字段，不阻断主流程）
                    m.put("pendingCount", null);
                }
            }
            out.add(m);
        }
        return out;
    }

    /**
     * 空策略兜底记录：SKIP 自动跳过、ESCALATE 系统升级换人都是"系统代替人做的决定"，
     * 必须落进 {@code wf_task_history} 才对得上账——否则单据静默换人/跳节点，事后无从追溯是谁放的行。
     * ERROR（抛异常阻断）与 ADMIN 由各自的异常信息/告警日志暴露，此处不重复记。
     */
    private void recordEmptyStrategy(Task task, String action) {
        if (task == null || (!"SKIP".equals(action) && !"ESCALATE".equals(action))) {
            return;
        }
        try {
            Long target = null;
            if ("ESCALATE".equals(action)) {
                Task live = taskService.createTaskQuery().taskId(task.getId()).singleResult();
                target = live == null ? null : toUserIdOrNull(live.getAssignee());
            }
            writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(), task.getName(),
                    "SKIP".equals(action) ? "AUTO_SKIP" : "ESCALATE", null, target,
                    "SKIP".equals(action)
                            ? "空策略兜底：本节点在该单据所属公司内无可办理人，按节点配置自动跳过（仅知会/消息类节点允许此档）"
                            : "空策略兜底：本节点候选角色无可办理的持有人（含自审回避，提单人本人被排除），沿汇报线升级给真实负责人办理（绝不落 admin）");
        } catch (Exception ex) {
            log.warn("空策略兜底留痕失败 task={} action={}: {}", task.getId(), action, ex.getMessage());
        }
    }

    /**
     * 节点办理人三级解析统一入口：①节点配置覆盖 → ②组织解析 → ③空策略兜底。
     *
     * <p>少了第③级，"某公司在节点角色下无成员"时任务既无 assignee 也无合法办理人，单据会静默
     * 永久滞留（典型：BASE_FIN_MANAGER 基地财务经理只对集团总部铺开，子公司对公付款单必然卡在
     * 该节点）。③ 由 {@link NodeOrgResolver#applyEmptyStrategy} 提供，按
     * {@code wf_node_config.empty_strategy} 处置为 ADMIN 转管理员 / SKIP 自动跳过 / ERROR 报错阻断。
     *
     * <p>SKIP 会把当前任务直接完成并产生新任务，故此处循环续跑，最多 8 跳防止配置成环空转。
     *
     * @param processKey 流程模型 key（wf_model.model_key）
     * @param task 刚创建的人工任务（首节点或 complete 后的下一节点）
     */
    private void assignApprover(String processKey, Task task) {
        Task cur = task;
        for (int hop = 0; hop < 8 && cur != null; hop++) {
            applyNodeConfig(processKey, cur);
            // 重查任务，让第②③级看到第①级 USER 配置刚设的 assignee：拿旧快照的话，
            // ADMIN 兜底会把显式指派覆盖掉。
            if (cur.getId() != null) {
                Task freshTask = taskService.createTaskQuery().taskId(cur.getId()).singleResult();
                if (freshTask == null) {
                    cur = null;
                    break;
                }
                cur = freshTask;
            }
            nodeOrgResolver.applyResolution(cur);
            String action = nodeOrgResolver.applyEmptyStrategy(processKey, cur);
            recordEmptyStrategy(cur, action);
            if (!"SKIP".equals(action)) {
                break;
            }
            // SKIP 后实例里可能同时留着并联兄弟任务（已各自解析补账过），只续跑真正的新任务
            cur = null;
            for (Task t : openTasks(task.getProcessInstanceId())) {
                if (!wfQueryService.pendingTaskExists(t.getId())) {
                    cur = t;
                    break;
                }
            }
        }
        // 双账补账：wf_task 若只在 start() 记首节点，第 2 个节点起引擎有任务、视图表没行，
        // 所有「按 wf_task PENDING 扫描」的链路就对后续节点全部失效——节点级超时（WfTimeoutJob
        // 读 model_key+node_key+create_time）、我的待办 myTodo、看板的当前办理人（fillCurrentHandlers
        // 读 candidate_ids）都只剩首节点可见。所以在三级解析收尾后统一补记真实落点。
        if (cur != null && cur.getId() != null) {
            // 必须重查再记账：empty_strategy 的 ESCALATE 是在这轮里 setAssignee 的，手头的 cur 是设派之前的快照，
            // 直接记会让 wf_task 镜像的 assignee_id 留空——按镜像跑的超时作业/看板当前办理人就看不到升级后的真人。
            // 镜像开关打开时停写 wf_task，改由监听器 TASK_CREATED 投影建行。
            if (mirrorByFacade()) {
                Task mirrored = taskService.createTaskQuery().taskId(cur.getId()).singleResult();
                recordTaskMirror(processKey, mirrored != null ? mirrored : cur);
            }
        }
    }

    /**
     * 待办视图补账：为 complete 推进出来的<b>非首节点</b>人工任务写 wf_task PENDING 行。
     *
     * <p>字段含义与 {@code start()} 首节点那条保持一致（同一张表两套写法会漂移）：单据信息一律从
     * {@code wf_instance} 视图取，不再读流程变量（历史变量会被清理）；{@code candidate_ids} 只在
     * <b>未被指派</b>时写（POOL/候选组态），格式 {@code ,1,2,3,} 与 {@code myTodo} 的 LIKE 匹配同源。
     *
     * <p>去重：{@code uk_tenant_task_id} 唯一键会拒绝重复行，故先查 PENDING 是否存在；重开/回跳
     * 生成的新 taskId 不冲突。异常只记 error 不影响审批事务（与既有双写风格一致）。
     */
    private void recordTaskMirror(String processKey, Task task) {
        try {
            if (task == null || task.getId() == null) {
                return;
            }
            if (wfQueryService.pendingTaskExists(task.getId())) {
                // 监听器投影会先建 assignee 为空的镜像行；三级解析（empty_strategy ESCALATE）补派出
                // 真人后必须回写 assignee，否则待办/看板/超时作业全部按镜像找人——单子在真人手里，
                // 却不在任何人的待办里。
                WfTask existing = wfQueryService.pendingTaskByTaskId(task.getId());
                Long assigneeLate = toUserIdOrNull(task.getAssignee());
                if (existing != null && existing.getAssigneeId() == null && assigneeLate != null) {
                    wfQueryService.updateTaskAssignee(task.getId(), assigneeLate);
                    log.info("待办视图补派回写: task={} node={} assignee={}",
                            task.getId(), task.getTaskDefinitionKey(), assigneeLate);
                }
                return;
            }
            WfInstance inst =
                    wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
            if (inst == null || inst.getBizType() == null) {
                // biz_type 为 NOT NULL：实例视图尚未写入数据库时无法补齐单据归属，交给 start() 那条链路负责
                log.warn("待办视图补账跳过（实例视图缺失）proc={} task={}",
                        task.getProcessInstanceId(), task.getId());
                return;
            }
            LocalDateTime createAt = task.getCreateTime() == null ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(task.getCreateTime().toInstant(),
                            ZoneId.systemDefault());
            WfTask t = new WfTask();
            t.setTenantId(inst.getTenantId() != null ? inst.getTenantId() : TenantContext.getTenantId());
            t.setTaskId(task.getId());
            t.setProcInstId(task.getProcessInstanceId());
            t.setModelKey(inst.getModelKey() != null ? inst.getModelKey() : processKey);
            t.setNodeKey(task.getTaskDefinitionKey());
            t.setNodeName(task.getName());
            t.setBizType(inst.getBizType());
            t.setBizId(inst.getBizId());
            t.setBizNo(inst.getBizNo());
            t.setTitle(inst.getTitle());
            t.setAmount(inst.getAmount());
            t.setStartUserId(inst.getStartUserId());
            t.setCreateTime(createAt);
            t.setStatus("PENDING");
            Long assignee = toUserIdOrNull(task.getAssignee());
            t.setAssigneeId(assignee);
            if (assignee == null) {
                List<Long> operable = nodeOrgResolver.listOperableUserIds(task);
                if (operable != null && !operable.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    // candidate_ids 列宽 varchar(1000)：COMMON_EMP 这类大角色有 151 个持有人，
                    // 全量拼接会溢出导致整条镜像插不进去（表现＝该节点又没有待办行）。
                    // 按解析顺序截到列宽以内；被截掉的人仍能从审批工作台（Flowable 候选组）看到这张单。
                    for (Long uid : operable) {
                        String next = "," + uid;
                        if (sb.length() + next.length() + 1 > 990) {
                            log.debug("待办视图候选人列按列宽截断 task={} 总数={}", task.getId(), operable.size());
                            break;
                        }
                        sb.append(next);
                    }
                    t.setCandidateIds(sb.append(',').toString());
                }
            }
            // 到期时间：节点配置 timeoutHours 的写入数据库规则，供超时扫描与看板"已逾期"高亮复用
            try {
                WfNodeConfig cfg = wfNodeConfigResolver.resolve(t.getModelKey(), task.getTaskDefinitionKey());
                if (cfg != null && cfg.getTimeoutHours() != null && cfg.getTimeoutHours() > 0) {
                    t.setDueTime(createAt.plusHours(cfg.getTimeoutHours()));
                }
            } catch (Exception ex) {
                log.debug("补账到期时间解析失败 task={}", task.getId());
            }
            wfQueryService.recordTask(t);
            log.info("待办视图补账: task={} node={} assignee={} candidates={}",
                    task.getId(), task.getTaskDefinitionKey(), assignee, t.getCandidateIds());
        } catch (Exception ex) {
            // 补账失败不能静默吞掉：先记缺陷（错误码 + 一条 sys_oper_log），
            // 再原样冒泡——引擎侧任务已经存在，镜像缺行意味着"单子在真人手里却不在任何人待办里"，
            // 这种漂移必须让调用方（start / assignApprovers）与界面看得见。
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED, "recordTaskMirror",
                    null, "proc=" + (task == null ? null : task.getProcessInstanceId())
                            + " task=" + (task == null ? null : task.getId()), ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "待办视图补账失败: " + ex.getMessage());
        }
    }

    /**
     * 流程变量值 → 金额（/124 镜像列兜底用）。取不到数值返回 {@code null}＝保持列空，
     * 不写 0——0 在金额分档流程里是"金额=0 档"的真实语义，混进镜像就是把缺值说成小额。
     */
    private static BigDecimal toAmountOrNull(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /** Flowable 的 assignee 是字符串用户 ID；非数字（角色码等）返回 null */
    private static Long toUserIdOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 审批：complete 当前任务，approved 决定流向（true→通过结束，false→驳回结束）。
     */
    public void complete(String taskId, boolean approved, String comment, Long operUserId) {
        complete(taskId, approved, comment, rejectTypeOf(approved, comment), operUserId);
    }

    /**
     * 审批 + 下一环节点名（PICK 档）。rejectType 仍按老规则推断，调用方无需感知。
     */
    public void complete(String taskId, boolean approved, String comment, Long operUserId, Long nextAssigneeUserId) {
        complete(taskId, approved, comment, rejectTypeOf(approved, comment), operUserId, nextAssigneeUserId);
    }

    /**
     * 审批 + 下一环节点名 + 运行时抄送（传阅）。rejectType 仍按老规则推断。
     *
     * <p>与 {@code (String,boolean,String,String,Long,Long)} 重载仅第 4/6 个参数类型不同
     * Long/String 对调。正常调用传入具体类型而非 {@code null} 字面量，不构成歧义。
     */
    public void complete(String taskId, boolean approved, String comment, Long operUserId,
                         Long nextAssigneeUserId, String ccUserIds) {
        complete(taskId, approved, comment, rejectTypeOf(approved, comment), operUserId,
                nextAssigneeUserId, ccUserIds);
    }

    /**
     * 审批 + 点名 + 抄送 + 自由驳回（rejectType 仍按老规则推断，调用方无需感知）。
     * 与 {@code (String,boolean,String,String,Long,Long,String)} 的第 4 参类型不同（Long/String），
     * 重载解析按实参类型精确匹配，不构成歧义。
     */
    public void complete(String taskId, boolean approved, String comment, Long operUserId,
                         Long nextAssigneeUserId, String ccUserIds, String rejectToNodeKey) {
        complete(taskId, approved, comment, rejectTypeOf(approved, comment), operUserId,
                nextAssigneeUserId, ccUserIds, rejectToNodeKey);
    }

    /**
     * 按业务单据审批：bizType + bizId → 查找活跃任务 → complete。
     * 列表页通用审批入口，不需要调用方感知 taskId。
     *
     * <p>并联会审网关（例如 INVOICE_MATCH 里成本费用岗与资产核算岗并行）会让同一 bizId 同时挂着
     * 两条在办任务。用 {@code singleResult()} 查就会抛 {@code Query return 2 results instead of max 1}
     * 包成 500。所以这里取列表，只办结操作人有权的那一条；分辨不出就明确拒办并提示走待办逐条
     * 办理，绝不代替用户挑一条办掉——会签的本意就是各岗位各自签核。
     */
    public void completeByBill(String bizType, Long bizId, boolean approved, String comment, Long operUserId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processVariableValueEquals("bizType", bizType)
                .processVariableValueEquals("bizId", bizId)
                .active()
                .list();
        if (tasks.isEmpty()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "未找到待审批任务: bizType=" + bizType + ", bizId=" + bizId);
        }
        Task task;
        if (tasks.size() == 1) {
            task = tasks.get(0);
        } else {
            Long operator = operUserId != null ? operUserId
                    : (UserContext.get() != null
                            ? UserContext.get().getUserId() : null);
            task = null;
            if (operator != null) {
                for (Task t : tasks) {
                    if (nodeOrgResolver.canOperate(t, operator)) { task = t; break; }
                }
            }
            if (task == null) {
                StringJoiner nodes = new StringJoiner("、");
                for (Task t : tasks) nodes.add(t.getName());
                throw new BizException(
                        ErrorCode.PARAM_INVALID,
                        "该单据当前有 " + tasks.size() + " 个并联节点同时在办（" + nodes + "），请在待办中逐条办理");
            }
        }
        complete(task.getId(), approved, comment, operUserId);
    }

    /** 取消僵尸流程实例（仅测试/运维清理用；PROCESS_CANCELLED 不触发业务收尾） */
    public void cancelInstance(String procInstId, String reason) {
        if (runtimeService.createProcessInstanceQuery().processInstanceId(procInstId).singleResult() == null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "实例不存在或已结束: " + procInstId);
        }
        runtimeService.deleteProcessInstance(procInstId, reason);
        try {
            wfQueryService.updateInstanceStatus(procInstId, "CANCELLED", LocalDateTime.now());
            // 幽灵待办办结：清理后 wf_task 的 PENDING 行必须同步关闭，否则会一直残留在待办里。
            // 与下面那行实例级 CANCEL 记录配对——那行只说明"实例被谁撤销"，
            // 这一句说明"撤销时挂在哪几个节点上的任务一起作废"。
            wfQueryService.closePendingTasksByInstance(procInstId,
                    "流程实例被撤销，本节任务未办理即作废" + (reason == null || reason.isBlank() ? "" : "（撤销事由：" + reason + "）"));
            // 撤销/终止必须自己落一条历史：只改视图状态不记录的话，时间线看不出单子死于何时何人之手
            writeHistory(procInstId, null, null, null, "CANCEL", approverUserIdOrDefault(), reason);
        } catch (Exception ex) {
            log.warn("僵尸实例清理后视图状态回写失败 {}: {}", procInstId, ex.getMessage());
        }
    }

    /**
     * 最终状态收尾（复用 「直改状态型单据收尾须终止流程」规则）：按 biz_type+biz_id
     * 终止该业务单据名下所有 RUNNING 流程实例——逐实例复用 {@link #cancelInstance}（引擎实例删除＋
     * wf_instance 置 CANCELLED＋wf_task 的 PENDING 置 CANCELLED＋双记录）。作废/终止等只改业务状态
     * 的收尾入口必须调用本方法，否则待办镜像滞留 PENDING，催办扫描会持续对空单空转。
     *
     * <p>去重与容错：该单没有 RUNNING 实例（未起流程/已终结）返回 0 不抛错，属正常；引擎侧实例
     * 已被"裸作废"终结而视图仍 RUNNING 时（PayPlanService 同族），只吞
     * 「实例不存在或已结束」这一种 PARAM_INVALID 并补视图办结；其余异常一律冒泡——流程收尾失败
     * 不得静默，调用方事务整体回滚、业务状态还原。
     *
     * @return 被办结的 RUNNING 实例数（0＝该单无在办流程）
     */
    public int cancelRunningInstancesByBiz(String bizType, Long bizId, String reason) {
        List<WfInstance> instances = wfQueryService.instancesByBiz(bizType, bizId);
        int cancelled = 0;
        for (WfInstance inst : instances) {
            // 只收在办实例：已终结轮次（驳回重提的历史实例、正常办结轮次）不动
            if (!"RUNNING".equalsIgnoreCase(inst.getStatus() == null ? "" : inst.getStatus().trim())) {
                continue;
            }
            String procInstId = inst.getProcInstId();
            if (procInstId == null || procInstId.isBlank()) {
                continue;
            }
            try {
                cancelInstance(procInstId, reason);
            } catch (BizException ex) {
                boolean alreadyEnded = ex.getCode() == ErrorCode.PARAM_INVALID.getCode()
                        && ex.getMessage() != null && ex.getMessage().contains("实例不存在或已结束");
                if (!alreadyEnded) {
                    throw ex; // 流程终止失败必须冒泡（事务回滚，业务状态不落 VOID）
                }
                // 引擎实例已不在：视图面补办结（否则 wf_instance 永久滞留 RUNNING、待办继续被催）
                wfQueryService.updateInstanceStatus(procInstId, "CANCELLED", LocalDateTime.now());
                wfQueryService.closePendingTasksByInstance(procInstId,
                        reason == null ? "流程实例已被终结，视图补办" : reason);
            }
            cancelled++;
        }
        return cancelled;
    }

    /**
     * 指定业务单据是否仍存在活动审批任务。
     * 用于 OA 桥接等"代审批"方判断是否需要继续完成后续节点（杜绝流程中途遗弃产生僵尸任务）。
     */
    public boolean hasActiveTask(String bizType, Long bizId) {
        return taskService.createTaskQuery()
                .processVariableValueEquals("bizType", bizType)
                .processVariableValueEquals("bizId", bizId)
                .active()
                .count() > 0;
    }

    public void complete(String taskId, boolean approved, String comment, String rejectType, Long operUserId) {
        complete(taskId, approved, comment, rejectType, operUserId, null, null);
    }

    /**
     * 审批 + 下一环节点名（PICK 档）的 rejectType 版；不带运行时抄送人（向后兼容重载）。
     */
    public void complete(String taskId, boolean approved, String comment, String rejectType,
                         Long operUserId, Long nextAssigneeUserId) {
        complete(taskId, approved, comment, rejectType, operUserId, nextAssigneeUserId, null);
    }

    /**
     * 审批支持指定驳回类型。
     * @param rejectType THROW_BACK 漏单驳回（电子流直送我，只能重传影像/改简字段）/
     *                   GRADE_BY_GRADE 逐级驳回（字段可全改，默认）
     * @param nextAssigneeUserId 下一环节办理人点名（取人方式 PICK 的节点必填）。下一节点不是 PICK 档时
     *                           这个参数不参与取人。
     * @param ccUserIds 传阅：审批人临时指定的抄送人用户ID，逗号分隔，可空。
     *                  与节点配置 {@code wf_node_config.cc_users/cc_roles} <b>取并集</b>后投递到
     *                  {@code wf_cc}；不传且节点未配抄送时这一步整段跳过。
     *                  抄送是附加可观测性，投递失败只记日志、绝不阻断审批主链路。
     */
    public void complete(String taskId, boolean approved, String comment, String rejectType,
                         Long operUserId, Long nextAssigneeUserId, String ccUserIds) {
        complete(taskId, approved, comment, rejectType, operUserId, nextAssigneeUserId, ccUserIds, null);
    }

    /**
     * 审批 + 自由驳回（指定退回级次）。
     *
     * @param rejectToNodeKey 驳回目标节点 key（仅在 {@code approved=false} 时有意义）。
     *        <b>为空 ⇒ 走「终止本流程」兜底</b>，默认路径不受影响；
     *        非空且开关 {@code sys_config.wf.reject.free.enabled}=1 时，改用 Flowable「搬执行」
     *        把在办执行搬回该上游节点 —— 单据在<b>同一实例内</b>继续流转、审批历史保留，
     *        语义「允许选择驳回级次 / 默认驳回到开始节点」。
     *        <p>执行面属"搬执行"（{@code ChangeActivityStateBuilder.moveActivityIdTo}），
     *        会真实改动在办单据本体；开关 fail-closed 默认关，<b>须先经
     *        {@code GET /wf/reject-targets} 只读预览核对影响面、并在重启窗口完成运行期验证后</b>
     *        方可启用。
     */
    public void complete(String taskId, boolean approved, String comment, String rejectType,
                         Long operUserId, Long nextAssigneeUserId, String ccUserIds, String rejectToNodeKey) {
        complete(taskId, approved, comment, rejectType, operUserId, nextAssigneeUserId, ccUserIds,
                rejectToNodeKey, null);
    }

    /**
     * 「审批项」的 {@code rejectType} 自动推断版（rejectType 仍按老规则推断，调用方无需感知）。
     *
     * <p>与 {@code (String,boolean,String,String,Long,Long,String,String,String)} 仅第 4 个参数类型不同
     * Long/String，重载解析按实参类型精确匹配——正常调用传具体类型而非 {@code null} 字面量，不构成歧义。
     */
    public void complete(String taskId, boolean approved, String comment, Long operUserId,
                         Long nextAssigneeUserId, String ccUserIds, String rejectToNodeKey,
                         String approvalItemCode) {
        complete(taskId, approved, comment, rejectTypeOf(approved, comment), operUserId,
                nextAssigneeUserId, ccUserIds, rejectToNodeKey, approvalItemCode);
    }

    /**
     * 「审批项」9 参版本，多一个 {@code approvalItemCode}。8 参版本以 {@code null} 委托到这里，
     * 因此既有调用方不受影响。
     *
     * @param approvalItemCode 节点上配置的审批项编码（{@code wf_approval_item.item_code}）。
     *        <b>为空 ⇒ 完全不走审批项分支</b>：不读表、不写结果变量、不改意见文案。
     *        <p>非空时语义（审批项是「输入」，approved/rejectToNodeKey 是「引擎指令」，二者分离）：
     *        <ul>
     *          <li>{@code POSITIVE} → {@code approved=true}，按原路径继续流转；</li>
     *          <li>{@code REJECT} → {@code approved=false}；配置了 {@code target_node} 时作为
     *              {@code rejectToNodeKey} 送进既有「自由驳回」通道（仍受 {@code reject_scope}
     *              节点校验 + {@code wf.reject.free.enabled} 总开关双重约束，校验未放行即落回
     *              「终止本流程」）；未配 {@code target_node} 时即普通驳回；</li>
     *          <li>{@code TERMINATE} → {@code approved=false}，走「终止本流程」路径。</li>
     *        </ul>
     *        编码在该节点解析不到（未配置/已停用）时<b>明确拒绝</b>，不静默降级为普通通过/驳回：
     *        审批人点了按钮却"什么都没发生"比报错更难排查。
     */
    public void complete(String taskId, boolean approved, String comment, String rejectType,
                         Long operUserId, Long nextAssigneeUserId, String ccUserIds, String rejectToNodeKey,
                         String approvalItemCode) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        // 「审批项」解析
        // 位置铁律：必须在驳回原因校验、越权守卫、驳回校验判定<b>之前</b>——审批项会把 approved
        // 反转（点"终止"按钮时代码侧仍是 approved=true），后面所有分支都必须看到反转后的值，
        // 否则会出现"按终止项提交、引擎却当通过推进"的双真值。
        String approvalItemName = null;
        String approvalItemOper = null;
        String approvalItemCategory = null;
        if (approvalItemCode != null && !approvalItemCode.isBlank()) {
            String modelKey = task.getProcessDefinitionId() == null
                    ? null : task.getProcessDefinitionId().split(":")[0];
            WfApprovalItem item =
                    approvalItemService.resolve(modelKey, task.getTaskDefinitionKey(), approvalItemCode);
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "审批项不存在或已停用：" + approvalItemCode.trim()
                                + "（节点 " + task.getTaskDefinitionKey() + "）");
            }
            approvalItemName = item.getItemName();
            approvalItemOper = item.getLinkedOper();
            approvalItemCategory = WfApprovalItemService.normalizeCategory(item.getCategory());
            if (WfApprovalItem.CAT_POSITIVE.equals(approvalItemCategory)) {
                approved = true;
            } else {
                approved = false;
                if (WfApprovalItem.CAT_REJECT.equals(approvalItemCategory)
                        && item.getTargetNode() != null && !item.getTargetNode().isBlank()
                        && (rejectToNodeKey == null || rejectToNodeKey.isBlank())) {
                    // 目标节点走既有驳回通道，校验逻辑一处不改（不新造第二条驳回路径）
                    rejectToNodeKey = item.getTargetNode().trim();
                }
            }
            log.info("审批项命中: task={} node={} item={}({}) → approved={} rejectToNode={}",
                    taskId, task.getTaskDefinitionKey(), approvalItemCode, approvalItemCategory,
                    approved, rejectToNodeKey);
        }
        // 驳回必须写明原因（与前端审批弹窗同一规则）。放在这里是因为它是所有人工审批入口的
        // 唯一办结：/approve、/approve-bill、/batch-approve 与 OA 代审都经此方法，
        // 绕开页面直接调接口也无法留下空原因的驳回记录。超时 AUTO_PASS、空节点自动跳过
        // 走 taskService 直连，不经过本方法，因此不受影响。
        if (!approved && (comment == null || comment.trim().length() < 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因（不少于 2 个字）");
        }
        // 附随开关：通过侧审批意见必填，默认关＝现网行为一字不改（通过可留空）。
        // 与驳回校验同办结、同豁免面：超时 AUTO_PASS 与空节点自动跳过走 taskService 直连，不经过本方法。
        if (approved && isApproveCommentRequired(task) && (comment == null || comment.trim().length() < 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "本流程要求通过时填写审批意见（不少于 2 个字）");
        }
        // 审批越权守卫===：仅当前节点办理人/候选人/生效受托人可办理
        Long operator = operUserId != null ? operUserId
                : (UserContext.get() != null
                        ? UserContext.get().getUserId() : null);
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法审批此任务");
        }
        if (!nodeOrgResolver.canOperate(task, operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, operateDeniedMessage(task));
        }
        // 作业池任务是处理类节点，不提供驳回语义——处理异常走该链的升级档节点或共享主管，不在这里打回。
        if (!approved && nodeOrgResolver.isPoolTask(task)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "作业池任务为处理类节点，不支持驳回；处理异常请走升级档节点或联系共享主管");
        }
        // 领取前置校验：POOL 作业节点未领取（无办理人）时一律拒办。只靠 canOperate 的候选组通道，
        // "同池任意候选人可直接审批"就成立，池软锁形同虚设，同一批流程会走出"有 CLAIM 记录"与
        // "零 CLAIM 记录"两种路径。领取（claim）才是池档的必经动作；已领取、被智能推送指派、
        // 被改派到名的任务不受影响，系统自动审核身份豁免。
        if (nodeOrgResolver.isPoolTask(task) && !isSystemAutoAuditor(operator)
                && (task.getAssignee() == null || task.getAssignee().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "作业池任务须先「领取」再办理（池内抢单锁定到人）：请在待办中点「领取」锁定本任务后办理");
        }
        // 自审禁止开关：approve.deny_self=1 且流程发起人=操作人=单据提交人时拒绝（读取失败按 0 放行）
        WfInstance self = wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
        // POOL 作业池档不走本校验：这条校验的语义是"禁止审批自己提交的单据"，而 POOL 节点办的是
        // 作业（取银行明细/编调节表/扫描齐套），不是审批；复核另有下一节点的另一岗承担。
        // 拦截反而破坏 POOL 的既定语义"池内谁做、记录到人"——提单人只要是该岗合法候选人就应能办。
        // 引擎层审批节点没有这个属性；特殊数据权限「创建者无权」讲的是"单据的创建人不能审核自己的
        // 单据"，业务参数「制单与审批不能为同一人」默认否——两处都指审批动作，不覆盖作业。
        if (isDenySelfEnabled() && !nodeOrgResolver.isPoolTask(task)) {
            if (self != null && operator.equals(self.getStartUserId())) {
                Long applyUser = nodeOrgResolver.resolveApplyUserId(task.getProcessInstanceId());
                // 提交人不可解析（域无申请人列 / 查询异常）时不能静默放行——那样"提单人自批发票"
                // 会零痕迹通过。这里 fail-closed：按发起人＝提交人这一最严口径拦截，
                // 首节点豁免逻辑不变（见 doneTasks 的判定）。
                if (applyUser == null || operator.equals(applyUser)) {
                    long doneTasks = historyService.createHistoricTaskInstanceQuery()
                            .processInstanceId(task.getProcessInstanceId()).finished().count();
                    if (doneTasks > 0) {
                        throw new BizException(ErrorCode.FORBIDDEN, applyUser == null
                                ? "禁止审批自己提交的单据（该单据类型取不到提交人列，自审禁止按最严格规则拦截）"
                                : "禁止审批自己提交的单据");
                    }
                }
            }
        }
        // 同人连办拦截：对 sys_config.approve.deny_serial_same_handler 指定的流程链
        // （月结链 GL_MONTH_CLOSE，按角色铺设备办理人 + 禁 admin 参与）——禁止同一操作人在同一
        // 流程实例内办理多个审批节点。默认关闭，仅由配置显式启用，避免误伤既有流程。
        // 系统自动审核身份（sysauto）豁免串行连办拦截——小额免审本来就是同一系统身份
        // 依次通过全部节点；人工审批人不受此豁免影响（制复核分离硬约束仍对真人生效）。
        if (self != null && isSerialSameHandlerDenied(self.getBizType()) && !isSystemAutoAuditor(operator)) {
            // 校验的语义是"同一人往前连办多个<b>不同</b>节点"，而自由驳回把令牌退回给
            // 某个人时，他要重办的正是自己办过的<b>同一节点</b>——按原写法一律算"连办"，
            // 于是单指派节点（candidate_ids=NULL）退回后既办不掉也驳不掉，只能转办/改派救。
            // 故排除同节点重办：跨节点（taskDefinitionKey 不同）才拦。
            long handled = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .taskAssignee(String.valueOf(operator)).finished().list().stream()
                    .filter(h -> !Objects.equals(h.getTaskDefinitionKey(), task.getTaskDefinitionKey()))
                    .count();
            if (handled > 0) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "禁止同一人在同一审批流程中连续办理多个节点（连办/自审拦截）");
            }
        }
        // 齐套校验·节点层：缺件一律拒绝，没有"带条件通过"
        // 排在越权/自审/连办三道既有校验之后、加签回跳之前：驳回永远放行（驳回是收紧控制，不是放行），
        // 只有"同意"这一步需要先确认资料齐不齐。灰度未开时 assertComplete 直接返回，行为零变化。
        if (approved && self != null) {
            billCompletenessGuard.assertComplete(self.getModelKey(),
                    BillCompletenessGuard.STAGE_NODE_PREFIX + task.getTaskDefinitionKey(),
                    self.getBizType(), self.getBizId());
        }
        // 加签「回跳」钩子：
        // 若当前办理人正是某条加签记录的当期加签人，其「同意」只代表"加签这一道审完"，
        // 必须回跳（前加签→原办理人；后加签→下一节点应得的人），绝不推进流程：
        // 故此处直接 return，绕过下面全部 complete / 驳回兜底 / 节点同步逻辑。
        // 驳回相反——加签人驳回就是业务驳回，让正常驳回兜底（terminateAsRejected）走完，
        // 加签记录置 CANCELLED 即可，不做回跳。
        WfAddSign addSignOnTask =
                wfAddSignService.activeByTaskAndUser(taskId, operator);
        if (addSignOnTask != null) {
            if (!approved) {
                wfAddSignService.markStatus(addSignOnTask.getId(),
                        WfAddSign.STATUS_CANCELLED);
            } else {
                returnFromAddSign(task, addSignOnTask, comment, operator);
                return;
            }
        }
        // 「重新提交跳回本节点」关联点：
        // resubmit_jump_back=1 的节点一旦驳回（普通驳回与指定级次驳回都算），先把本节点 key 记入
        // 实例变量 rejectOriginNode，供"单据重新提交时跳回本节点"消费。未配该列的节点零副作用。
        if (!approved) {
            recordRejectOriginNode(task);
        }
        // 自由驳回钩子：
        // 位置铁律：必须在 taskService.complete() 之前。驳回时本方法底部的兜底是
        // 「先 complete 再 deleteProcessInstance」，执行已向前推进；若在此后才搬执行，
        // 就要从"推进后的新节点"往回搬，形成二次搬执行、极易错位。
        // 提前 return 的范式与上面的加签回跳钩子一致：绕过 complete/票签/驳回兜底/节点同步全部下游。
        // 生效条件：本节点 wf_node_config.reject_scope ≠ NONE
        // 并且全局总开关 wf.reject.free.enabled=1、本流程在 wf.reject.free.models 白名单内。
        // 任一不满足 ⇒ 落回「终止本流程」路径；
        // 全局开关保留作总开关/kill-switch（回滚路径，勿删）。
        if (!approved && rejectToNodeKey != null && !rejectToNodeKey.isBlank()
                && !isVoteNode(task) && isFreeRejectModelAllowed(modelKeyOf(task)) && freeRejectScopeOf(task) != null) {
            rejectToNode(task, rejectToNodeKey.trim(), comment, operator);
            return;
        }
        // 审批项/调用方指定了目标节点、但校验未放行（节点 reject_scope=NONE 或总开关关闭）：
        // 如实留一行日志。否则"配了目标节点、实际却驳成了终止"在运行期无从解释——
        // 沉默的配置等于没有配置。
        if (!approved && rejectToNodeKey != null && !rejectToNodeKey.isBlank() && !isVoteNode(task)
                && !(isFreeRejectModelAllowed(modelKeyOf(task)) && freeRejectScopeOf(task) != null)) {
            log.warn("驳回目标节点未生效（本节点 reject_scope 未开启，或总闸 wf.reject.free.enabled 关闭，"
                    + "或本流程未进 wf.reject.free.models 灰度白名单），"
                    + "落回「终止本流程」: task={} target={}", taskId, rejectToNodeKey);
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", approved);
        vars.put("comment", comment);
        if (!approved) {
            vars.put("rejectType", "THROW_BACK".equals(rejectType) ? "THROW_BACK" : "GRADE_BY_GRADE");
        }
        // 审批结果变量（ActionResult）：
        // 流转条件若只能拿"这张单的静态属性"（金额/币种/是否境外）当参数，就分不出"上一节点审出了
        // 什么结果"；而月度会签的"半数以上反对即转上级"这类规则根子上依赖审批结果。
        // 写入后 BPMN 排他网关可直接写 ${ActionResult == 'xxx'}。
        // 三个变量分工（不合并成一个 JSON：EL 表达式里字符串比对最直观，也不引入解析依赖）：
        //   ActionResult 审批项编码（条件判断用的标识符）
        //   ActionResultName 审批项名称（报表/时间线展示用，避免前端再查配置表）
        //   ActionResultCategory POSITIVE/REJECT/TERMINATE（判定"这类结果会不会终止流程"）
        //   ActionOper 关联表单操作（可空；仅供业务域订阅，引擎不执行——见 WfApprovalItem 注释）
        // 未传 approvalItemCode 时一个变量都不写，实例变量集合保持不变。
        if (approvalItemCode != null && !approvalItemCode.isBlank()) {
            vars.put("ActionResult", approvalItemCode.trim());
            vars.put("ActionResultName", approvalItemName == null ? approvalItemCode.trim() : approvalItemName);
            vars.put("ActionResultCategory", approvalItemCategory);
            if (approvalItemOper != null && !approvalItemOper.isBlank()) {
                vars.put("ActionOper", approvalItemOper.trim());
            }
        }
        // PICK 档下一环节点名校验：必须在 taskService.complete <b>之前</b>（本类无事务包裹）。
        // 驳回不校验——实例随后被终止，没有"下一环节"可言。
        if (approved) {
            enforcePickOnApprove(task, self, vars, nextAssigneeUserId, operator);
        }
        // 审计追溯：候选组（角色）任务完成时 assignee 可能为空或被兜底为 admin，
        // 完成前将 Flowable 任务 assignee 显式置为实际操作人，历史表方可定位真实审批人。
        try {
            taskService.setAssignee(task.getId(), String.valueOf(operator));
        } catch (Exception ex) {
            log.warn("回写 Flowable 任务办理人失败 taskId={} operator={}", taskId, operator, ex);
        }
        // 票签计票：若本任务属于 VOTE 多实例节点（启动期注入的 <nodeKey>_approvedCount 变量存在），
        // 按审批结果累加赞成/反对计数（流程变量），再 complete——引擎据 completionCondition 判断是否提前办结。
        countVoteIfNeeded(task, approved);
        // PROCESS_COMPLETED 监听器在 complete() 内<b>同步</b>派发，而镜像行的 DONE 是在下方
        // markTaskDone 才写。不登记"本线程正在办结哪条任务"，实例级办结环节就会把这条真实办结的
        // 收尾任务当成未办理，给它写一行作废记录——历史表里就凭空多出几条假 VOID。
        String completingPrev = WfQueryService.beginCompletingTask(taskId);
        // 前置件：同线程把本次办结的操作人带给 PROCESS_COMPLETED 监听器（见 END_OPER_USER_ID）。
        // 只传形参 operUserId（与下方 APPROVE/REJECT 记录的 oper_user_id 同一来源），不传 UserContext 默认值。
        Long endOperPrev = beginEndOperator(operUserId);
        try {
            taskService.complete(taskId, vars);
        } catch (FlowableOptimisticLockingException ex) {
            // ST-02: 并发双审的败者返回友好业务提示，而非 500 系统异常
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "任务已被处理，请刷新待办");
        } finally {
            endEndOperator(endOperPrev);
            WfQueryService.endCompletingTask(completingPrev);
        }
        // 待办视图双写：任务完成标记 DONE 并回写真实办理人（审计追溯）。
        // 这一步失败只打一行 log 的话，引擎已推进、镜像仍是 PENDING，待办列表里这条单子永远办不掉。
        // 所以按"记缺陷 + 收尾后冒泡"处理：先把错误码与 sys_oper_log 记下来、异常暂存，让下面的
        // 历史记录/实例推进/抄送等收尾步骤照常走完，最后再统一抛给界面。
        Exception mirrorFailure = null;
        // 镜像开关打开时停写 wf_task，改由监听器 TASK_COMPLETED 投影（置 DONE + 回写真实办理人）。
        // wf_task_history 是审计记录，过渡期双写——不在此守卫内（见 writeHistory 的来源标记）。
        if (mirrorByFacade()) {
            try {
                wfQueryService.markTaskDone(taskId, operator);
            } catch (Exception ex) {
                // 镜像写入失败已由缺陷记录器记录，并暂存 ex 交调用方决定是否上抛
                mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED, "markTaskDone",
                        self == null ? null : self.getTenantId(), "taskId=" + taskId + " operator=" + operator, ex);
                mirrorFailure = ex;
            }
        }
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), approved ? "APPROVE" : "REJECT", operUserId,
                commentWithApprovalItem(comment, approvalItemName, approvalItemOper));
        // 票签(VOTE)多实例节点标记——必须在驳回兜底之<b>前</b>判定，否则一票否决会先把流程杀掉、
        // 比例投票没有生效机会
        boolean voteNode = isVoteNode(task);
        // 票签办结判定（先于驳回兜底）：
        // VOTE 节点按 vote_ratio 聚合：逐票累计，办结（同节点无在办任务）后按"赞成×100 ≥ 阈值×应到"
        // 给出节点结论；未达通过线才以驳回语义收尾。故 VOTE 节点<b>不得</b>走下面的"1 票即终止"兜底。
        if (voteNode) {
            resolveVoteOutcome(task, comment, rejectType, operUserId);
        }
        // 驳回兜底（票签节点跳过）：
        // 由 wf_business_flow.node_json 动态生成的 BPMN 只有「金额>阈值」排他网关，
        // 没有 ${approved == false} 的驳回分支（仅内置 expense-claim.bpmn20.xml 有）。
        // 结果是：approve(approved=false) 只把变量塞进去就 complete，流程继续往下走 → 驳回永远不生效。
        // 此处统一兜底：驳回后若流程仍在运行，说明模型无驳回分支，强制终止实例并发布"审批不通过"结束事件，
        // 业务监听（ExpenseWfEventListener 等）据此把单据置 REJECTED，语义等同"驳回至发起人、需重新提交"。
        // 【票签例外】VOTE 多实例的一票"反对"是投票而非定论：此处若即时终止，vote_ratio 的比例判定
        // 就永无生效机会（3 人票签、阈值 50% 需 2 票赞成，投出第 1 张反对票即杀流程）⇒ 交给上面的办结判定。
        if (!approved && !voteNode) {
            terminateAsRejected(task.getProcessInstanceId(), comment, rejectType);
        }
        // 单据流程感知：同步 wf_instance.current_node（推进到下一人工任务）
        try {
            // 办结推进路径负责给新生成的下一节点待办发提醒——发提醒不能只在 start() 发首节点
            syncInstanceAfterAdvance(task.getProcessInstanceId(), true);
        } catch (BizException be) {
            // 空策略 ERROR 在此抛出的业务异常必须透出（否则被下面的兜底 catch 吞掉，
            // 「报错阻断」等同于配置未生效，节点仍会静默滞留）。
            throw be;
        } catch (Exception ex) {
            log.warn("流程当前节点同步失败 taskId={}", taskId);
        }
        // 委托完成响应：操作人若正作为他人的受托人，回告委托人"你的待办已被代办"。
        // 放在推进办结之后、且内部整体 try/catch —— 响应是附加可观测性，绝不阻断审批主链路。
        notifyDelegatePrincipal(task.getProcessInstanceId(), operator, approved);
        // 加签「后加签」生效：本次审批通过并已推进，把下一节点新产生的任务改派给加签人。
        // 只换办理人、不改流程走向；内部整体 try/catch，失败绝不阻断审批主链路。
        applyPendingAfterAddSign(task.getProcessInstanceId(), taskId, approved, operator);
        // 传阅投递：
        // wf_cc 一度是「读端与前端齐备、生产端缺失」的空壳——recordCc() 全仓无人调用、
        // wf_node_config 没有抄送列、表里一行数据都没有。这里补上生产端这一半。
        // 只在「通过并推进」后投递：驳回会走 terminateAsRejected 终止实例，且已有独立终止
        // 通知链路，给已终止实例挂抄送点击价值低（如需「驳回也抄送」，放开此 if 即可）。
        // 节点未配抄送且未传 ccUserIds 时不写任何行。
        // 逐条内部 try/catch —— 传阅是附加可观测性，绝不阻断审批主链路。
        if (approved) {
            try {
                wfCcService.deliver(task.getProcessInstanceId(), task.getTaskDefinitionKey(), operator, ccUserIds);
            } catch (Exception ex) {
                log.warn("传阅投递失败 taskId={} procInst={}", taskId, task.getProcessInstanceId(), ex);
            }
        }
        // 冒泡办结：引擎侧已推进、历史与实例视图均已收尾，此处把镜像写失败抛给调用方。
        // 本方法没有 @Transactional、也不回滚引擎推进，业务不回滚是既定行为；但界面必须看得见。
        // 错误码与 sys_oper_log 已在上方缺陷记录器写入数据库。
        if (mirrorFailure != null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "审批已提交，但待办视图（wf_task）回写失败[" + WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED
                            + "]，请刷新待办；管理员可查系统日志与双账对账报表：taskId=" + taskId);
        }
    }

    // 加签（前/后加签）
    // 能力边界：Flowable 8 没有 ProcessInstanceModification，无法在运行期动态插入 BPMN 节点
    // （javap 核对 flowable-engine-8.0.0.jar 确认）。故本实现用「临时改派 + 回跳」做等价语义，
    // 详见 com.fssc.workflow.entity.WfAddSign 类注释。
    // 库中没有任何 wf_add_sign 记录时，complete() 不进入加签分支。

    /**
     * 发起加签（前加签 / 后加签）。
     *
     * <p>权限：仅当前任务的可办理人（{@code NodeOrgResolver.canOperate}，含受托人通道）；
     * 加签人须存在、在职（NORMAL）、且非 admin —— 与「admin 禁入业务审批节点」的既有铁律同一规则。
     *
     * <p>同一任务已有进行中的加签时<b>直接拒绝而非排队</b>：加签链的顺序性一旦被静默重排，
     * 审批记录就无法解释"到底谁先审的"。
     */
    public WfAddSign addSign(String taskId, String position,
                                                      String signUsersCsv, String reason) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        Long operator = UserContext.get() != null
                ? UserContext.get().getUserId() : null;
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法发起加签");
        }
        if (!nodeOrgResolver.canOperate(task, operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能对当前由你办理的任务发起加签");
        }
        // allow_addsign 曾经"实体不映射、界面不显示、运行时零读取"三重落空
        // 变成真开关。现网 311 个节点配 0、44 个配 1——一接上就大面积禁签，故必须走灰度：
        // approve.addsign_gate.enabled=1 且模型在 approve.addsign_gate.models 内才拦，缺省＝不拦（现行为不变）。
        if (isConfigGatedOn("approve.addsign_gate.enabled", "approve.addsign_gate.models", modelKeyOf(task))) {
            WfNodeConfig addSignCfg = wfNodeConfigResolver.resolve(modelKeyOf(task), task.getTaskDefinitionKey());
            if (addSignCfg != null && Integer.valueOf(0).equals(addSignCfg.getAllowAddsign())) {
                throw new BizException(ErrorCode.FORBIDDEN, "节点「"
                        + (addSignCfg.getNodeName() == null || addSignCfg.getNodeName().isBlank()
                            ? task.getTaskDefinitionKey() : addSignCfg.getNodeName())
                        + "」未开放加签（流程配置「允许加签」=否）；确需增加审批人请联系流程管理员调整节点配置");
            }
        }
        String pos = position == null ? "" : position.trim().toUpperCase();
        boolean before = WfAddSign.POSITION_BEFORE.equals(pos);
        if (!before && !WfAddSign.POSITION_AFTER.equals(pos)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加签位置只能是 BEFORE（前加签）或 AFTER（后加签）");
        }
        List<Long> signers = new ArrayList<>();
        for (String s : (signUsersCsv == null ? "" : signUsersCsv).split(",")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            long sid;
            try {
                sid = Long.parseLong(t);
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加签人ID必须是数字: " + t);
            }
            if (!signers.contains(sid)) {
                signers.add(sid);
            }
        }
        if (signers.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少选择一位加签人");
        }
        StringJoiner names = new StringJoiner("、");
        StringJoiner csv = new StringJoiner(",");
        // 本节点已投票者不得再被加签——同一节点二次出票破坏会签独立性（先投会签票、
        // 之后其节点又出一张加签票）。这也就成了加签人的第一道名单约束。
        Set<Long> votedAtNode = votersAtNode(task.getProcessInstanceId(), task.getTaskDefinitionKey());
        for (Long sid : signers) {
            SysUser u = sysUserMapper.selectById(sid);
            if (u == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加签人不存在: " + sid);
            }
            if (u.getStatus() != null && !"NORMAL".equals(u.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加签人已停用: " + sid);
            }
            if (u.getIsAdmin() != null && u.getIsAdmin() == 1) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "不允许把管理员加签为审批人（admin 禁入业务审批节点）");
            }
            if (votedAtNode.contains(sid)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "加签人「"
                        + (u.getNickName() != null ? u.getNickName() : String.valueOf(sid))
                        + "」已在本节点投过票，同一节点不得二次出票（会签独立性）；确需其复核请联系流程管理员改派至后续节点或重走流程");
            }
            names.add(u.getNickName() != null ? u.getNickName() : String.valueOf(sid));
            csv.add(String.valueOf(sid));
        }
        if (wfAddSignService.hasOpenOnTask(task.getId())
                || !wfAddSignService.pendingAfterByProc(task.getProcessInstanceId(), task.getId()).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该任务已有进行中的加签，请等它走完或先取消");
        }
        WfAddSign rec = new WfAddSign();
        rec.setProcInstId(task.getProcessInstanceId());
        rec.setSrcTaskId(task.getId());
        rec.setNodeKey(task.getTaskDefinitionKey());
        rec.setNodeName(task.getName());
        rec.setPosition(pos);
        rec.setSignUsers(csv.toString());
        rec.setSeqIndex(0);
        rec.setReason(reason);
        rec.setCreateUserId(operator);
        if (before) {
            // 前加签：立刻改派，加签人审完由 returnFromAddSign 交还原办理人
            rec.setTaskId(task.getId());
            rec.setExitAssignee(task.getAssignee());
            rec.setStatus(WfAddSign.STATUS_ACTIVE);
            wfAddSignService.create(rec);
            reassignTask(task.getId(), String.valueOf(signers.get(0)));
        } else {
            // 后加签：先登记，等本任务办结推进时由 applyPendingAfterAddSign 落到新任务上
            rec.setStatus(WfAddSign.STATUS_PENDING);
            wfAddSignService.create(rec);
        }
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "ADDSIGN", operator, signers.get(0),
                (before ? "前加签：" : "后加签：") + names
                        + (signers.size() > 1 ? "（依次加签）" : "")
                        + (reason == null || reason.isBlank() ? "" : "；原因：" + reason));
        log.info("加签已登记 procInst={} task={} position={} signers={}",
                task.getProcessInstanceId(), task.getId(), pos, rec.getSignUsers());
        return rec;
    }

    /** 取消加签。权限：加签发起人或当期加签人本人。进行中的加签取消时先把任务交还回跳目标。 */
    public void cancelAddSign(Long id) {
        WfAddSign rec = wfAddSignService.byId(id);
        if (rec == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加签记录不存在: " + id);
        }
        Long operator = UserContext.get() != null
                ? UserContext.get().getUserId() : null;
        Long cur = wfAddSignService.currentSigner(rec);
        if (operator == null
                || (!operator.equals(rec.getCreateUserId()) && !operator.equals(cur))) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有加签发起人或当期加签人可以取消加签");
        }
        if (WfAddSign.STATUS_ACTIVE.equals(rec.getStatus())
                && cur != null && operator.equals(cur)) {
            // 当期加签人放弃：把任务交还回跳目标，避免单据卡在一个已放弃的加签人手上
            reassignTask(rec.getTaskId(), rec.getExitAssignee());
        }
        wfAddSignService.markStatus(rec.getId(), WfAddSign.STATUS_CANCELLED);
        writeHistory(rec.getProcInstId(), rec.getTaskId(), rec.getNodeKey(), rec.getNodeName(),
                "ADDSIGN", operator, "取消加签");
    }

    /**
     * 该流程实例上仍在进行/待生效的加签（只读）。传 taskId 时自动解析其所属实例，
     * 便于前端在"正在办理某任务"的上下文里一次拿全；两者都取不到则返回空表（不抛异常）。
     */
    public List<WfAddSign> openAddSign(String procInstId, String taskId) {
        String pid = procInstId;
        if ((pid == null || pid.isBlank()) && taskId != null && !taskId.isBlank()) {
            Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (t != null) {
                pid = t.getProcessInstanceId();
            } else {
                WfAddSign rec = wfAddSignService.activeByTask(taskId);
                if (rec != null) {
                    pid = rec.getProcInstId();
                }
            }
        }
        if (pid == null || pid.isBlank()) {
            return new ArrayList<>();
        }
        return wfAddSignService.openByProc(pid);
    }

    /**
     * 加签回跳：加签人「同意」只代表加签这一道审完 —— 把任务交还（前加签→原办理人；
     * 后加签→下一节点应得的人）并<b>不推进流程</b>。可再加签靠记录嵌套天然支持：
     * 新记录的回跳目标是上一个加签人，回跳时取最近一条 ACTIVE 记录即可。
     */
    private void returnFromAddSign(Task task, WfAddSign rec,
                                   String comment, Long operator) {
        Long nextSigner = wfAddSignService.advanceToNext(rec);
        String restore = nextSigner != null ? String.valueOf(nextSigner) : rec.getExitAssignee();
        reassignTask(task.getId(), restore);
        if (nextSigner == null) {
            wfAddSignService.markStatus(rec.getId(), WfAddSign.STATUS_DONE);
        }
        boolean before = WfAddSign.POSITION_BEFORE.equals(rec.getPosition());
        String tail = nextSigner != null
                ? ("，转下一位加签人 " + nextSigner)
                : ("，已交还" + (rec.getExitAssignee() == null
                        ? "候选组/任务池" : "原办理人 " + rec.getExitAssignee()));
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                // 审结行改记 ADDSIGN_DONE，与发起行（ADDSIGN）分码——审计表自此一眼可见
                // "这一道加签有没有走完"，不再靠 target_user_id 与 wf_add_sign.status 反推。
                // 读侧 WfQueryService.isAddSignTrail 两码同堆（的序号/分母规则零漂移）。
                task.getName(), WfQueryService.ACTION_ADDSIGN_DONE, operator,
                (before ? "前加签" : "后加签") + "审完" + tail
                        + (comment == null || comment.isBlank() ? "" : "；意见：" + comment));
        log.info("加签回跳 task={} restore={} nextSigner={}", task.getId(), restore, nextSigner);
    }

    /** 后加签生效：把推进后新产生的任务改派给加签人（只换办理人，不改流程走向）。 */
    private void applyPendingAfterAddSign(String procInstId, String srcTaskId,
                                          boolean approved, Long operator) {
        try {
            if (!approved || procInstId == null || srcTaskId == null) {
                return;
            }
            List<WfAddSign> pend =
                    wfAddSignService.pendingAfterByProc(procInstId, srcTaskId);
            if (pend.isEmpty()) {
                return;
            }
            WfAddSign rec = pend.get(0);
            for (int i = 1; i < pend.size(); i++) {
                wfAddSignService.markStatus(pend.get(i).getId(),
                        WfAddSign.STATUS_FAILED);
                log.warn("后加签重复登记，已置 FAILED recId={}", pend.get(i).getId());
            }
            List<Task> live = taskService.createTaskQuery()
                    .processInstanceId(procInstId).active().list();
            if (live.size() != 1) {
                // 0=流程已办结；>1=并联/多实例节点，无法确定"该加签哪一条" → 不生效并告知发起人
                wfAddSignService.markStatus(rec.getId(), WfAddSign.STATUS_FAILED);
                log.warn("后加签未生效：推进后活跃任务数={}（需恰好 1）procInst={} recId={}",
                        live.size(), procInstId, rec.getId());
                notifyAddSignFailed(rec, live.size());
                return;
            }
            Task next = live.get(0);
            Long first = wfAddSignService.currentSigner(rec);
            wfAddSignService.bindTask(rec.getId(), next.getId(), next.getAssignee());
            reassignTask(next.getId(), first == null ? null : String.valueOf(first));
            writeHistory(procInstId, next.getId(), next.getTaskDefinitionKey(), next.getName(),
                    "ADDSIGN", operator, first, "后加签：" + first + "（原办理人 "
                            + (next.getAssignee() == null ? "候选组" : next.getAssignee()) + "）");
            log.info("后加签已生效 procInst={} task={} signer={}", procInstId, next.getId(), first);
        } catch (Exception ex) {
            log.warn("后加签生效处理失败 procInst={} srcTask={}: {}", procInstId, srcTaskId, ex.getMessage());
        }
    }

    /** 改派任务；target 为空则退回候选组/任务池（unclaim）。同步 wf_task 镜像，失败只记日志。 */
    private void reassignTask(String taskId, String target) {
        try {
            if (target == null || target.isBlank()) {
                taskService.unclaim(taskId);
                wfQueryService.clearTaskAssignee(taskId);
            } else {
                taskService.setAssignee(taskId, target);
                wfQueryService.updateTaskAssignee(taskId, toUserIdOrNull(target));
            }
        } catch (Exception ex) {
            log.error("加签改派任务失败 taskId={} target={}", taskId, target, ex);
        }
    }

    /** 后加签未能生效时告知发起人 —— 「配了不生效」必须让人知道，绝不静默。 */
    private void notifyAddSignFailed(WfAddSign rec, int activeTasks) {
        try {
            if (rec.getCreateUserId() == null) {
                return;
            }
            String why = activeTasks == 0
                    ? "流程已办结，没有可加签的后续节点"
                    : ("推进后在办任务有 " + activeTasks + " 条（并联/多实例节点），无法确定加签对象");
            notifyService.notifyUser(rec.getCreateUserId(), "WF_ADDSIGN", "后加签未生效",
                    "你在节点【" + (rec.getNodeName() == null ? rec.getNodeKey() : rec.getNodeName())
                            + "】登记的后加签未能生效：" + why + "。流程本身不受影响。",
                    null, null);
        } catch (Exception ex) {
            log.warn("后加签失败告知发送失败 recId={}: {}", rec.getId(), ex.getMessage());
        }
    }

    /**
     * 委托完成响应。
     *
     * <p>委托人把待办委托出去之后，原任务从自己的「我的待办」里消失（可见性交给受托人），
     * 于是**无从知道**是"受托人办了"还是"流程卡住了"。委托建立/取消都有通知，
     * 唯独办完这一环没有，这里补上。
     *
     * <p>判定规则与 {@code NodeOrgResolver.canOperate} 的委托通道**同源**：操作人作为受托人、
     * 状态 NORMAL、且当前时间落在 [begin_time, end_time] 内。改派/转办等机器路径不经过本方法，
     * 不产生响应。
     *
     * <p>本方法只做"通知"，**不参与任何权限判定**——自审禁止由 {@code approve.deny_self}
     * 在 complete() 前段统一拦截，与是否委托无关。
     */
    private void notifyDelegatePrincipal(String procInstId, Long operator, boolean approved) {
        try {
            if (operator == null || procInstId == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            List<WfDelegate> ds = wfDelegateMapper.selectList(
                    new LambdaQueryWrapper<WfDelegate>()
                            .eq(WfDelegate::getToUserId, operator)
                            .eq(WfDelegate::getStatus, "NORMAL")
                            .le(WfDelegate::getBeginTime, now)
                            .ge(WfDelegate::getEndTime, now));
            if (ds == null || ds.isEmpty()) {
                return;
            }
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            String title = (inst == null || inst.getTitle() == null || inst.getTitle().isBlank())
                    ? procInstId : inst.getTitle();
            String opName = String.valueOf(operator);
            try {
                SysUser ou = sysUserMapper.selectById(operator);
                if (ou != null && (ou.getNickName() != null || ou.getUserName() != null)) {
                    opName = (ou.getNickName() == null ? ou.getUserName() : ou.getNickName())
                            + "(" + ou.getUserName() + ")";
                }
            } catch (Exception ignore) {
                // 取展示名失败不降级响应本身
            }
            String msg = (approved ? "你委托的待办已由 " : "你委托的待办已被 ") + opName
                    + (approved ? " 代办完成：" : " 驳回：") + title;
            for (WfDelegate d : ds) {
                try {
                    // 响应消息必须带 (bizType, bizId) 成对落 sys_message——少了 biz_id，
                    // 委托人收到"你的委托已办"却点不回那张单
                    notifyService.notifyUser(d.getFromUserId(), "DELEGATE_DONE",
                            approved ? "受托代办完成" : "受托代办（已驳回）", msg,
                            inst == null ? null : inst.getBizType(),
                            inst == null ? null : inst.getBizId(), "/workflow/my-todo");
                    log.info("委托完成回执: fromUser={} toUser={} inst={}", d.getFromUserId(), operator, procInstId);
                } catch (Exception e) {
                    log.warn("委托完成回执发送失败 fromUser={} inst={}: {}", d.getFromUserId(), procInstId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("委托完成回执处理异常 inst={}: {}", procInstId, e.getMessage());
        }
    }

    /**
     * 人工任务办结后的流程推进办结：下一节点三级取人（assignApprover，内含镜像补账）
     * + 实例视图 current_node / current_assignee 同步。
     *
     * <p>从 {@code complete()} 抽出，供超时自动通过这条机器路径复用——自动通过若只把手上这条
     * 待办置 DONE 而不推进，推进出来的下一节点没人取人、也没镜像行，单子＝"通过后失踪"。
     *
     * <p>空策略 ERROR 会抛 {@code BizException}，由调用方决定是否透出给终端用户。
     *
     * <p>{@code notifyTodo=true} 时，节点刷新后给<b>本次新出现</b>的每条待办发"审批待办提醒"——
     * 只在 start() 给首节点发提醒的话，后续节点生成时后几名审批人一条都收不到。
     * 自由驳回/重提跳回两个环节自带定向通知，须传 false 防双发。
     */
    private void syncInstanceAfterAdvance(String procInstId, boolean notifyTodo) {
        WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
        if (inst == null || !"RUNNING".equals(inst.getStatus())) {
            return;
        }
        List<Task> open = openTasks(procInstId);
        // 节点办理人三级解析：节点配置覆盖 → 组织解析 → 空策略兜底
        if (!open.isEmpty() && inst.getModelKey() != null) {
            assignApprovers(inst.getModelKey(), procInstId);
            // 解析会改派办理人，快照不刷新＝把"上一环节的办理人"写进实例视图
            open = openTasks(procInstId);
        }
        WfInstance upd = new WfInstance();
        upd.setCurrentNode(joinNodeNames(open));
        // 这里必须取下一任务解析后的真实办理人，不能写"刚点通过的人"（operUserId）：那样实例视图的
        // "当前办理人"永远是上一个审批人，列表、看板、监控大屏的"在谁手上"从头错到尾。
        // POOL/候选组态无人认领时置空串——update(entity,wrapper) 忽略 null 字段，
        // 用 null 清不掉上一环节留下的旧值，界面会继续显示已办结的人。
        if (!open.isEmpty()) {
            upd.setCurrentAssignee(open.stream()
                    .map(t -> t.getAssignee() == null ? "" : t.getAssignee())
                    .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "," + b).orElse(""));
        }
        // 镜像开关打开时门面停写 wf_instance.current_node/current_assignee，
        // 改由监听器 TASK_CREATED/TASK_ASSIGNED 的投影接管（注意：投影取"最后一条任务"，与这里
        // joinNodeNames 的并联合集规则存在差异，属过渡期已知取舍，见 WfMirrorProjectionListener 类注释）。
        if (mirrorByFacade()) {
            wfQueryService.updateInstance(upd, procInstId);
        }
        // 节点刷新后逐条给<b>本次新出现</b>的待办发提醒。只通知创建时刻落在办结窗内的任务——
        // 并联分支在办结前就已存在的兄弟待办早发过，不重发。
        if (notifyTodo) {
            long cutoff = System.currentTimeMillis() - 120_000L;
            for (Task t : open) {
                if (t.getCreateTime() == null || t.getCreateTime().getTime() < cutoff) {
                    continue;
                }
                notifyTodoToTargets(t, inst);
            }
        }
    }

    /**
     * 待办提醒收件人：assignee 有值 ⇒ 只发本人；无 assignee（候选组/POOL 态）⇒
     * 发镜像 candidate_ids 全体（无镜像行回退 {@code listOperableUserIds}），封顶 20 人防大角色群发。
     * <b>绝不</b>回落 approver/提单人：POOL 态把提醒发给 approver 等于催到提单人名下，
     * 其中个别人还被 deny_self 禁办，实际可办人一条都没有。
     * 只动提醒面：不套 deny_self、不加公司过滤——通知候选人≠授予办理权。
     */
    private List<Long> todoNotifyTargets(Task live) {
        List<Long> out = new ArrayList<>();
        if (live == null) {
            return out;
        }
        Long assignee = toUserIdOrNull(live.getAssignee());
        if (assignee != null) {
            out.add(assignee);
            return out;
        }
        try {
            WfTask mirror = wfQueryService.pendingTaskByTaskId(live.getId());
            if (mirror != null && mirror.getCandidateIds() != null) {
                for (String s : mirror.getCandidateIds().split(",")) {
                    Long id = toUserIdOrNull(s.trim());
                    if (id != null && !out.contains(id)) {
                        out.add(id);
                    }
                    if (out.size() >= 20) {
                        break;
                    }
                }
            }
            if (out.isEmpty()) {
                for (Long id : nodeOrgResolver.listOperableUserIds(live)) {
                    if (id != null && !out.contains(id)) {
                        out.add(id);
                    }
                    if (out.size() >= 20) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("待办提醒收件人解析失败 task={}: {}", live.getId(), ex.getMessage());
        }
        return out;
    }

    /** 给一条待办的全体收件人发"审批待办提醒"（规则：bizType+bizId 成对，可联查） */
    private void notifyTodoToTargets(Task live, WfInstance inst) {
        for (Long target : todoNotifyTargets(live)) {
            try {
                notifyService.notifyUser(target, "APPROVAL", "审批待办提醒",
                        "您有一条新的审批任务：【" + (inst == null || inst.getTitle() == null ? live.getName() : inst.getTitle())
                                + "】（流程：" + live.getName() + "）",
                        inst == null ? null : inst.getBizType(),
                        inst == null ? null : inst.getBizId(), "/approval");
            } catch (Exception ex) {
                log.warn("待办多渠道通知失败 user={} task={}: {}", target, live.getId(), ex.getMessage());
            }
        }
    }

    /**
     * 撤回告知：给撤回瞬间仍在办的每一节，按其<b>收件人规则</b>发一条窄规则站内信
     * 「单据已撤回，本环节无需处理」。
     *
     * <p>收件人复用 {@link #todoNotifyTargets(Task)} 而不是另起一套——同一条"这一节该谁办"的事实
     * 不允许有两处真值：assignee 有值只发本人，无 assignee（候选组/POOL 态）发全体候选人、封顶 20 人，
     * <b>绝不</b>回落 approver／提单人（回落会把信发到被 deny_self 禁办的人名下）。
     * 提单人本人排除：撤回是他自己点的动作，页面已即时反馈，再发一条＝自问自答。
     *
     * <p>{@code msg_type} 沿用 {@code APPROVAL}（前端 {@code MSG_TYPE} 已登记该码＝「审批通知」），
     * 不新增类型码——新增会让通知中心直接显示英文原码（{@code NotificationCenter.vue} 未登记类型的既有行为），
     * 修一条缺陷造出另一条显示缺陷。撤回语义写在标题与正文里，与转办／改派／退回同一做法。
     *
     * <p>单据坐标按 {@code (bizType, bizId)} 成对带出（规则），收件人可从通知联查该单；
     * 实例视图缺失时退回流程变量，两者都取不到则只发文本（不猜坐标）。
     */
    private void notifyWithdrawnToHandlers(List<Task> inFlight,
                                           WfInstance inst,
                                           Map<String, Object> vars, Long withdrawnBy) {
        if (inFlight == null || inFlight.isEmpty()) {
            return;
        }
        String billNo = inst == null ? null : inst.getBizNo();
        String docTitle = inst == null ? null : inst.getTitle();
        String bizType = inst != null && inst.getBizType() != null ? inst.getBizType()
                : (vars == null ? null : asString(vars.get("bizType")));
        Long bizId = inst != null && inst.getBizId() != null ? inst.getBizId()
                : (vars == null || !(vars.get("bizId") instanceof Number n) ? null : n.longValue());
        String doc = (billNo == null || billNo.isBlank() ? "" : "《" + billNo.trim() + "》")
                + (docTitle == null || docTitle.isBlank() ? "" : " " + docTitle.trim());
        Set<Long> notified = new HashSet<>();
        for (Task t : inFlight) {
            for (Long target : todoNotifyTargets(t)) {
                if (target == null || target.equals(withdrawnBy) || !notified.add(target)) {
                    continue;
                }
                try {
                    notifyService.notifyUser(target, "APPROVAL", "单据已撤回",
                            "您正在办理的单据【" + (doc.isBlank() ? String.valueOf(t.getName()) : doc.trim())
                                    + "】已由提单人撤回，节点「" + t.getName() + "」的任务已作废，本环节无需处理。",
                            bizType, bizId, "/approval");
                } catch (Exception ex) {
                    log.warn("撤回告知失败 user={} task={}: {}", target, t.getId(), ex.getMessage());
                }
            }
        }
    }

    /** 流程变量安全转字符串（null 保持 null，不产生 "null" 文案） */
    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 本（实例,节点）已投票者集合：记录动作 APPROVE/REJECT/AUTO_PASS 的操作人。
     * 不加 catch——审计查询失败时宁可拒绝加签，也不放开"同节点二次出票"。
     */
    private Set<Long> votersAtNode(String procInstId, String nodeKey) {
        Set<Long> ids = new LinkedHashSet<>();
        if (procInstId == null || nodeKey == null) {
            return ids;
        }
        for (WfTaskHistory h : wfQueryService.listHistory(
                new LambdaQueryWrapper<WfTaskHistory>()
                        .eq(WfTaskHistory::getProcInstId, procInstId)
                        .eq(WfTaskHistory::getNodeKey, nodeKey)
                        .in(WfTaskHistory::getAction, "APPROVE", "REJECT", "AUTO_PASS"))) {
            if (h.getOperUserId() != null) {
                ids.add(h.getOperUserId());
            }
        }
        return ids;
    }

    /**
     * 实例当前在办的人工任务列表（按创建时间稳定排序）。
     *
     * <p>并联网关（并联会审、资产/成本并联复审）扇出后，同一实例会同时挂多条人工任务，
     * Flowable 的 {@code singleResult()} 遇多行直接抛
     * {@code "Query return 3 results instead of max 1"}——AR_REPORT 三条会审支正好挂在
     * 启动瞬间，于是 start() 的视图写入数据库整段被 catch 吞掉，表现为"业务记录已生成、流程实例没有"。
     * 取"实例当前节点"一律走这条列表规则。
     */
    private List<Task> openTasks(String procInstId) {
        List<Task> list = taskService.createTaskQuery().processInstanceId(procInstId).list();
        if (list == null) {
            return Collections.emptyList();
        }
        if (list.size() > 1) {
            List<Task> sorted = new ArrayList<>(list);
            sorted.sort(Comparator
                    .comparing((Task t) -> t.getCreateTime() == null ? new Date(0) : t.getCreateTime())
                    .thenComparing(t -> String.valueOf(t.getId())));
            return sorted;
        }
        return list;
    }

    /** 并联扇出的兄弟任务各自走一遍三级取人（assignApprover 内含 SKIP 续跑＋镜像补账） */
    private void assignApprovers(String processKey, String procInstId) {
        for (int round = 0; round < 2; round++) {
            boolean progressed = false;
            for (Task t : openTasks(procInstId)) {
                // 镜像开关下，监听器（TASK_CREATED 投影）会先于三级解析给新任务建 PENDING 镜像行，
                // 此时 assignee 为空。若守卫写成「有 PENDING 镜像就跳过」，assignApprover 的三级解析
                // 连同 empty_strategy ESCALATE 兜底就一起被跳掉——候选组有组码、组内却解析不出真人时
                // （例如提单人＝顶级部门负责人本人，DIRECT_SUPERVISOR 判「无上级」），ASSIGNEE_ 永远为空、
                // 任务对所有人不可见、流程静默卡死（对账作业只报 WF-MIRROR-002 不修）。
                // 所以规则是：镜像已解析出真人（assignee_id 非空）才跳过；空 assignee 的镜像行仍走三级解析补派。
                WfTask mirror = wfQueryService.pendingTaskByTaskId(t.getId());
                if (mirror != null && mirror.getAssigneeId() != null) {
                    continue;
                }
                assignApprover(processKey, t);
                progressed = true;
            }
            if (!progressed) {
                return;
            }
        }
    }

    /** 实例视图 current_node 文案：并联在办时列出各分支节点名（列宽 200，超出截断） */
    private static String joinNodeNames(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            String name = t.getName() == null ? "" : t.getName();
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(name);
        }
        String all = sb.toString();
        return all.length() > 200 ? all.substring(0, 197) + "..." : all;
    }

    /**
     * 任务所属模型的 key（= 流程定义 key），用于灰度键按 model 白名单判定。
     * Flowable 的 Task 只有 processDefinitionId（形如 {@code key:version:deployId}），
     * 故优先读 wf_instance 的冗余列，取不到再截 definitionId。
     */
    private String modelKeyOf(Task task) {
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
            if (inst != null && inst.getModelKey() != null && !inst.getModelKey().isBlank()) {
                return inst.getModelKey();
            }
        } catch (Exception ex) {
            log.warn("读取任务所属 modelKey 失败 taskId={}: {}", task.getId(), ex.getMessage());
        }
        String defId = task.getProcessDefinitionId();
        return defId == null ? null : defId.split(":")[0];
    }

    /** {@link #modelKeyOf(Task)} 的实例规则：委托待办过滤按流程实例缓存复用，避免同一实例重复查库 */
    private String modelKeyOfProc(String procInstId) {
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst != null && inst.getModelKey() != null && !inst.getModelKey().isBlank()) {
                return inst.getModelKey();
            }
        } catch (Exception ex) {
            log.warn("读取流程实例 modelKey 失败 proc={}: {}", procInstId, ex.getMessage());
        }
        return null;
    }

    /**
     * 转办：把当前待办任务改派给其他用户办理（taskService.setAssignee）。
     * 权限：仅当前节点办理人/候选人/生效受托人可转办（与 complete 同一守卫）；
     * 不能转给本人。转办不产生审批意见节点推进，仅变更受理人并记录 TRANSFER 历史 + 通知被转办人。
     */
    public void transfer(String taskId, Long targetUserId, String comment, Long operUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        Long operator = operUserId != null ? operUserId
                : (UserContext.get() != null
                        ? UserContext.get().getUserId() : null);
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法转办此任务");
        }
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转办目标用户必填");
        }
        if (targetUserId.equals(operator)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把待办转办给自己");
        }
        // 目标必须是可办理的真人：只挡"必填/非本人"的话，单子可以转给停用或零角色用户，
        // 节点就成了无人可办的死节点。提单通道早有 sanitizeApprover 兜这一道，转办通道同样要有。
        assertAssignableUser(targetUserId, "转办");
        if (!nodeOrgResolver.canOperate(task, operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, operateDeniedMessage(task));
        }
        // allow_transfer 必须真的被读，存了不读等于伪配置。现网 602 个节点全为 1，
        // 所以这道校验不影响现有流程。只管人工转办；超时自动转办由节点的 timeout_action 决定，
        // 是另一条通道。
        WfNodeConfig transferCfg = wfNodeConfigResolver.resolve(modelKeyOf(task), task.getTaskDefinitionKey());
        if (transferCfg != null && Integer.valueOf(0).equals(transferCfg.getAllowTransfer())) {
            throw new BizException(ErrorCode.FORBIDDEN, "节点「"
                    + (transferCfg.getNodeName() == null || transferCfg.getNodeName().isBlank()
                            ? task.getTaskDefinitionKey() : transferCfg.getNodeName())
                    + "」未开放转办（流程配置「允许转办」=否）；确需换人请联系流程管理员使用「改派」");
        }
        // 转办目标收紧到本节点可办理名单（与 /task-candidates 同一规则）。
        // 灰度键 approve.transfer_strict.enabled/models 默认关，此时不校验；
        // 开启前提是改派已可用（wf:task:reassign），否则"唯一候选人＝发起人"的死锁会失去出口。
        if (isConfigGatedOn("approve.transfer_strict.enabled", "approve.transfer_strict.models",
                modelKeyOf(task))) {
            List<Long> operable = nodeOrgResolver.listTransferTargets(task, operator);
            if (operable == null || operable.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "本节点没有可转办的其他人，请联系流程管理员使用「改派」处理");
            }
            if (!operable.contains(targetUserId)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "转办目标必须从本节点可办理名单中选择（与转办弹窗同一规则）；"
                                + "确需派给名单外的人（换岗/离职/本岗位只有你一人）请联系流程管理员使用「改派」");
            }
        }
        taskService.setAssignee(taskId, String.valueOf(targetUserId));
        // 待办/实例视图双写办结（与领取共用）：PENDING 行改派、缺行补账、实例当前办理人同步
        syncTaskViewAfterAssigneeChange(task, targetUserId, "转办");
        // 用 8 参版本把被转办人写进 target_user_id：只留自由文本"转办给用户 123"的话，
        // 无法按人检索"这单是谁转给谁的"。操作人取已解析的 operator（入参 operUserId 在机器路径下为 null）。
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "TRANSFER", operator, targetUserId,
                comment == null || comment.isBlank() ? "转办给用户 " + targetUserId : comment);
        // 多渠道通知被转办人
        try {
            notifyService.notifyUser(targetUserId, "APPROVAL", "转办待办提醒",
                    "【" + task.getName() + "】有一条转办任务，请及时处理",
                    null, "/approval");
        } catch (Exception ex) {
            log.warn("转办通知失败 target={}: {}", targetUserId, ex.getMessage());
        }
    }

    /**
     * 本节点可转办名单（转办弹窗数据源）：规则与 {@link #transfer} 的目标校验完全一致
     * （{@code NodeOrgResolver.listTransferTargets}＝可办理名单 ∪ canOperate 放行规则补齐 ∪ 生效受托人），
     * 再排除操作人本人。名单为空＝"本节点没有别人可办"，
     * 前端此时引导找流程管理员改派，而不是放开全局选人。
     *
     * <p>名单含他人姓名与在办量，属越权信息，故与 {@code /next-candidates} 同一守卫：只有本任务
     * 办理人/候选人/生效受托人可查。
     */
    public Map<String, Object> taskCandidates(String taskId, Long operator) {
        Map<String, Object> out = new HashMap<>();
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法查看此任务的候选人");
        }
        if (!nodeOrgResolver.canOperate(task, operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, "非当前节点办理人/候选人，无权查看此任务候选人");
        }
        out.put("nodeName", task.getName());
        out.put("nodeKey", task.getTaskDefinitionKey());
        List<Long> ids = new ArrayList<>();
        for (Long id : nodeOrgResolver.listTransferTargets(task, operator)) {
            if (!operator.equals(id)) {
                ids.add(id);
            }
        }
        out.put("candidates", describeUsers(ids));
        out.put("empty", ids.isEmpty());
        return out;
    }

    /**
     * 领取任务（POOL 任务池档 / 抢单）：候选人把"仅候选组"态的任务
     * 锁定到自己名下，之后由本人办理。
     *
     * <p>与转办的区别：转办是<b>换人</b>（需要目标人、写 TRANSFER），领取是<b>认领</b>（不选人、
     * 只能认领给自己，写 CLAIM）。用 {@code taskService.claim} 而非 setAssignee：Flowable 的 claim 在
     * 任务已被他人认领时抛 {@code FlowableTaskAlreadyClaimedException}，天然给出并发抢单的败者语义
     * （setAssignee 是后点覆盖先点，两个人会各自以为拿到了同一张单）。
     *
     * <p>守卫：① 必须是本节点可办理的人（与 {@code /task-candidates} 同一规则的候选判定，含公司维度）；
     * ② 任务已被指派给他人 → 明确提示"已被领取"；③ deny_self 开着时不放开给单据提交人
     * （否则领取后 complete 立刻被自审拦截，等于把人锁死在办不动的单子上）；POOL 作业池档不适用③——
     * 作业节点不属自审规则，提单人是合法候选人。
     */
    public void claim(String taskId, Long operUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        Long operator = operUserId != null ? operUserId
                : (UserContext.get() != null
                        ? UserContext.get().getUserId() : null);
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法领取此任务");
        }
        String uid = String.valueOf(operator);
        if (uid.equals(task.getAssignee())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该任务已在你名下，无需重复领取");
        }
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该任务已由他人领取或指派，请刷新待办列表");
        }
        List<Long> operable = nodeOrgResolver.listOperableUserIds(task);
        if (operable == null || !operable.contains(operator)) {
            throw new BizException(ErrorCode.FORBIDDEN, "你不是本节点的候选办理人，无权领取此任务");
        }
        if (isDenySelfEnabled() && !nodeOrgResolver.isPoolTask(task)
                && operator.equals(nodeOrgResolver.resolveApplyUserId(task.getProcessInstanceId()))) {
            long doneTasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId()).finished().count();
            if (doneTasks > 0) {
                throw new BizException(ErrorCode.FORBIDDEN, "禁止领取自己提交单据的审批节点（自审拦截）");
            }
        }
        try {
            taskService.claim(taskId, uid);
        } catch (FlowableTaskAlreadyClaimedException ex) {
            throw new BizException(ErrorCode.FORBIDDEN, "该任务已由他人领取，请刷新待办列表");
        } catch (FlowableOptimisticLockingException
                 | FlowableObjectNotFoundException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务已被处理，请刷新待办");
        }
        syncTaskViewAfterAssigneeChange(task, operator, "领取");
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "CLAIM", operator, operator, "领取任务（任务池认领）");
    }

    /**
     * 释放任务（取消领取）：把任务退回候选组态，同池其他人可再领取。
     *
     * <p>只有领取人本人可释放（管理员换人走改派 L2）。释放前必须确认"退回后仍有人可办"
     * （{@code listOperableUserIds} 非空），否则会人为制造一个无人可办的死节点。
     */
    public void unclaim(String taskId, Long operUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        Long operator = operUserId != null ? operUserId
                : (UserContext.get() != null
                        ? UserContext.get().getUserId() : null);
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法释放此任务");
        }
        if (!String.valueOf(operator).equals(task.getAssignee())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能释放自己领取的任务");
        }
        // （服务端环节，与待办列表 canUnclaim 同口径）：非池节点没有"释放回池"这回事。
        // AUTO/PICK 档的 assignee 是节点配置解析出来的（指派到人即最终状态），unclaim 之后
        // 该节点既无候选组可回退、也没有第二个人会去领取，单据就地卡死（列表里消失、SLA 无人接）。
        // 直接复用 NodeOrgResolver.isPoolTask（handling_mode=POOL），不在本类另造一套规则；
        // 换人的正当通道是「转办」与管理员「改派」，两者都有记录、都不改变节点归属。
        if (!nodeOrgResolver.isPoolTask(task)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本节点按配置直接指派办理人，不存在任务池；如需换人请用转办或改派");
        }
        List<Long> operable = nodeOrgResolver.listOperableUserIds(task);
        if (operable == null || operable.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本节点已无可领取的其他办理人，释放后单据将无人可办；确需换人请使用「转办」或联系流程管理员「改派」");
        }
        // 回收（释放）后打池局部标记——该任务不参与智能推送，仅可人工领取
        try { taskService.setVariableLocal(taskId, "pool_no_auto_assign", Boolean.TRUE); } catch (Exception ex) { log.warn("回收标记失败 taskId={}: {}", taskId, ex.getMessage()); }
        taskService.unclaim(taskId);
        try {
            wfQueryService.clearTaskAssignee(taskId);
        } catch (Exception ex) {
            log.error("释放待办视图回写失败 taskId={}", taskId, ex);
        }
        try {
            WfInstance inst =
                    wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
            if (inst != null && "RUNNING".equals(inst.getStatus())) {
                WfInstance upd = new WfInstance();
                // 置空串而非 null：MyBatis-Plus 的 update(entity, wrapper) 会忽略 null 字段，
                // 用 null 释放不掉旧的"当前办理人"（表现是释放成功但列表仍显示原领取人）。
                upd.setCurrentAssignee("");
                wfQueryService.updateInstance(upd, task.getProcessInstanceId());
            }
        } catch (Exception ex) {
            log.warn("释放实例视图同步失败 taskId={}: {}", taskId, ex.getMessage());
        }
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "UNCLAIM", operator, null, "释放任务（退回任务池）");
    }

    /**
     * 办理人变更后的待办视图双写办结（转办/领取共用）：有 PENDING 行则改派，缺行则补账
     * （历史在途任务的镜像缺失是常态，不能因为没行就丢掉新的办理人）。
     */
    private void syncTaskViewAfterAssigneeChange(Task task, Long assignee, String label) {
        try {
            if (wfQueryService.pendingTaskExists(task.getId())) {
                // 转办/领取/改派都先经 taskService.setAssignee，监听器 TASK_ASSIGNED 已按事件投影同一改派；
                // 此处保留为过渡期双写（去重、同值），以覆盖"监听器未注册期间就已存在的在途任务"。
                wfQueryService.updateTaskAssignee(task.getId(), assignee);
            } else if (mirrorByFacade()) {
                // 缺行补账在开关打开时由监听器 TASK_CREATED 负责；门面不再补，
                // 否则会与投影争抢 wf_task 的唯一写入者身份（缺行＝投影失败，应由对账器暴露而非门面静默补齐）。
                recordTaskMirror(modelKeyOf(task), taskService.createTaskQuery()
                        .taskId(task.getId()).singleResult());
            }
        } catch (Exception ex) {
            log.error("{}待办视图回写失败 taskId={} assignee={}", label, task.getId(), assignee, ex);
        }
        try {
            // 实例视图的 current_assignee 由监听器 TASK_ASSIGNED 投影；门面在开关打开时
            // 不再写，否则两个写入者争抢同一行（本块与上方 updateTaskAssignee 的区别：那条是过渡期有意双写，
            // 覆盖"监听器注册期间之前就已存在的在途任务"，本块无此必要）。
            if (mirrorByFacade()) {
                WfInstance inst =
                        wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
                if (inst != null && "RUNNING".equals(inst.getStatus())) {
                    WfInstance upd = new WfInstance();
                    upd.setCurrentAssignee(String.valueOf(assignee));
                    wfQueryService.updateInstance(upd, task.getProcessInstanceId());
                }
            }
        } catch (Exception ex) {
            log.warn("{}实例视图同步失败 taskId={}: {}", label, task.getId(), ex.getMessage());
        }
    }

    /**
     * 改派（管理员通道）：流程管理员把在办任务改指给任意可办理的真人。
     *
     * <p>与转办的差别只在**授权来源**：转办要求操作人是本节点办理人/候选人，改派要求操作人持
     * {@code wf:task:reassign} 权限点（Controller 的 {@code @RequiresPerm} 已校验），因此此处不做
     * {@code canOperate}，允许派给不在候选组的人——这正是"离职/换岗/唯一候选人是发起人本人"三条死路的出口
     * （「流程移交」这一类诉求就落在这条通道上）。
     *
     * <p>目标侧仍然硬校验：存在 / 在职 / 非 admin / 至少一个角色；并且 deny_self 开着时不派给该单据提交人
     * （否则落地即被自审拦截＝又做一个办不动的节点）；POOL 作业池档不适用（作业不属自审规则）。
     * 原因必填，写 REASSIGN 历史（带 target_user_id）可追溯。
     */
    public void reassign(String taskId, Long targetUserId, String reason, Long operUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        Long operator = operUserId != null ? operUserId
                : (UserContext.get() != null
                        ? UserContext.get().getUserId() : null);
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法改派此任务");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "改派必须填写原因（如：原审批人离职/换岗/无人可办）");
        }
        assertAssignableUser(targetUserId, "改派");
        if (String.valueOf(targetUserId).equals(task.getAssignee())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该任务已在目标用户名下，无需改派");
        }
        if (isDenySelfEnabled() && !nodeOrgResolver.isPoolTask(task)
                && targetUserId.equals(nodeOrgResolver.resolveApplyUserId(task.getProcessInstanceId()))) {
            long doneTasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId()).finished().count();
            if (doneTasks > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "改派目标为该单据提交人，禁止自审；请选本节点其他可办理人");
            }
        }
        Long previous = null;
        try {
            previous = task.getAssignee() == null ? null : Long.valueOf(task.getAssignee().trim());
        } catch (NumberFormatException ignored) {
            // 非数字 assignee（历史遗留/外部集成）：记录时按空处理
        }
        taskService.setAssignee(taskId, String.valueOf(targetUserId));
        // 待办/实例视图双写办结（与转办/领取共用）：改派是历史在途单（无镜像行）最常见的补账入口
        syncTaskViewAfterAssigneeChange(task, targetUserId, "改派");
        writeHistory(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), "REASSIGN", operator, targetUserId,
                reason + "（原办理人：" + (previous == null ? "候选组待领取" : previous) + "）");
        try {
            notifyService.notifyUser(targetUserId, "APPROVAL", "改派待办提醒",
                    "【" + task.getName() + "】有一条任务由流程管理员改派给您，请及时处理",
                    null, "/approval");
        } catch (Exception ex) {
            log.warn("改派通知失败 target={}: {}", targetUserId, ex.getMessage());
        }
        log.info("流程改派: taskId={} node={} {} -> {} operator={} reason={}",
                taskId, task.getName(), previous, targetUserId, operator, reason);
    }

    /**
     * 驳回类型判断辅助：默认按"逐级驳回"处理。
     * 调用方可显式传 THROW_BACK（漏单驳回）走 5 参 complete。
     */
    private String rejectTypeOf(boolean approved, String comment) {
        return approved ? null : "GRADE_BY_GRADE";
    }

    /**
     * 超时自动通过（机器路径）：与人工审批同一套办结——引擎 complete → 待办镜像置 DONE
     * → AUTO_PASS 记录 → 下一节点三级取人并推进实例视图。
     *
     * <p>不能只直连 {@code taskService.complete()} 然后把手上这条镜像行置 DONE：推进出来的下一节点
     * 没人解析取人、也没有镜像行，单子"自动通过之后就从所有待办里消失"；
     * 且不留审批历史＝审批链凭空少一节，事后无法解释这单为何到了下一环节。
     *
     * @param operUserId 超时时该节点的审批人（候选组未认领时为 null，仅作记录）
     * @param reason 记录意见，如"节点 SLA 24 小时，已超时 6 小时"
     */
    public void autoPassByTimeout(String taskId, Long operUserId, String reason) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return; // 扫描与执行之间已被人工办结：机器路径让路
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", true);
        vars.put("comment", reason);
        // 与人工办结环节同源——PROCESS_COMPLETED 监听器在 complete() 内同步派发，
        // 机器路径同样要先登记"本线程正在办结哪条任务"，否则超时自动通过的收尾任务会被写成假 VOID。
        String completingPrev = WfQueryService.beginCompletingTask(taskId);
        // 与人工办结环节同源带出操作人。本形参在候选组未认领时本就为 null（仅记录用），
        // 结束事件随之为空——不猜人。
        Long endOperPrev = beginEndOperator(operUserId);
        try {
            taskService.complete(taskId, vars);
        } catch (FlowableOptimisticLockingException ex) {
            log.info("超时自动通过遇并发办结，跳过 taskId={}", taskId);
            return;
        } finally {
            endEndOperator(endOperPrev);
            WfQueryService.endCompletingTask(completingPrev);
        }
        try {
            // 本行置 DONE 已由监听器 TASK_COMPLETED 投影（上方 taskService.complete 触发）；
            // 开关打开时门面停写，避免两个写入者对同一条 wf_task 行重复置最终状态。
            if (mirrorByFacade()) {
                wfQueryService.markTaskDone(taskId, operUserId);
            }
        } catch (Exception ex) {
            log.error("超时自动通过后待办视图回写失败 taskId={}", taskId, ex);
        }
        writeHistory(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                task.getName(), "AUTO_PASS", operUserId, null, reason);
        try {
            // 超时自动通过后推进出的下一节点同样要发待办提醒——机器路径不能漏掉这一步
            syncInstanceAfterAdvance(task.getProcessInstanceId(), true);
        } catch (Exception ex) {
            log.error("超时自动通过后流程推进失败 taskId={} procInstId={}", taskId, task.getProcessInstanceId(), ex);
        }
    }

    /**
     * 超时升级转办（机器路径）：改派给真实上级，并补齐 ESCALATE 记录 + 双账同步 + 通知。
     *
     * <p>与人工改派（{@link #reassign}）共用目标人硬校验（存在/在职/非 admin/至少一个角色）与
     * 视图办结 {@link #syncTaskViewAfterAssigneeChange}，差别只在授权来源：定时任务没有登录态，
     * 也不做 canOperate（扫描线程本来就不是办理人）。
     */
    public void escalateByTimeout(String taskId, Long targetUserId, String reason) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return;
        }
        Long previous = toUserIdOrNull(task.getAssignee());
        if (targetUserId == null || targetUserId.equals(previous)) {
            return; // 没有可派的人或已在该人名下：交给调用方退化为催办，此处不制造空转
        }
        assertAssignableUser(targetUserId, "超时升级");
        taskService.setAssignee(taskId, String.valueOf(targetUserId));
        syncTaskViewAfterAssigneeChange(task, targetUserId, "超时升级");
        writeHistory(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(), task.getName(),
                "ESCALATE", previous, targetUserId,
                reason + "（原审办人：" + (previous == null ? "候选组待领取" : previous) + "）");
        try {
            notifyService.notifyUser(targetUserId, "TIMEOUT_ESCALATED", "审批任务超时转办通知",
                    "审批任务「" + task.getName() + "」已超时，系统已升级转办给您，请尽快处理。",
                    null, "/approval");
        } catch (Exception ex) {
            log.warn("超时升级通知失败 target={}: {}", targetUserId, ex.getMessage());
        }
    }

    /**
     * 超时自动回收作业任务（机器路径）：把领取人卡住的 POOL 任务退回任务池，同池他人可再领取。
     *
     * <p>与人工「释放」的区别是不校验操作人（定时任务线程没有登录态），但走同一套双账回写与记录，
     * 动作记 {@code TIMEOUT_RELEASE} 以便与本人主动释放（{@code UNCLAIM}）区分。
     */
    public void releaseByTimeout(String taskId, Long ownerUserId, String reason) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return; // 扫描与执行之间已被人工办结：机器路径让路
        }
        taskService.unclaim(taskId);
        // 回池后必须保留/重建候选组——unclaim 只清 assignee，若任务创建时未落
        // ACT_RU_IDENTITY_LINK（候选由 NodeOrgResolver 运行态解析的组态），回池后
        // taskCandidateGroupIn 查不到任何组 ⇒ 任务对所有 pool 成员不可见、任何人 403 无法领取，
        // 成为孤儿任务。此处按节点组态回补候选组（assign_type=ROLE）。
        restorePoolCandidates(taskId, task.getProcessInstanceId(), task.getTaskDefinitionKey());
        try {
            // 清办理人已由监听器 TASK_ASSIGNED（unclaim 触发）投影；开关打开时门面停写。
            if (mirrorByFacade()) {
                wfQueryService.clearTaskAssignee(taskId);
            }
        } catch (Exception ex) {
            log.error("超时回收待办视图回写失败 taskId={}", taskId, ex);
        }
        try {
            // 实例视图同理由投影接管，门面在开关打开时整块跳过。
            if (mirrorByFacade()) {
                WfInstance inst =
                        wfQueryService.instanceByProcInstId(task.getProcessInstanceId());
                if (inst != null && "RUNNING".equals(inst.getStatus())) {
                    WfInstance upd = new WfInstance();
                    // 置空串而非 null：MyBatis-Plus 的 update(entity, wrapper) 会忽略 null 字段
                    upd.setCurrentAssignee("");
                    wfQueryService.updateInstance(upd, task.getProcessInstanceId());
                }
            }
        } catch (Exception ex) {
            log.warn("超时回收实例视图同步失败 taskId={}: {}", taskId, ex.getMessage());
        }
        writeHistory(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(), task.getName(),
                "TIMEOUT_RELEASE", ownerUserId, null, "超时自动回收：" + reason);
        if (ownerUserId != null) {
            try {
                notifyService.notifyUser(ownerUserId, "TIMEOUT_REMINDER", "作业任务已超时回收",
                        "您领取的任务「" + task.getName() + "」超时未办理，已退回共享任务池由同池同事领取。",
                        null, "/approval");
            } catch (Exception ex) {
                log.warn("超时回收通知失败 owner={}: {}", ownerUserId, ex.getMessage());
            }
        }
    }

    /**
     * POOL 任务回池后按节点组态回补候选组（assign_type=ROLE → addCandidateGroup）。
     *
     * <p>背景：CLC_1（RECEIVABLE_COLLECT）超时回收后 ACT_RU_IDENTITY_LINK 0 行——
     * taskCandidateGroupIn 查不到组 ⇒ 该任务对所有 pool 成员不可见、任何人领取/办理 403，
     * 成为永久孤儿（只有流程管理员 reassign 可救）。本方法失败仅记日志，绝不阻断回收主链。
     */
    private void restorePoolCandidates(String taskId, String procInstId, String nodeKey) {
        try {
            WfTask mirror = wfQueryService.pendingTaskByTaskId(taskId);
            String modelKey = mirror != null ? mirror.getModelKey() : null;
            if (modelKey == null || modelKey.isBlank()) {
                // 镜像缺失时从流程定义反推（processDefinitionKey 即 modelKey）
                var pi = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(procInstId).singleResult();
                modelKey = pi == null ? null : pi.getProcessDefinitionKey();
            }
            if (modelKey == null || nodeKey == null) {
                return;
            }
            WfNodeConfig cfg = wfNodeConfigResolver.resolve(modelKey, nodeKey);
            if (cfg == null || !"ROLE".equalsIgnoreCase(cfg.getAssignType())
                    || cfg.getAssignValue() == null || cfg.getAssignValue().isBlank()) {
                return;
            }
            for (String rc : cfg.getAssignValue().split(",")) {
                if (!rc.isBlank()) {
                    taskService.addCandidateGroup(taskId, rc.trim());
                }
            }
        } catch (Exception ex) {
            log.warn("回池候选组回补失败（不阻断回收）: taskId={}, node={}, err={}", taskId, nodeKey, ex.getMessage());
        }
    }

    /**
     * 自审禁止开关（fail-closed）：
     * <ul>
     *  <li>sys_config.approve.deny_self = '1' → 开启（禁止审批本人提交的单据）；</li>
     *  <li>配置缺失（无记录）→ <b>默认开启</b>（fail-closed：宁可禁止，不静默放行）；</li>
     *  <li>配置显式为 '0' → 关闭（兼容历史演示数据）；</li>
     *  <li>读取异常 → 默认开启（fail-closed）。</li>
     * </ul>
     *
     * <p><b>缺省方向与同人连办保持统一</b>：本方法与 {@link #isSerialSameHandlerDenied(String)}
     * 必须同向——同一份内控语义在两条校验上给出相反答案，就没法解释也没法验收。
     * 统一为 <b>fail-closed（缺省即拦截/禁止）</b>：财务内控宁可少做不可误放。
     * 两条开关的当前实际生效值已显式 seed 进 {@code sys_config}，缺省分支正常不会走到。</p>
     */
    /** 系统自动审核身份（sysauto）判定 */
    private boolean isSystemAutoAuditor(Long userId) {
        if (userId == null) return false;
        try {
            SysUser u = sysUserMapper.selectById(userId);
            return u != null && "sysauto".equals(u.getUserName());
        } catch (Exception ex) {
            return false;
        }
    }

    /** 智能推送开关：pool.autoDispatch（缺省=1 开启；抢单兜底） */
    private boolean isPoolAutoDispatchEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, "pool.autoDispatch")
                            .last("limit 1"));
            if (c == null || c.getConfigValue() == null) return true;
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.warn("读取 pool.autoDispatch 异常，默认开启: {}", ex.getMessage());
            return true;
        }
    }

    /**
     * 实例的业务类型（连办校验预检查询用）：按镜像视图 {@code wf_instance.biz_type} 取，
     * 与审批校验 {@code complete()} 用的 {@code self.getBizType()} 同一来源；取不到返回 null，
     * {@link #isSerialSameHandlerDenied(String)} 对 null 一律放行 ⇒ 预检退化，不影响指派。
     */
    private String bizTypeOfInstance(String procInstId) {
        if (procInstId == null) {
            return null;
        }
        try {
            WfInstance inst = wfQueryService.instanceByProcInstId(procInstId);
            return inst == null ? null : inst.getBizType();
        } catch (Exception ex) {
            log.debug("取实例 bizType 失败 procInstId={}: {}", procInstId, ex.getMessage());
            return null;
        }
    }

    /**
     * 该用户在本实例<b>已经办结过节点</b>？判断与连办校验 {@code complete()} 内那条
     * （{@code historicTaskInstanceQuery.processInstanceId(...).taskAssignee(uid).finished().count()>0}）
     * <b>逐字同一</b>——预检必须与校验规则一致，否则"预检放行、审批被拒"或"预检误杀、能办不给办"。
     *
     * <p>刻意不做成公共方法去替换 complete() 里的那一段：连办校验那段一字不动，
     * 这里只是把同一判断在读侧再算一次，且只在校验生效时才会被调用。
     */
    private boolean handledNodesInInstance(String procInstId, Long userId) {
        if (procInstId == null || userId == null) {
            return false;
        }
        try {
            return historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(procInstId)
                    .taskAssignee(String.valueOf(userId)).finished().count() > 0;
        } catch (Exception ex) {
            // 读取失败按"没办过"处理：预检是收紧推送，不是收紧办理权限，误判的后果只是推送范围放宽一点
            log.debug("连办预检查历史失败 procInstId={} userId={}: {}", procInstId, userId, ex.getMessage());
            return false;
        }
    }

    private boolean isDenySelfEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, "approve.deny_self")
                            .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return true; // 配置缺失 → 默认禁止自审（fail-closed）
            }
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.error("读取 approve.deny_self 异常，fail-closed 默认禁止自审: {}", ex.getMessage(), ex);
            return true;
        }
    }

    /**
     * 同人连办拦截开关：sys_config.approve.deny_serial_same_handler 指定生效流程链。
     * <ul>
     *  <li>缺失（无记录）→ <b>拦截</b>（缺省方向与 {@link #isDenySelfEnabled()} 统一为
     *      fail-closed「缺省即拦截」）；</li>
     *  <li>空串 / '0' → 不拦截（当前实际生效值，已显式 seed 进 sys_config）；</li>
     *  <li>'1' 或 'ALL' → 对所有流程启用；</li>
     *  <li>逗号分隔 bizType（如 'GL_MONTH_CLOSE'）→ 仅对该流程链启用（月结链铺办理人+禁 admin 的强制兜底）；</li>
     *  <li>读取异常 → fail-closed，但<b>只对代码层显式登记的强制流程链</b>
     *      {@link #SERIAL_SAME_HANDLER_ENFORCED_BIZTYPES} 生效（见下方取舍说明）。</li>
     * </ul>
     * 与 deny_self 的区别：deny_self 仅拦「提交人审自己」，本开关拦「同一人办理同一实例的多个审批节点」（连办）。
     *
     * <p><b>为什么异常分支不做"全量 fail-closed"</b>：本方法是审批完成链路上的校验，若一次 DB 抖动
     * 就对<b>所有</b> bizType 一律拦截，会把全量在途审批瞬间打成 403（大面积误伤，比"误放"更难恢复），
     * 与内控"宁可少做不可误放"的目标相比代价失衡。故异常时收窄为"只拦代码层显式声明为强制启用的链"，
     * 其余仍放行——这是本条唯一的行为增量，且仅在 sys_config 读取抛异常时触发。</p>
     */
    private boolean isSerialSameHandlerDenied(String bizType) {
        if (bizType == null) {
            return false;
        }
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, "approve.deny_serial_same_handler")
                            .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                // 缺省方向统一为 fail-closed（与 approve.deny_self 一致）。
                // 历史库已有该行（值为空串）⇒ 本分支不改变运行结果；见类上方 javadoc 与治理脚本。
                log.warn("approve.deny_serial_same_handler 无配置行，按统一缺省方向 fail-closed 拦截 bizType={}", bizType);
                return true;
            }
            String v = c.getConfigValue().trim();
            if (v.isEmpty() || "0".equals(v)) {
                return false;
            }
            if ("1".equalsIgnoreCase(v) || "ALL".equalsIgnoreCase(v)) {
                return true;
            }
            for (String t : v.split(",")) {
                if (t.trim().equalsIgnoreCase(bizType)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.error("读取 approve.deny_serial_same_handler 异常，fail-closed 仅对显式登记流程链拦截 bizType={}: {}",
                    bizType, ex.getMessage(), ex);
            return SERIAL_SAME_HANDLER_ENFORCED_BIZTYPES.contains(bizType);
        }
    }

    /**
     * 同人连办拦截在<b>代码层显式登记</b>的强制流程链（与 sys_config 值同形：bizType）。
     *
     * <p>仅在 sys_config <b>读取异常</b>时作为 fail-closed 的收窄依据——配置可读时一律以配置为准，
     * 本常量不参与判定。登记规则＝"该链的办理人铺法与 admin 排除是强制兜底、宁可停也不放行"。</p>
     */
    private static final Set<String> SERIAL_SAME_HANDLER_ENFORCED_BIZTYPES =
            Set.of("GL_MONTH_CLOSE");

    /** 按任务 ID 反查流程实例 ID；任务已办结时退回历史表（撤销预览的入参便利方法）。 */
    public String procInstIdOfTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (t != null) {
            return t.getProcessInstanceId();
        }
        HistoricTaskInstance h =
                historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        return h == null ? null : h.getProcessInstanceId();
    }

    /**
     * 撤销预览入参解析：Flowable 的实例 ID 与任务 ID 都是数字串，<b>无法按格式区分</b>，
     * 故先与实例视图核对，未命中再按任务 ID 反查。
     *
     * <p>两者都不命中时<b>原样返回</b>，让预览接口给出"空壳 + 空链路"而不是 400 ——
     * 前端据此提示"未找到该流程"，比一个参数校验错误更好排查。
     */
    public String resolvePreviewTarget(String idOrTaskId) {
        if (idOrTaskId == null || idOrTaskId.isBlank()) {
            return null;
        }
        String k = idOrTaskId.trim();
        try {
            if (wfQueryService.instanceByProcInstId(k) != null) {
                return k;
            }
        } catch (Exception ignore) {
            // 视图查不到就落到任务反查
        }
        String pi = procInstIdOfTask(k);
        return pi != null ? pi : k;
    }

    /**
     * 「已办撤销回原节点」<b>只读预览</b>。
     *
     * <p><b>只做预览、不做执行</b>：Flowable 8 <b>没有</b>
     * {@code ProcessInstanceModification}（javap 核对 flowable-engine-8.0.0.jar 确认），
     * 等价原语是 {@code ChangeActivityStateBuilder.moveActivityIdTo} —— 它属于「搬执行」：
     * 直接把运行中的执行实例挪到目标活动，会真实改动在办单据本体（作废既有任务、重放历史流转）。
     * 改错的代价落在单据本身而不是日志上 ⇒ 本方法<b>只读、零副作用</b>，只回答
     * "如果把这张单退回到某个节点重办，会发生什么"，供人工核对与后续开关放行。
     *
     * <p>开关 {@code sys_config.wf.withdraw.done.enabled} 缺省 0（关闭，fail-closed）。
     * 无论开关如何，本方法都<b>不</b>修改任何数据。
     *
     * @return 单据信息 + 在办任务 + 可回退节点（每项列出该节点重办将作废的后续节点）
     */
    public Map<String, Object> withdrawPreview(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "procInstId 不能为空");
        }
        WfInstance inst = null;
        try {
            inst = wfQueryService.instanceByProcInstId(procInstId);
        } catch (Exception ex) {
            log.warn("撤销预览：查实例失败 procInst={}", procInstId, ex);
        }
        List<Map<String, Object>> chain;
        try {
            chain = wfQueryService.chain(procInstId);
        } catch (Exception ex) {
            log.warn("撤销预览：查审批链失败 procInst={}", procInstId, ex);
            chain = Collections.emptyList();
        }
        // 在办任务取引擎真实态（不只信 wf_task 镜像 —— 镜像有过滞后缺陷）
        List<Map<String, Object>> active = new ArrayList<>();
        try {
            for (Task t : taskService.createTaskQuery().processInstanceId(procInstId).active().list()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("taskId", t.getId());
                m.put("nodeKey", t.getTaskDefinitionKey());
                m.put("nodeName", t.getName());
                m.put("assignee", t.getAssignee());
                active.add(m);
            }
        } catch (Exception ex) {
            log.warn("撤销预览：查在办任务失败 procInst={}", procInstId, ex);
        }

        // 可回退节点 = 链上「已办」节（DONE/REJECTED）。规则取「退回到该节点重办」：
        // 该节点本身需重办，其后所有节点（含未到达的 TODO）都会被作废后重新流转。
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Map<String, Object> n = chain.get(i);
            String state = String.valueOf(n.get("state"));
            if (!"DONE".equals(state) && !"REJECTED".equals(state)) {
                continue;
            }
            List<String> willVoid = new ArrayList<>();
            for (int j = i + 1; j < chain.size(); j++) {
                willVoid.add(String.valueOf(chain.get(j).get("nodeName")));
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("nodeKey", n.get("nodeKey"));
            c.put("nodeName", n.get("nodeName"));
            c.put("handler", n.get("handler"));
            c.put("time", n.get("time"));
            c.put("action", n.get("action"));
            c.put("willReopenSelf", true);
            c.put("willVoidNodes", willVoid);
            c.put("willVoidCount", willVoid.size());
            candidates.add(c);
        }

        boolean enabled = isDoneWithdrawEnabled();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("procInstId", procInstId);
        out.put("modelKey", inst == null ? null : inst.getModelKey());
        out.put("bizType", inst == null ? null : inst.getBizType());
        out.put("bizId", inst == null ? null : inst.getBizId());
        out.put("bizNo", inst == null ? null : inst.getBizNo());
        out.put("title", inst == null ? null : inst.getTitle());
        out.put("instanceStatus", inst == null ? null : inst.getStatus());
        out.put("currentNode", inst == null ? null : inst.getCurrentNode());
        out.put("enabled", enabled);
        out.put("enabledHint", enabled
                ? "「已办撤销」开关已开启（sys_config.wf.withdraw.done.enabled=1）；但执行面仍未开放，本页依旧只读"
                : "「已办撤销」默认关闭（sys_config.wf.withdraw.done.enabled 未置 1）；本页为只读预览");
        out.put("activeTasks", active);
        out.put("candidates", candidates);
        out.put("notice", "只读预览：本接口不修改任何流程数据。「已办撤销回原节点」的执行面尚未开放 —— "
                + "Flowable 8 无 ProcessInstanceModification，等价原语 moveActivityIdTo 属「搬执行」，"
                + "会真实改动在办单据本体，须先经本预览核对影响面、再由开关放行。");
        return out;
    }

    // 自由驳回（任意级次回退）
    // 能力边界：Flowable 8 无 ProcessInstanceModification；把执行退回上游节点的等价原语是
    // ChangeActivityStateBuilder.moveActivityIdTo —— 属「搬执行」，会真实改动在办单据本体。
    // 规则与「已办撤销」完全一致：只读预览先行（rejectTargets）+ 开关 fail-closed 默认关。
    // 开关未开时 complete() 走 terminateAsRejected。

    /**
     * 「自由驳回」只读预览：给定当前待办任务，列出可退回的上游节点及各自影响面。
     *
     * <p>纯查询，不修改任何数据。候选集与 {@code withdrawPreview} 共用同一条链规则
     * （{@code wfQueryService.chain} 的 DONE/REJECTED 节），不另造一套"哪些节点算上游"的判断 ——
     * 两处规则一旦分叉，预览说能退、执行说不能退，比没有预览更糟。
     *
     * <p>预览内容：每个候选节点 + 「退回到它，哪些后续节点会被作废」（willVoidNodes），
     * 供审批人选级次时看清代价。当前节点是 TODO 态天然不在候选集内。
     */
    public Map<String, Object> rejectTargets(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不存在或已处理: " + taskId);
        }
        String pi = task.getProcessInstanceId();
        Map<String, Object> out = withdrawPreview(pi);
        out.put("forTaskId", taskId);
        out.put("forNodeKey", task.getTaskDefinitionKey());
        out.put("forNodeName", task.getName());
        out.remove("notice"); // 撤销语义的公告不适用于驳回页，换下方专用 notice
        // 自由驳回的可用性：总开关 + 灰度白名单 + 节点档位 + 并联在办任务数（>1 时"搬执行"语义不唯一，执行面会拒绝）
        // 放开条件是三把锁：总开关 ∧ 逐链灰度白名单 ∧ 本节点 reject_scope≠NONE。
        // 预览必须与执行面（complete 的同一判定）同源，且把三道校验分开回显——
        // 否则运维只看到"开关未开"，分不清是没开总开关还是本流程没进白名单。
        String gateModelKey = modelKeyOf(task);
        boolean masterOn = isFreeRejectEnabled();
        boolean whitelisted = isModelInList(readConfig("wf.reject.free.models"), gateModelKey);
        boolean enabled = masterOn && whitelisted;
        // 校验以节点配置 reject_scope 为准，全局开关退化为总开关。
        // 预览必须与执行面（complete 里的 freeRejectScopeOf 判定）同源，否则会出现
        // "预览说能退、执行说不能退"——比没有预览更糟。
        String scope = freeRejectScopeOf(task);
        boolean scopeEnabled = scope != null;
        String prevNodeKey = prevDoneNode(pi, task.getTaskDefinitionKey());
        out.put("rejectScope", scopeEnabled ? scope : "NONE");
        out.put("nodeScopeEnabled", scopeEnabled);
        // PREV_ONE 档下这是唯一合法目标，ANY_DONE 档下合法目标另有"声明集"（校验①.6），均显式回显，
        // 供前端把选择器收敛——预览必须与执行面同源，否则"预览说能退、执行说不能退"比没有预览更糟。
        out.put("prevNodeKey", prevNodeKey);
        Set<String> declared = scopeEnabled && "ANY_DONE".equals(scope)
                ? declaredRejectTargets(task, firstHandledNode(pi), prevNodeKey) : null;
        out.put("declaredTargets", declared);
        if (declared != null && out.get("candidates") instanceof List<?> cands) {
            // 选择器与执行面同源：集外目标点了也必被校验①.6 拒，不渲染比渲染了报错好。
            List<Object> narrowed = new ArrayList<>();
            for (Object c : cands) {
                if (c instanceof Map<?, ?> m && m.get("nodeKey") != null
                        && declared.contains(String.valueOf(m.get("nodeKey")))) {
                    narrowed.add(c);
                }
            }
            out.put("candidates", narrowed);
        }
        out.put("resubmitJumpBack", nodeResubmitJumpBack(task));
        out.put("rejectOriginNode", rejectOriginNodeVar(pi));
        int activeCount;
        try {
            activeCount = taskService.createTaskQuery().processInstanceId(pi).active().list().size();
        } catch (Exception ex) {
            log.warn("驳回预览：查在办任务数失败 procInst={}", pi, ex);
            activeCount = -1;
        }
        boolean singleActive = activeCount == 1;
        boolean vote = isVoteNode(task);
        out.put("enabled", enabled && scopeEnabled && singleActive && !vote);
        out.put("switchEnabled", masterOn);
        out.put("modelWhitelisted", whitelisted);
        out.put("activeTaskCount", activeCount);
        out.put("voteNode", vote);
        // 复用 withdrawPreview 产物时其 enabledHint 是「已办撤销」的文案，这里必须按驳回语义覆写，
        // 否则两张预览页会显示同一句提示
        out.put("enabledHint", !enabled
                ? (masterOn
                        ? "「自由驳回」总开关已开，但本流程未进灰度白名单 sys_config.wf.reject.free.models；本页为只读预览"
                        : "「自由驳回」总开关未开（sys_config.wf.reject.free.enabled 未置 1，默认关闭）；本页为只读预览")
                : (scopeEnabled
                        ? "本节点 reject_scope=" + scope + " 且总开关与灰度白名单均已放开；执行面经重启窗口运行期验证后可用"
                        : "本节点 reject_scope=NONE（未开启指定级次驳回）；本页为只读预览"));
        if (!masterOn) {
            out.put("disabledReason", "总开关未开启（sys_config.wf.reject.free.enabled 未置 1，默认关闭）");
        } else if (!whitelisted) {
            out.put("disabledReason", "本流程未进逐链灰度白名单（sys_config.wf.reject.free.models 未包含 "
                    + gateModelKey + "，空清单＝全库不生效）；如需放开，请把该 model_key 追加进白名单");
        } else if (!scopeEnabled) {
            out.put("disabledReason", "本节点未开启指定级次驳回（wf_node_config.reject_scope=NONE，缺省）；"
                    + "如需按节点放开，请把该节点 reject_scope 配为 PREV_ONE（仅退回上一节点）或 ANY_DONE"
                    + "（退回「声明集」内的节点：提单人节点／上一级／审批项 wf_approval_item.target_node 显式声明的目标）");
        } else if (vote) {
            out.put("disabledReason", "票签（VOTE）节点按比例聚合判定，不支持指定级次驳回");
        } else if (!singleActive) {
            out.put("disabledReason", "该单据当前有 " + activeCount + " 个在办节点（并联/会签），不支持指定级次驳回");
        } else {
            out.put("disabledReason", null);
        }
        out.put("notice", "只读预览：本接口不修改任何流程数据。执行面（complete 传 rejectToNodeKey）需同时满足"
                + "「本节点 reject_scope ≠ NONE」「总开关 sys_config.wf.reject.free.enabled=1」"
                + "「本流程在 sys_config.wf.reject.free.models 灰度白名单内」三项条件，且须先在"
                + "重启窗口完成运行期验证 —— 原语 moveActivityIdTo 属「搬执行」，会真实改动在办单据本体。"
                + (scopeEnabled && "PREV_ONE".equals(scope)
                        ? " 本节点为 PREV_ONE 档：只允许退回紧邻上一已完成节点 " + prevNodeKey + "，其余目标一律拒绝。"
                        : (scopeEnabled && "ANY_DONE".equals(scope)
                                ? " 本节点为 ANY_DONE 档，但目标须命中声明集 declaredTargets（提单人节点／上一级／审批项显式声明），集外一律拒绝。"
                                : "")));
        return out;
    }

    /**
     * 自由驳响应行（仅由 {@code complete(..., rejectToNodeKey)} 在开关与节点配置双重放行时调用）。
     *
     * <p>四道强制校验，任何一道不过都<b>抛业务异常而非降级</b>：静默降级回"终止本流程"会让
     * 审批人以为退回成功、实际单据已被作废 —— 这是比失败更糟的错位。
     * ① 目标节点必须属于本实例<b>已完成</b>节点（防跨实例/未来节点乱搬）；
     * ①.5 节点配置档位：{@code reject_scope=PREV_ONE} 时目标必须恰为紧邻上一已完成节点，
     *     {@code ANY_DONE} 时保持原行为（任一已完成节点）；{@code NONE} 直接拒绝；
     * ①.6 「目标集限定」：{@code ANY_DONE} 档下目标还须命中<b>声明集</b>，
     *     见 {@link #declaredRejectTargets}；会签节点<b>不写死禁止</b>作目标；
     * ② 实例在办任务必须恰好 1 条（并联/会签时"搬执行"语义不唯一）；
     * ③ 总开关 {@code wf.reject.free.enabled}=1（调用方已判，此处不再重复判）。
     *
     * <p>收尾与 {@code complete()} 同一套规则：待办镜像置 DONE → REJECT_TO 记录（含意见）→
     * {@code syncInstanceAfterAdvance} 重算新任务指派（node_config 三级取人）并刷新实例视图 →
     * 站内信告知被退回节点的原办理人。记录在搬执行<b>之前</b>写一条，搬失败也有据可查。
     */
    private void rejectToNode(Task task, String targetNodeKey, String comment, Long operator) {
        String pi = task.getProcessInstanceId();
        String fromNodeKey = task.getTaskDefinitionKey();
        // 校验⓪：逐链灰度白名单。执行面自己再判一次，不轻信调用方——
        // 与本方法上方的节点档位判定同一取舍："只信入口"就是下一个假开关。
        if (!isFreeRejectModelAllowed(modelKeyOf(task))) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本流程未放开指定级次驳回（总开关 wf.reject.free.enabled 未置 1，或未进 "
                            + "wf.reject.free.models 灰度白名单），请改用普通驳回");
        }
        // 校验①：目标必须在本实例已完成节点集合内
        Set<String> doneKeys = new HashSet<>();
        for (Map<String, Object> n : wfQueryService.chain(pi)) {
            String st = String.valueOf(n.get("state"));
            if (("DONE".equals(st) || "REJECTED".equals(st)) && n.get("nodeKey") != null) {
                doneKeys.add(String.valueOf(n.get("nodeKey")));
            }
        }
        if (!doneKeys.contains(targetNodeKey)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "驳回目标不属于本实例已完成节点，已拒绝: " + targetNodeKey);
        }
        // 校验①.5：节点配置档位。校验走节点配置而非私有全局开关后，执行面仍须自己再判一次——
        // 不能只信调用方的入参（执行入口可能被其它路径复用）。
        String scope = freeRejectScopeOf(task);
        if (scope == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本节点未开启指定级次驳回（wf_node_config.reject_scope=NONE），请改用普通驳回");
        }
        String prev = prevDoneNode(pi, fromNodeKey);
        if ("PREV_ONE".equals(scope)) {
            if (prev == null || !prev.equals(targetNodeKey)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "本节点 reject_scope=PREV_ONE，只允许退回紧邻上一已完成节点"
                                + (prev == null ? "（当前链上找不到上一已完成节点）" : "：" + prev)
                                + "，已拒绝目标 " + targetNodeKey);
            }
        } else {
            // 校验①.6：ANY_DONE 档不再等于"链上任一已完成节点都可驳"。
            Set<String> declared = declaredRejectTargets(task, firstHandledNode(pi), prev);
            if (!declared.contains(targetNodeKey)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "驳回目标不在本节点声明的可退回集合 " + declared
                                + " 内，已拒绝: " + targetNodeKey
                                + "（需由提单人节点/上一级/审批项 wf_approval_item.target_node 显式声明）");
            }
        }
        // 校验②：在办任务必须恰好 1 条
        List<Task> active = openTasks(pi);
        if (active.size() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该单据当前有 " + active.size() + " 个在办节点（并联/会签），不支持指定级次驳回，请逐条办理");
        }
        // 记录（搬执行前）：REJECT_FROM 试探记录，失败可追溯
        writeHistory(pi, task.getId(), fromNodeKey, task.getName(),
                "REJECT_TO", operator, null, comment + " [驳回至 " + targetNodeKey + "]");
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(pi)
                    .moveActivityIdTo(fromNodeKey, targetNodeKey)
                    .changeState();
        } catch (Exception ex) {
            log.error("自由驳回搬执行失败 procInst={} {} -> {}", pi, fromNodeKey, targetNodeKey, ex);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "驳回到指定节点失败（流程未变动，可重试或改走普通驳回）: " + ex.getMessage());
        }
        // 收尾：镜像 + 最终状态记录 + 指派重算 + 视图刷新（各自 try/catch，绝不因收尾失败掩盖搬执行已成功的事实）
        try {
            // 【刻意不走 mirrorByFacade() 守卫】——不是遗漏，是判断后的保留：
            // 上方 changeActivityState().changeState() 会触发 Flowable ENTITY_DELETED，投影按实体类型
            // 判定任务删除后把该行置 CANCELLED（搬执行把原节点任务物理删掉，引擎侧事实如此），
            // 但业务语义上这次驳回是"该任务被办理了"（本次记录 REJECT_TO/REJECT_FROM 即证据），
            // 因此门面必须在其后把镜像行改写为 DONE。两种模式最终状态一致（投影 CANCELLED → 门面 DONE），
            // 且本行是"先 CANCELLED 再 DONE"的确定性覆盖、非并发争抢，故保留双写。
            wfQueryService.markTaskDone(task.getId(), operator);
        } catch (Exception ex) {
            log.error("自由驳回：待办镜像置 DONE 失败 taskId={}", task.getId(), ex);
        }
        writeHistory(pi, task.getId(), fromNodeKey, task.getName(),
                "REJECT_TO_DONE", operator, null, comment);
        try {
            // notifyTodo=false：下面已对回退节点办理人定向通知，避免双发
            syncInstanceAfterAdvance(pi, false);
        } catch (Exception ex) {
            log.warn("自由驳回：实例视图同步失败 procInst={}", pi, ex);
        }
        // 通知被退回节点的办理人：取回退后新任务的 assignee/候选人
        try {
            for (Task t : openTasks(pi)) {
                Long notifyTo = null;
                if (t.getAssignee() != null && t.getAssignee().matches("\\d+")) {
                    notifyTo = Long.parseLong(t.getAssignee());
                }
                if (notifyTo != null) {
                    notifyService.notifyUser(notifyTo, "APPROVAL", "单据被退回至你办理",
                            "上级审批驳回了单据并指定退回本节点，请查看意见后处理。意见：" + (comment == null ? "" : comment),
                            null, null);
                }
            }
        } catch (Exception ex) {
            log.warn("自由驳回：退回通知失败 procInst={}", pi, ex);
        }
        log.info("自由驳回完成 procInst={} {} -> {} operator={}", pi, fromNodeKey, targetNodeKey, operator);
    }

    /**
     * 提单人节点＝本实例链上<b>最早一个已办理</b>节点（{@code chain()} 按 {@code wf_node_config.sort} 升序出链）。
     * 驳回至该节点即"打回给提单人"。找不到返回 null。
     */
    private String firstHandledNode(String procInstId) {
        for (Map<String, Object> n : wfQueryService.chain(procInstId)) {
            String st = String.valueOf(n.get("state"));
            if (("DONE".equals(st) || "REJECTED".equals(st)) && n.get("nodeKey") != null) {
                return String.valueOf(n.get("nodeKey"));
            }
        }
        return null;
    }

    /**
     * 驳回目标「声明集」＝ <b>提单人节点 ∪ 紧邻上一已完成节点
     * ∪ 本节点启用审批项显式声明的 {@code wf_approval_item.target_node}</b>。
     *
     * <p><b>为什么不是"流程上所有走过的节点"</b>：{@code ANY_DONE} 的字面含义太宽。驳回是把在办执行
     * {@code moveActivityIdTo} 搬到目标节点，搬到金额审批已完成的中间节点，等于凭空造出一个
     * "该节点从未申请过的复审"，责任链从此对不上记录。驳回目标必须是<b>声明出来的集合</b>。
     *
     * <p><b>会签节点不写死禁止</b>：它能否作目标，取决于是否出现在本集合内（默认档只有上一级与提单人
     * 节点，放开须配置）。"会签操作设置里没有打回"说的是节点内操作设置，不是"会签不可被驳回为目标"。
     *
     * <p><b>结构差异</b>：我方无连线/边实体（{@code workflow/entity} 17 个类无 edge/transition），
     * "决策项声明的目标集"只能由 {@code wf_approval_item.target_node} 承载；
     * BPMN 的 {@code SequenceFlow} 是<b>正向</b>流转边，不能当驳回目标声明用。
     */
    private Set<String> declaredRejectTargets(Task task, String submitterNodeKey, String prevNodeKey) {
        Set<String> declared = new LinkedHashSet<>();
        if (prevNodeKey != null) {
            declared.add(prevNodeKey);
        }
        if (submitterNodeKey != null) {
            declared.add(submitterNodeKey);
        }
        for (WfApprovalItem item : approvalItemService.listEnabled(
                modelKeyOf(task), task.getTaskDefinitionKey())) {
            String tn = item.getTargetNode();
            if (tn != null && !tn.isBlank()) {
                declared.add(tn.trim());
            }
        }
        return declared;
    }

    /** 当前任务所在节点的配置（按 modelKey:nodeKey 精确匹配，与超时/空策略同一条规则）；取不到返回 null */
    private WfNodeConfig nodeConfigOf(Task task) {
        if (task == null || task.getProcessDefinitionId() == null) {
            return null;
        }
        String modelKey = task.getProcessDefinitionId().split(":")[0];
        return wfNodeConfigResolver.resolve(modelKey, task.getTaskDefinitionKey());
    }

    /**
     * 驳回校验档位：{@code wf_node_config.reject_scope} 的唯一读取点。
     *
     * <p><b>返回值语义</b>：{@code ANY_DONE} / {@code PREV_ONE} 表示本节点已开启指定级次驳回；
     * {@code null} 表示未开启（配成 {@code NONE}、未配、或值无法识别）——未开启时走「终止本流程」。
     *
     * <p><b>为什么全局开关还要留着</b>：本列负责"按节点分级放开"，全局开关
     * {@code wf.reject.free.enabled} 负责"一键全关"。搬执行（moveActivityIdTo）属破坏性能力，
     * 回滚路径必须留一个总开关，两者是<b>与</b>关系（先满足节点档位、再满足总开关）。
     */
    private String freeRejectScopeOf(Task task) {
        try {
            WfNodeConfig cfg = nodeConfigOf(task);
            if (cfg == null || cfg.getRejectScope() == null) {
                return null;
            }
            String s = cfg.getRejectScope().trim().toUpperCase();
            return ("PREV_ONE".equals(s) || "ANY_DONE".equals(s)) ? s : null;
        } catch (Exception ex) {
            log.warn("驳回档位读取失败（按未开启处理）task={}: {}", task == null ? null : task.getId(), ex.getMessage());
            return null;
        }
    }

    /** 本节点是否配了「重新提交跳回本节点」（{@code resubmit_jump_back=1}） */
    private boolean nodeResubmitJumpBack(Task task) {
        WfNodeConfig cfg = nodeConfigOf(task);
        return cfg != null && Integer.valueOf(1).equals(cfg.getResubmitJumpBack());
    }

    /**
     * 「重新提交跳回本节点」关联点：{@code resubmit_jump_back=1} 的节点驳回时，把本节点 key 写入
     * <b>旧</b>流程实例变量 {@code rejectOriginNode}。
     *
     * <p><b>写与读</b>：本方法只负责"写"。跨实例的"读"分两步——
     * {@link #injectResubmitJumpBackAnchor} 把旧实例上的关联点接力进新实例的启动变量；
     * {@link #consumeRejectAnchor} 在新实例启动后<b>真正搬执行</b>到该节点。
     * 少了后一步，关联点"只写不读"，对引擎完全惰性＝又一枚配了不生效的假配置。
     * 变量本身照常写入历史变量库（ACT_HI_VARINST）并在驳回预览里回显，两条通道都留得下证据。
     *
     * <p>零影响：未配该列的节点只多一次配置读取，不写任何变量。
     */
    private void recordRejectOriginNode(Task task) {
        try {
            if (!nodeResubmitJumpBack(task)) {
                return;
            }
            runtimeService.setVariable(task.getProcessInstanceId(), "rejectOriginNode", task.getTaskDefinitionKey());
            log.info("驳回锚点已记录（重新提交时跳回本节点）: proc={} node={}",
                    task.getProcessInstanceId(), task.getTaskDefinitionKey());
        } catch (Exception ex) {
            // 记关联点失败不得阻断驳回主链路（跳回是增强语义，不是驳回的前置条件）
            log.warn("驳回锚点记录失败 task={}: {}", task == null ? null : task.getId(), ex.getMessage());
        }
    }

    /** 实例变量 {@code rejectOriginNode}（无则 null），只读预览用 */
    private String rejectOriginNodeVar(String procInstId) {
        try {
            Object v = runtimeService.getVariable(procInstId, "rejectOriginNode");
            return v == null ? null : String.valueOf(v);
        } catch (Exception ex) {
            log.debug("驳回锚点变量读取失败 proc={}: {}", procInstId, ex.getMessage());
            return null;
        }
    }

    /**
     * 「重新提交跳回本节点」跨实例关联点接力：把同一业务单据上一枚已驳回历史实例上
     * 记录的 {@code rejectOriginNode} 取回，并入新实例的启动变量。
     *
     * <p><b>要解决的问题</b>：驳回时 {@link #recordRejectOriginNode} 把本节点 key 写进<b>旧</b>实例变量；
     * 而重提交走 REJECTED → DRAFT → 重新 submit，起的是<b>新</b>实例，新实例不继承旧实例变量，
     * 于是关联点只写不读、从未被消费。本方法补上"读"这一半，使关联点能跨实例到达新实例。
     *
     * <p><b>做到哪一步</b>：本方法把关联点带到新实例的启动变量 {@code rejectOriginNode}，并回传
     * {@link RejectAnchor}（关联点＋来源旧实例＋旧实例金额）供执行面使用。真正的"跳回"由
     * {@link #consumeRejectAnchor} 在实例启动、首任务镜像与通知都落账<b>之后</b>执行
     * （走 {@code moveActivityIdTo} 搬执行＋回收首节点副作用）。
     * 没在定义侧加排他网关：现网 87 枚模型全部锁版本，改定义需重发布窗口且对历史实例不生效，
     * 而本能力要在灰度链上立刻可验。
     *
     * <p><b>零影响边界</b>：① 本流程（model_key）无 {@code resubmit_jump_back=1} 的节点 ⇒ 直接返回，
     * 既不查 wf_instance、也不注入任何键；② 无历史驳回实例、或旧实例未留存该变量 ⇒ 不注入任何键；
     * ③ 调用方已在 extraVars 显式传入同名变量时以调用方为准。全过程 try/catch，任何异常只降级为
     * "不注入"，绝不让"提交/重新提交"因本增强而失败。
     */
    private RejectAnchor injectResubmitJumpBackAnchor(String processKey, String bizType, Long bizId, Map<String, Object> vars) {
        try {
            if (processKey == null || bizType == null || bizId == null) {
                return null;
            }
            // ① 校验：本流程确有"重新提交跳回本节点"的节点才继续——未配则连查询都不做，历史零影响。
            Long jumpBackNodes = wfNodeConfigMapper.selectCount(
                    new LambdaQueryWrapper<WfNodeConfig>()
                            .eq(WfNodeConfig::getModelKey, processKey)
                            .eq(WfNodeConfig::getResubmitJumpBack, 1));
            if (jumpBackNodes == null || jumpBackNodes == 0) {
                return null;
            }
            // ② 取同一业务单据的历史实例（instancesByBiz 已按 id 倒序，最新的在前），找最近的已驳回实例。
            List<WfInstance> history = wfQueryService.instancesByBiz(bizType, bizId);
            if (history == null || history.isEmpty()) {
                return null;
            }
            for (WfInstance inst : history) {
                if (inst == null || !"REJECTED".equals(inst.getStatus()) || inst.getProcInstId() == null) {
                    continue;
                }
                // ③ 旧实例已结束，运行时变量已随实例销毁，须走历史变量库；取不到则继续向前找更早的驳回实例。
                String node = historicVariableOf(inst.getProcInstId(), "rejectOriginNode");
                if (node == null || node.isBlank()) {
                    continue;
                }
                if (vars.containsKey("rejectOriginNode")) {
                    // 调用方显式指定优先（extraVars 已并入 vars），不覆盖其意图。
                    // 但此时关联点来源实例不明，执行面无法核对"上一轮实际走过的路线"，故不返回关联点＝不跳回。
                    log.info("重新提交跳回锚点由调用方显式指定，跳过跨实例接力: bizType={} bizId={}", bizType, bizId);
                    return null;
                }
                vars.put("rejectOriginNode", node);
                log.info("重新提交跳回锚点跨实例接力: bizType={} bizId={} 旧实例={} 锚点节点={}",
                        bizType, bizId, inst.getProcInstId(), node);
                return new RejectAnchor(node, inst.getProcInstId(), inst.getAmount());
            }
        } catch (Exception ex) {
            // 接力失败不得阻断启动主链路（跳回是增强语义，不是提交/重新提交的前置条件）
            log.warn("重新提交跳回锚点接力失败（按不注入处理）bizType={} bizId={}: {}", bizType, bizId, ex.getMessage());
        }
        return null;
    }

    /**
     * 重提跳回关联点：驳回时写在<b>旧</b>实例上的 {@code rejectOriginNode}，
     * 连同"从哪一实例取到"的溯源信息一并交给启动侧。
     *
     * @param nodeKey 要跳回的节点（＝当初驳回它的那个节点）
     * @param sourceProcInstId 关联点来源的旧实例（执行面据此核对上一轮实际路线与已完成节点）
     * @param sourceAmount 旧实例金额（金额一变必须重走全链，见 {@link #consumeRejectAnchor}）
     */
    private record RejectAnchor(String nodeKey, String sourceProcInstId, BigDecimal sourceAmount) {
    }

    /**
     * 历史流程变量读取（实例结束/删除后运行时变量已销毁，须走历史库）。取不到或异常一律返回 null，
     * 由调用方按"无关联点"降级，不向上抛。
     */
    private String historicVariableOf(String procInstId, String name) {
        try {
            HistoricVariableInstance v =
                    historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(procInstId).variableName(name).singleResult();
            return v == null || v.getValue() == null ? null : String.valueOf(v.getValue());
        } catch (Exception ex) {
            log.debug("历史流程变量读取失败 proc={} name={}: {}", procInstId, name, ex.getMessage());
            return null;
        }
    }

    /**
     * 「重新提交跳回被驳回节点」执行面：新实例启动后，把执行从当前（重走一遍的）首节点直接搬到
     * 关联点节点，使编辑重做不再重复占用关联点<b>之前</b>的各岗。
     *
     * <p><b>为什么不走 {@link #complete} 自动通过</b>：跳过的节点办理人不是提单人，用提单人身份 complete
     * 会被 {@code NodeOrgResolver.canOperate} 的越权守卫硬拒（fail-closed 是铁律）。
     * 为出功能去放宽那条守卫＝把越权通道打开，绝不允许。故这里与「自由驳回」同源，用引擎原语
     * {@code moveActivityIdTo} 搬执行，跳过动作只留一条 {@code RESUBMIT_SKIP} 痕，
     * <b>不给被跳过的节点伪造"已通过"记录</b>。
     *
     * <p>六道校验，任一不过即"按全链重走"（降级不报错）：
     * ① 总开关 {@code wf.resubmit.jumpback.enabled}=1 且 {@code wf.resubmit.jumpback.models} 含本流程
     *    （{@link #isConfigGatedOn}，与 PICK/加签校验同一灰度规则，空清单＝全库不生效）；
     * ② 关联点节点自身 {@code wf_node_config.resubmit_jump_back=1}；
     * ③ 金额与驳回时逐字一致——金额一改，超限额的高等级审批（如"超 500 万须总裁审批"）必须重走，
     *    跳回等于绕过它，属不可接受的风险；
     * ④ 上一轮驳回实例的<b>实际路线</b>可核对：当前节点在旧路线上、关联点也在，且二者之间的每个节点
     *    上一轮都已办结（有结束时间）；否则说明本轮路线与上轮不同或存在未审节点，不跳；
     * ⑤ 新定义图上关联点自当前节点可达（网关/事件/条件穿越，BFS）；
     * ⑥ 关联点不是多签节点（ALL/SEQ/VOTE）——搬进多实例活动需集合变量随执行树重建，风险另计，暂不做。
     *
     * <p>副作用回收与 {@link #rejectToNode} 同一套规则：搬之前写一条试探痕（失败可追溯）→
     * 搬执行 → 被搬走的节点镜像置 DONE（引擎已物理删除该任务，镜像留 PENDING＝永远办不掉的幽灵待办）
     * → {@code syncInstanceAfterAdvance} 重算关联点节点三级取人并刷新实例视图 → 站内信告知关联点办理人。
     *
     * <p>全过程 try/catch：跳回是增强语义，任何异常只降级为"按全链重走"，绝不让重新提交失败。
     */
    private void consumeRejectAnchor(String processKey, String procInstId,
                                     BigDecimal newAmount, RejectAnchor anchor) {
        try {
            if (anchor == null || anchor.nodeKey() == null || anchor.nodeKey().isBlank()
                    || anchor.sourceProcInstId() == null) {
                return;
            }
            String anchorKey = anchor.nodeKey().trim();
            if (!isConfigGatedOn("wf.resubmit.jumpback.enabled", "wf.resubmit.jumpback.models", processKey)) {
                log.info("重提跳回未放行（总闸或灰度白名单缺省关），本单按全链重走: model={} proc={}", processKey, procInstId);
                return;
            }
            WfNodeConfig anchorCfg = wfNodeConfigResolver.resolve(processKey, anchorKey);
            if (anchorCfg == null || !Integer.valueOf(1).equals(anchorCfg.getResubmitJumpBack())) {
                log.info("重提跳回未放行（锚点节点未配 resubmit_jump_back=1）: model={} node={}", processKey, anchorKey);
                return;
            }
            String mt = anchorCfg.getMultiType() == null ? "" : anchorCfg.getMultiType().trim().toUpperCase();
            if ("ALL".equals(mt) || "SEQ".equals(mt) || "VOTE".equals(mt)) {
                log.info("重提跳回未放行（锚点为多签节点，搬执行进多实例活动不在本能力范围）: model={} node={} multiType={}",
                        processKey, anchorKey, mt);
                return;
            }
            if (anchor.sourceAmount() != null && newAmount != null
                    && anchor.sourceAmount().compareTo(newAmount) != 0) {
                log.info("重提跳回中止（金额已改，高等级审批必须重走）: proc={} 旧金额={} 新金额={} 锚点={}",
                        procInstId, anchor.sourceAmount(), newAmount, anchorKey);
                return;
            }
            List<Task> open = openTasks(procInstId);
            if (open.size() != 1) {
                log.info("重提跳回中止（当前有 {} 条在办任务，搬执行语义不唯一）: proc={}", open.size(), procInstId);
                return;
            }
            Task cur = open.get(0);
            if (anchorKey.equals(cur.getTaskDefinitionKey())) {
                return; // 本就该从这里办，无需搬
            }
            // 校验④：上一轮实际路线核对
            List<String> oldRoute = new ArrayList<>();
            Set<String> oldFinished = new HashSet<>();
            for (HistoricTaskInstance h :
                    historyService.createHistoricTaskInstanceQuery().processInstanceId(anchor.sourceProcInstId()).list()) {
                if (h.getTaskDefinitionKey() == null) {
                    continue;
                }
                oldRoute.add(h.getTaskDefinitionKey());
                if (h.getEndTime() != null) {
                    oldFinished.add(h.getTaskDefinitionKey());
                }
            }
            int from = oldRoute.indexOf(cur.getTaskDefinitionKey());
            int to = oldRoute.lastIndexOf(anchorKey);
            if (from < 0 || to < 0 || to <= from) {
                log.info("重提跳回中止（旧路线不可核对：当前节点或锚点不在上一轮路线上）: proc={} cur={} 锚点={}",
                        procInstId, cur.getTaskDefinitionKey(), anchorKey);
                return;
            }
            List<String> between = new ArrayList<>(oldRoute.subList(from + 1, to));
            between.removeAll(oldFinished);
            if (!between.isEmpty()) {
                log.info("重提跳回中止（锚点之前存在上一轮未办结的节点，跳过即绕过必要审批）: proc={} 未办结={}",
                        procInstId, between);
                return;
            }
            // 校验⑤：新定义图上可达
            if (!userTaskReachable(cur.getProcessDefinitionId(), cur.getTaskDefinitionKey(), anchorKey)) {
                log.info("重提跳回中止（新定义图上锚点自当前节点不可达，路由已变）: proc={} {} -> {}",
                        procInstId, cur.getTaskDefinitionKey(), anchorKey);
                return;
            }
            String skippedNodes = String.join("、", oldRoute.subList(from, to));
            writeHistory(procInstId, cur.getId(), cur.getTaskDefinitionKey(), cur.getName(),
                    "RESUBMIT_SKIP", cur.getAssignee() == null ? null : toUserIdOrNull(cur.getAssignee()), null,
                    "重新提交跳回被驳回节点 " + anchorKey + "（跳过本轮重走的节点：" + skippedNodes
                            + "；上一轮这些节点均已通过，来源实例 " + anchor.sourceProcInstId() + "）");
            try {
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(procInstId)
                        .moveActivityIdTo(cur.getTaskDefinitionKey(), anchorKey)
                        .changeState();
            } catch (Exception ex) {
                log.error("重提跳回搬执行失败（本单按全链重走，未变动成功）proc={} {} -> {}: {}",
                        procInstId, cur.getTaskDefinitionKey(), anchorKey, ex.getMessage(), ex);
                return;
            }
            try {
                wfQueryService.markTaskDone(cur.getId(), toUserIdOrNull(cur.getAssignee()));
            } catch (Exception ex) {
                log.error("重提跳回：被跳过节点的待办镜像收尾失败 taskId={}", cur.getId(), ex);
            }
            try {
                // notifyTodo=false：下面已对被跳回的节点逐个发定向通知，防双发
                syncInstanceAfterAdvance(procInstId, false);
            } catch (Exception ex) {
                log.warn("重提跳回：实例视图同步失败 proc={}", procInstId, ex);
            }
            for (Task t : openTasks(procInstId)) {
                Long notifyTo = toUserIdOrNull(t.getAssignee());
                if (notifyTo == null) {
                    continue; // 候选组/POOL 态：无人可点名，不伪造收件人
                }
                try {
                    notifyService.notifyUser(notifyTo, "APPROVAL", "驳回单已重新提交并跳回本节点",
                            "提单人修改后重新提交，单据已直接回到你此前驳回的那个节点【" + t.getName()
                                    + "】，中间环节不再重复占用，请查看修改后的单据。", null, "/approval");
                } catch (Exception ex) {
                    log.warn("重提跳回通知失败 task={}: {}", t.getId(), ex.getMessage());
                }
            }
            log.info("重提跳回完成: proc={} {} -> {} 跳过={}", procInstId, cur.getTaskDefinitionKey(), anchorKey, skippedNodes);
        } catch (Exception ex) {
            // 跳回是增强语义：任何异常只降级为"按全链重走"，绝不让提交/重新提交因此失败
            log.error("重提跳回异常（按全链重走）proc={} 锚点={}: {}", procInstId,
                    anchor == null ? null : anchor.nodeKey(), ex);
        }
    }

    /**
     * BPMN 图上"从 {@code fromKey} 出发能否走到 {@code targetKey}"（网关/事件/<b>其它人工节点</b>一并穿越）。
     * 与 {@link #userTasksBeyond} 的差别只在后者到人工节点即停，这里要的是可达性而非下一环节名单。
     * 图不可解析、节点不存在一律返回 false（fail-closed：判定不了就不搬）。
     */
    private boolean userTaskReachable(String procDefId, String fromKey, String targetKey) {
        try {
            Process p = mainProcess(procDefId);
            FlowElement fromEl = p == null ? null : p.getFlowElement(fromKey);
            if (!(fromEl instanceof FlowNode) || targetKey == null) {
                return false;
            }
            Set<String> seen = new HashSet<>();
            ArrayDeque<FlowElement> queue = new ArrayDeque<>();
            for (SequenceFlow f : ((FlowNode) fromEl).getOutgoingFlows()) {
                queue.add(p.getFlowElement(f.getTargetRef()));
            }
            for (int guard = 0; !queue.isEmpty() && guard < 400; guard++) {
                FlowElement el = queue.poll();
                if (el == null || !seen.add(el.getId())) {
                    continue;
                }
                if (targetKey.equals(el.getId())) {
                    return true;
                }
                if (el instanceof FlowNode fn) {
                    for (SequenceFlow f : fn.getOutgoingFlows()) {
                        queue.add(p.getFlowElement(f.getTargetRef()));
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("锚点可达性解析失败（按不可达处理）procDef={} {} -> {}: {}",
                    procDefId, fromKey, targetKey, ex.getMessage());
            return false;
        }
    }

    /**
     * 紧邻上一已完成节点（{@code PREV_ONE} 档的唯一合法驳回目标）。
     *
     * <p>链规则与 {@code rejectTargets} 完全同源（{@code wfQueryService.chain}，按节点配置 sort 顺序、
     * 状态取最后一次办理结果），不另造一套"哪些节点算上游"的判断——两处一旦分叉，预览说能退、
     * 执行说不能退。
     *
     * <p>找不到（当前节点在链首、链上无已完成节点、或当前节点不在链上且链上无 DONE）返回 null，
     * 调用方按"拒绝"处理（fail-closed：无法判定就不放行搬执行）。
     */
    private String prevDoneNode(String procInstId, String fromNodeKey) {
        try {
            List<Map<String, Object>> chain = wfQueryService.chain(procInstId);
            int cur = -1;
            for (int i = 0; i < chain.size(); i++) {
                if (fromNodeKey != null && fromNodeKey.equals(String.valueOf(chain.get(i).get("nodeKey")))) {
                    cur = i;
                    break;
                }
            }
            // 当前节点不在链上（配置缺行/键改过）：退而取链上最后一个已完成节点
            int from = cur >= 0 ? cur - 1 : chain.size() - 1;
            for (int i = from; i >= 0; i--) {
                String st = String.valueOf(chain.get(i).get("state"));
                if ("DONE".equals(st) || "REJECTED".equals(st)) {
                    return String.valueOf(chain.get(i).get("nodeKey"));
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("上一已完成节点解析失败 proc={} node={}: {}", procInstId, fromNodeKey, ex.getMessage());
            return null;
        }
    }

    /**
     * 「自由驳回」开关 {@code sys_config.wf.reject.free.enabled}，缺省 0（fail-closed）。
     *
     * <p>与 {@code wf.withdraw.done.enabled} 同一规则：读不到配置即认为关闭 ——
     * 这是改动在办单据本体的破坏性能力，宁可少做不可误开。
     */
    private boolean isFreeRejectEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, "wf.reject.free.enabled")
                            .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return false; // 配置缺失 → 默认关闭（fail-closed）
            }
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.error("读取 wf.reject.free.enabled 异常，fail-closed 默认关闭: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 「自由驳回」逐链灰度白名单 {@code sys_config.wf.reject.free.models}。
     *
     * <p><b>为什么要这一道</b>：只看「总开关=1 <b>且</b> 本节点 reject_scope≠NONE」的话，节点档位
     * 现状是 355/355 一刀切的 {@code ANY_DONE}，总开关一开＝<b>全库所有审批节点同时</b>获得
     * 搬执行能力，没法"灰度到 1 条链"——只能全开或全关。所以补一道 model 白名单，与
     * PICK、加签的灰度同一规则：空清单＝全库不生效（fail-closed）。
     *
     * <p>现网总开关 {@code wf.reject.free.enabled=0}，本道校验接入时不改变任何行为。
     */
    private boolean isFreeRejectModelAllowed(String modelKey) {
        return isConfigGatedOn("wf.reject.free.enabled", "wf.reject.free.models", modelKey);
    }

    /** 白名单串（逗号分隔 model_key）是否含本流程；空清单＝不生效。只判成员，不读总开关——供预览分开回显两道校验 */
    private boolean isModelInList(String rawModels, String modelKey) {
        if (rawModels == null || rawModels.isBlank() || modelKey == null || modelKey.isBlank()) {
            return false;
        }
        for (String m : rawModels.split(",")) {
            if (modelKey.equalsIgnoreCase(m.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 「已办撤销」开关 {@code sys_config.wf.withdraw.done.enabled}，缺省 0。
     *
     * <p>规则取 <b>fail-closed</b>（与 {@code approve.deny_self} 一致）：读不到配置即认为关闭 ——
     * 这是破坏性能力，宁可少做不可误开。
     */
    private boolean isDoneWithdrawEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, "wf.withdraw.done.enabled")
                            .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return false; // 配置缺失 → 默认关闭（fail-closed）
            }
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.error("读取 wf.withdraw.done.enabled 异常，fail-closed 默认关闭: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 撤销（撤回发起）：仅**提单人本人**、且任务尚未开始处理时可撤回；返回 false 表示已被处理。
     *
     * <p>{@code operUserId} 必须由调用方从登录态取（现有调用方 {@code ExpenseBillService#withdraw}
     * 传 {@code UserContext.getRequired().getUserId()}），不得取自请求体——本方法的归属判定全靠它。
     */
    public boolean withdraw(String procInstId, Long operUserId) {
        long cnt = taskService.createTaskQuery().processInstanceId(procInstId).count();
        if (cnt == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程已处理，无法撤回");
        }
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        if (pi == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程实例不存在: " + procInstId);
        }
        Map<String, Object> vars = runtimeService.getVariables(procInstId);
        // 取回租户，避免收尾在无租户上下文执行
        Long tenantId = null;
        WfInstance inst = null;
        try {
            inst = wfQueryService.instanceByProcInstId(procInstId);
            if (inst != null) tenantId = inst.getTenantId();
        } catch (Exception ignore) { /* 视图未写入数据库则交给总线兜底 */ }
        // 撤回归属是硬规则：只看流程有没有被办理、不看操作人是不是提单人的话，任何拿到 procInstId
        // 的调用方都能把别人的在办单据撤掉（撤完单据回到发起态、审批链作废）。唯一调用方
        // ExpenseBillService.withdraw 自己有 requireMine 兜住，所以这里不改变现有行为，
        // 补的是"引擎层裸通道"这一半：校验要落在被复用的方法里，不能指望每个调用方各自记得装。
        // "审核中的单据，反审核人必须是工作流反审核节点对应的处理人"只落到"提单人本人"这一半——
        // 我方节点模型没有"反审核节点"类型，那一半无对应物，不写成已实现；待该节点类型落地后按同一规则放开。
        // 取不到提单人时拒绝（fail-closed），与下方 allow_withdraw 开关的 fail-open 相反：
        // 那条是功能开关，这条是授权判定，和跨公司委托校验、admin 禁入同一风格。
        // 现存数据里 wf_instance.start_user_id 全都不为 NULL，该分支对真实用户不可达。
        Long starter = inst == null ? null : inst.getStartUserId();
        if (starter == null) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "未能确认该单据的提单人，为避免撤掉他人单据已拒绝撤回；请在「我的单据」中操作，或联系流程管理员");
        }
        if (!starter.equals(operUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "只能撤回本人提交的单据，该单据的提单人不是当前操作人");
        }
        // 「允许撤回」开关要真的被消费：wf_model.allow_withdraw 若只有前端开关与列表列、后端零
        // 消费点，就是典型"配了不生效"的死配置。
        // 规则刻意取 fail-open：仅当能查到模型且该列显式为 0 时才拒绝；查不到/为 NULL 一律放行——
        // 历史模型该列可能为空，不能当成"关闭撤回"。
        if (inst != null && inst.getModelKey() != null) {
            try {
                WfModel m = wfModelMapper.selectOne(
                        new LambdaQueryWrapper<WfModel>()
                                .eq(WfModel::getModelKey, inst.getModelKey())
                                .last("LIMIT 1"));
                if (m != null && Boolean.FALSE.equals(m.getAllowWithdraw())) {
                    throw new BizException(ErrorCode.FORBIDDEN, "流程【" + inst.getModelKey()
                            + "】已关闭「允许撤回」，如需撤回请联系流程管理员");
                }
            } catch (BizException be) {
                throw be;
            } catch (Exception ex) {
                log.warn("允许撤回开关读取失败，按放行处理 modelKey={}: {}",
                        inst.getModelKey(), ex.getMessage());
            }
        }
        // 撤回瞬间仍在办的每一节要先取快照：deleteProcessInstance 之后 ACT_RU_TASK 无行、
        // 候选链接一并消失，届时再也答不出"该告知谁"。这是撤回通知唯一的查询环节。
        List<Task> inFlightOnWithdraw = openTasks(procInstId);
        runtimeService.deleteProcessInstance(procInstId, "申请人撤回");
        // 同族办结：撤回走 deleteProcessInstance 只产生 PROCESS_CANCELLED，不触发
        // PROCESS_COMPLETED 监听 ⇒ 实例视图滞留 RUNNING、wf_task 镜像滞留 PENDING
        // 催办/超时会对已死实例空转。与 terminateAsRejected/cancelInstance 同一办结规则。
        try {
            wfQueryService.updateInstanceStatus(procInstId, "CANCELLED", LocalDateTime.now());
            // 撤回同样落节点级作废记录（撤回前在办的每一节都要在链上留下"到了但没办"）
            wfQueryService.closePendingTasksByInstance(procInstId, "申请人撤回，流程终止；本节任务未办理即作废");
        } catch (Exception sex) {
            log.warn("撤回后实例状态/待办镜像同步失败 procInstId={}: {}", procInstId, sex.getMessage());
        }
        // 撤回必须告知在办办理人——催办把人叫来"尽快处理"，撤回却让他手上的单子
        // 静默消失，两条体验叠在一起自相矛盾。通知失败只 warn：撤回本身必须成功，
        // 不能让"发信"这条附加环节把已办结的撤回动作回滚成失败（与待办提醒同一容错规则）。
        try {
            notifyWithdrawnToHandlers(inFlightOnWithdraw, inst, vars, operUserId);
        } catch (Exception nex) {
            log.warn("撤回告知发送失败 procInstId={}: {}", procInstId, nex.getMessage());
        }
        // 撤回也触发业务收尾（释放预算）。事件必须携带 procInstId：业务收尾要读流程变量
        // （如 ReconBizAction 的 prevStatus/prevSupplierConfirmStatus 快照回滚）。用 4 参构造的话
        // 撤回事件 procInstId 为 null，回头就落不进快照回滚分支、被兜底成 DIFF——
        // 驳回与通过两路都带 procInstId，撤回是漏带的第三条路。
        WfProcessEndedEvent ended = new WfProcessEndedEvent(
                (String) vars.get("bizType"), (Long) vars.get("bizId"), false, "申请人撤回", null, procInstId);
        ended.setTenantId(tenantId);
        // 显式标"撤回"，消费方不必再去比 comment 字面量
        ended.setEndType(WfProcessEndedEvent.END_WITHDRAWN);
        eventPublisher.publishEvent(ended);
        writeHistory(procInstId, null, null, null, "WITHDRAW", operUserId, "申请人撤回");
        return true;
    }

    /**
     * 待办列表的 POOL 软锁标注：{@code taskId -> 领取人用户ID}，
     * 只含"作业节点且已被他人领取"的行，前端据此灰显「去处理」并显示"XX 正在办理"。
     * 与 {@code canOperate} 的拒办规则同源（见 {@link NodeOrgResolver#poolLockedByOthers}）。
     */
    public Map<String, Long> poolLockedByOthers(List<Task> tasks, Long viewerId) {
        return nodeOrgResolver.poolLockedByOthers(tasks, viewerId);
    }

    /**
     * 越权拒办提示：POOL 作业节点已被他人领取时，明说锁定人与出口（释放 / 转办 / 改派），
     * 其余情形沿用原有提示。这里只组织文案，判定仍由 {@code canOperate} 说了算。
     */
    private String operateDeniedMessage(Task task) {
        String owner = nodeOrgResolver.poolLockOwner(task);
        if (owner == null) {
            return "非当前节点办理人/候选人，无权办理此任务";
        }
        String name = owner;
        try {
            SysUser u = sysUserMapper.selectById(Long.valueOf(owner));
            if (u != null) {
                name = u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName();
            }
        } catch (Exception ignored) {
            // 取不到姓名就退回显示用户 ID
        }
        return "该任务已由「" + name + "」领取办理（共享作业任务池同时只允许一人办理）。"
                + "如需接办请联系其释放任务，或由其「转办」；流程管理员可用「改派」。";
    }

    /** 我的待办 */
    public List<Task> todo(Long userId) {
        // 两个主查询不再 includeProcessVariables()。带上它会走 "任务 + 关联实体" 查询，
        // 把当页任务的运行态变量连表拉回，万级在办时单次请求就压在秒级。
        // 现在连身份链接也不预取：候选组由 {@link NodeOrgResolver#filterGroupTasks} 一次 IN 批量装载
        // 单据键由 wf_instance 批量取（{@link #bizKeyByProc}），
        // 页面行变量由 {@link #withProcessVariables} 按当页 20 行补载。两条查询都回到最朴素的
        // ACT_RU_TASK 分页查询，不再产生「任务+关联实体」连表。
        // 本人待办（assignee）
        List<Task> assigned = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(userId))
                .orderByTaskCreateTime().desc()
                .list();
        // 角色候选组待办（审批人按角色路由）
        // 角色码装载两查合一：原本 sys_user_role + sys_role 两次往返，是待办慢请求的大头
        List<String> roleCodes = new ArrayList<>();
        try {
            roleCodes.addAll(sysUserMapper.selectRoleCodes(userId));
        } catch (Exception ex) {
            log.error("查询用户角色失败 userId={}", userId, ex);
        }
        if (roleCodes.isEmpty()) {
            return excludeTerminalBills(assigned);
        }
        List<Task> group = taskService.createTaskQuery()
                .taskCandidateGroupIn(roleCodes)
                .orderByTaskCreateTime().desc()
                .list();
        // 多分子公司路由：业务段角色按单据公司过滤，共享/集团角色跨公司可见
        group = nodeOrgResolver.filterGroupTasks(group, userId, roleCodes);
        // POOL 软锁可见性：同池他人已领取的任务不在候选组查询结果里，按镜像规则回捞回来，
        // 让它在待办中"可见但灰显"（锁定者规则），灰显与拒办由 lockedByOther/canOperate 负责。
        try {
            Set<String> poolKeys = wfNodeConfigResolver.poolNodeKeys();
            if (!poolKeys.isEmpty()) {
                Set<String> seen = new HashSet<>();
                assigned.forEach(t -> seen.add(t.getId()));
                group.forEach(t -> seen.add(t.getId()));
                for (String lockedId : wfQueryService.poolTasksClaimedByOthers(userId, poolKeys)) {
                    if (seen.contains(lockedId)) {
                        continue;
                    }
                    Task locked = taskService.createTaskQuery().taskId(lockedId).includeProcessVariables().singleResult();
                    if (locked != null && !String.valueOf(userId).equals(locked.getAssignee())) {
                        group.add(locked);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("POOL 锁定任务回捞失败 userId={}: {}", userId, ex.getMessage());
        }
        // AUTO 档同岗回捞。AUTO 节点激活即写死 assignee，Flowable 的 taskCandidateGroupIn
        // 只回未指派任务 ⇒ 同岗位其他人在待办里看不到这条，但 approve 允许他们办（只看登录人权限与两道校验）。
        // 结果是「能办却看不见」：工作台空白、单子静默停摆。此处补回捞，可见即可（能否办理仍由 approve 判定）。
        try {
            Map<String, String> autoRoleMap = wfNodeConfigResolver.autoNodeRoleMap();
            if (!autoRoleMap.isEmpty()) {
                Set<String> seen = new HashSet<>();
                assigned.forEach(t -> seen.add(t.getId()));
                group.forEach(t -> seen.add(t.getId()));
                for (String tid : wfQueryService.autoTasksOfSameRole(userId, autoRoleMap, roleCodes)) {
                    if (seen.contains(tid)) {
                        continue;
                    }
                    Task extra = taskService.createTaskQuery().taskId(tid).includeProcessVariables().singleResult();
                    if (extra != null && !String.valueOf(userId).equals(extra.getAssignee())) {
                        group.add(extra);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("AUTO 同岗任务回捞失败 userId={}: {}", userId, ex.getMessage());
        }
        // 委托代理：当前用户是受托人（to_user）时，委托人（from_user）名下的待办也并入
        // （含 assignee 直接指定 + 委托人角色候选组任务，两类都转由受托人处理）
        List<Task> delegated = new ArrayList<>();
        try {
            LocalDateTime now = LocalDateTime.now();
            List<WfDelegate> ds = wfDelegateMapper.selectList(
                    new LambdaQueryWrapper<WfDelegate>()
                            .eq(WfDelegate::getToUserId, userId)
                            .eq(WfDelegate::getStatus, "NORMAL")
                            .le(WfDelegate::getBeginTime, now)
                            .ge(WfDelegate::getEndTime, now));
            Set<Long> fromIds = new HashSet<>();
            for (WfDelegate d : ds) {
                if (d.getFromUserId() != null) fromIds.add(d.getFromUserId());
            }
            for (Long fromId : fromIds) {
                // a) 委托人直接指定（assignee）的任务
                delegated.addAll(taskService.createTaskQuery()
                        .taskAssignee(String.valueOf(fromId)).includeProcessVariables().orderByTaskCreateTime().desc().list());
                // b) 委托人角色候选组的任务（角色码 → candidateGroup）
                List<SysUserRole> fromUrs = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, fromId));
                List<Long> fromRoleIds = fromUrs.stream().map(SysUserRole::getRoleId).toList();
                if (!fromRoleIds.isEmpty()) {
                    List<String> fromRoles = new ArrayList<>();
                    sysRoleMapper.selectBatchIds(fromRoleIds).forEach(r -> {
                        if (r.getRoleCode() != null) fromRoles.add(r.getRoleCode());
                    });
                    if (!fromRoles.isEmpty()) {
                        delegated.addAll(taskService.createTaskQuery()
                                .taskCandidateGroupIn(fromRoles).includeProcessVariables().orderByTaskCreateTime().desc().list());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("委托待办合并失败 userId={}", userId, ex);
        }
        // wf_model.enable_delegate=0 的流程不支持委托代办——委托人的待办不并入受托人，
        // 与 NodeOrgResolver.canOperate 第 3 步同一规则（两处都读 delegateDisabledModels，不会一边显一边拦）。
        // 现网 87 个模型该列全为 1 ⇒ 清单为空 ⇒ 直接跳过，连实例都不查。
        Set<String> delegateOff = nodeOrgResolver.delegateDisabledModels();
        if (!delegateOff.isEmpty() && !delegated.isEmpty()) {
            Map<String, String> modelByProc = new HashMap<>();
            delegated.removeIf(t -> {
                String mk = modelByProc.computeIfAbsent(t.getProcessInstanceId(), this::modelKeyOfProc);
                return mk != null && delegateOff.contains(mk);
            });
        }
        // 合并去重（assignee 优先）
        Set<String> seen = new HashSet<>();
        List<Task> merged = new ArrayList<>();
        for (Task t : delegated) { seen.add(t.getId()); merged.add(t); }
        for (Task t : group) { if (!seen.contains(t.getId())) { seen.add(t.getId()); merged.add(t); } }
        for (Task t : assigned) { if (!seen.contains(t.getId())) merged.add(t); }
        merged.sort((a, b) -> {
            Date da = a.getCreateTime(), db = b.getCreateTime();
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });
        return excludeTerminalBills(merged);
    }

    /**
     * 兜底：剔除业务单据已最终状态（VOID/CLOSED/ARCHIVED）或已逻辑删除的待办。
     *
     * <p>待办查询只按 Flowable 任务状态出数，不回查业务单据——作废等终止入口漏走、或存在历史脏数据
     * 使流程实例滞留时，作废单会挂在角标 / 审批工作台 / 工作台「我的待办」三处。此处按单据最终状态就地剔除，
     * 与 {@code WfQueryService.myTodo}（分页审批台）共用 {@link BillTerminalChecker} 同一规则。
     *
     * <p><b>不能逐行 {@code isBillTerminal}</b>：那样每次待办请求都发 N 条
     * {@code SELECT status FROM exp_bill WHERE id=?}，在办一多就成了该请求库侧耗时的首位。
     * 这里按 bizType 分组一次 IN 批量判定，规则与逐行判定同源（{@link BillTerminalChecker#terminalBillIds}）。
     */
    private List<Task> excludeTerminalBills(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        // 单据键优先取 wf_instance 批量映射（一次 IN 查询），
        // 不再依赖任务上已加载的流程变量；映射缺行时回落到变量（若有），两者皆无则放行（fail-open）。
        Set<String> procs = new LinkedHashSet<>();
        for (Task t : tasks) {
            if (t != null && t.getProcessInstanceId() != null) {
                procs.add(t.getProcessInstanceId());
            }
        }
        Map<String, BizKey> bizByProc = bizKeyByProc(procs);
        Map<String, Set<Long>> idsByType = new HashMap<>();
        for (Task t : tasks) {
            if (t == null) {
                continue;
            }
            BizKey key = bizKeyOf(t, bizByProc);
            if (key == null) {
                continue;
            }
            idsByType.computeIfAbsent(key.type(), k -> new HashSet<>()).add(key.id());
        }
        Map<String, Set<Long>> terminalByType = new HashMap<>();
        for (Map.Entry<String, Set<Long>> e : idsByType.entrySet()) {
            terminalByType.put(e.getKey(), billTerminalChecker.terminalBillIds(e.getKey(), e.getValue()));
        }
        List<Task> out = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            if (t == null) {
                continue;
            }
            BizKey key = bizKeyOf(t, bizByProc);
            if (key != null
                    && terminalByType.getOrDefault(key.type(), Set.of()).contains(key.id())) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    /** 任务对应单据键（bizType + bizId）。 */
    private record BizKey(String type, Long id) { }

    private BizKey bizKeyOf(Task t, Map<String, BizKey> bizByProc) {
        if (t == null) {
            return null;
        }
        BizKey byProc = bizByProc.get(t.getProcessInstanceId());
        if (byProc != null) {
            return byProc;
        }
        // 兜底：wf_instance 里查不到（历史脏数据 / 实例已归档）时，若任务恰好带着变量就用变量
        Map<String, Object> pv = t.getProcessVariables();
        String bizType = pv == null ? null : asString(pv.get("bizType"));
        Long bizId = (pv != null && pv.get("bizId") instanceof Number n) ? n.longValue() : null;
        return (bizType == null || bizId == null) ? null : new BizKey(bizType, bizId);
    }

    /**
     * 按流程实例批量取单据键。一次 IN 查询代替"逐任务反序列化流程变量"。
     *
     * <p>等价性：全量比过 ACT_RU_TASK 与 {@code wf_instance}——每个在办任务对应的实例行都存在，
     * {@code biz_type/biz_id} 与运行态变量 {@code bizType/bizId} 逐条一致。查不到的行不抛错、
     * 不剔除（fail-open，与"无 bizType/bizId 就不参与终态剔除"同义）。
     */
    private Map<String, BizKey> bizKeyByProc(Collection<String> procs) {
        Map<String, BizKey> out = new HashMap<>();
        if (procs == null || procs.isEmpty()) {
            return out;
        }
        List<String> ids = new ArrayList<>(procs);
        for (int i = 0; i < ids.size(); i += 500) {
            List<String> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
            try {
                List<WfInstance> rows = wfInstanceMapper.selectList(
                        new LambdaQueryWrapper<WfInstance>()
                                .select(WfInstance::getProcInstId,
                                        WfInstance::getBizType,
                                        WfInstance::getBizId)
                                .in(WfInstance::getProcInstId, chunk));
                for (WfInstance inst : rows) {
                    if (inst.getProcInstId() != null && inst.getBizType() != null && inst.getBizId() != null) {
                        out.put(inst.getProcInstId(), new BizKey(inst.getBizType(), inst.getBizId()));
                    }
                }
            } catch (Exception e) {
                log.warn("wf_instance 批量取单据键失败（本批按无单据键放行）: {}", e.getMessage());
            }
        }
        return out;
    }

    /**
     * 给【当前页】任务补回运行态变量。
     *
     * <p>{@link #todo} 不再为全量在办加载变量，页面行仍需要 {@code title} 等展示字段；
     * 这里按 taskId 批量查一次（待办页最多 200 行），语义与原 {@code includeProcessVariables}
     * 逐字段同源。查询失败时原样返回（页面回退到 wf_instance / 历史变量兜底）。
     */
    public List<Task> withProcessVariables(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        List<String> ids = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            if (t != null && t.getId() != null) {
                ids.add(t.getId());
            }
        }
        if (ids.isEmpty()) {
            return tasks;
        }
        Map<String, Task> loaded = new HashMap<>();
        try {
            // 身份链接一并取回：下游 poolLockedByOthers / candidateNames 需要候选组，否则会退化成逐行查库
            for (Task t : taskService.createTaskQuery().taskIds(ids)
                    .includeProcessVariables().includeIdentityLinks().list()) {
                loaded.put(t.getId(), t);
            }
        } catch (Exception e) {
            log.warn("待办当页变量补载失败（按无变量返回）: {}", e.getMessage());
            return tasks;
        }
        List<Task> out = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            Task hit = t == null ? null : loaded.get(t.getId());
            out.add(hit != null ? hit : t);
        }
        return out;
    }

    // 审计（wf_task_history 等业务视图双写，只增不改）

    /** 流程操作审计：落 wf_task_history（审计表，不提供删除） */
    private void writeHistory(String procInstId, String taskId, String nodeKey, String nodeName,
                              String action, Long operUserId, String comment) {
        writeHistory(procInstId, taskId, nodeKey, nodeName, action, operUserId, null, comment);
    }

    /**
     * 「审批项」记录前缀：把自定义审批项名称（与关联表单操作）并入审批意见，
     * 使时间线、批退原因统计与质量报表都能直接看到"这一票是什么结果"，而不必再去查配置表。
     *
     * <p><b>为什么不直接写进 {@code wf_task_history.action}</b>：该列 {@code varchar(20) NOT NULL}，
     * 且 {@code WfQueryService} 用 {@code "REJECT".equals(action)} 反推实例状态——写文案会把
     * "已驳回"误判为"已办结"，也会因超长整条 INSERT 失败。故 action 保持枚举，审批项落在意见里。
     *
     * <p><b>零影响</b>：{@code itemName} 为空（未传 approvalItemCode）时原样返回，意见逐字不变。
     */
    private static String commentWithApprovalItem(String comment, String itemName, String linkedOper) {
        if (itemName == null || itemName.isBlank()) {
            return comment;
        }
        String prefix = "〔审批项：" + itemName.trim()
                + (linkedOper == null || linkedOper.isBlank() ? "" : " / 表单操作：" + linkedOper.trim()) + "〕";
        return comment == null || comment.isBlank() ? prefix : prefix + comment;
    }

    /**
     * 供定时作业补记录：催办这类"系统替人敲的门"若只自增 urged_count，
     * 时间线上完全看不出这一环节被催过几次、什么时候催的。
     */
    public void recordJobAction(String procInstId, String taskId, String nodeKey, String nodeName,
                                String action, Long targetUserId, String comment) {
        writeHistory(procInstId, taskId, nodeKey, nodeName, action, null, targetUserId, comment);
    }

    /**
     * 带操作人的系统侧动作记录：发信走作业通道，但出声的是人（发起人催办）——
     * 记录必须答得出"谁催的"，只写 7 参版本会留下 oper_user_id 为空、姓名却来自登录态的半截行。
     */
    public void recordJobAction(String procInstId, String taskId, String nodeKey, String nodeName,
                                String action, Long operUserId, Long targetUserId, String comment) {
        writeHistory(procInstId, taskId, nodeKey, nodeName, action, operUserId, targetUserId, comment);
    }

    /**
     * 8 参版本：多一个 {@code targetUserId}，供"涉及第三人"的动作记录（转办=被转办人，
     * ASSIGN=被点名的下一环节办理人）。可回答"这单是谁派给谁的"。
     */
    private void writeHistory(String procInstId, String taskId, String nodeKey, String nodeName,
                              String action, Long operUserId, Long targetUserId, String comment) {
        try {
            // 取舍说明：wf_task_history 是审计记录，即使投影开关打开也<b>仍然写</b>
            // （"节点历史"不能因架构切换而丢失，过渡期必须双写并比对）。它与 wf_instance/wf_task
            // 的处理刻意不同——后两者由监听器接管、门面停写；本表双写，只附加来源标记，便于对账器
            // 与人工排查区分"门面双写行"与"监听器投影行"。
            String effectiveComment = mirrorByFacade() ? comment : markDualWrite(comment);
            WfTaskHistory h = new WfTaskHistory();
            h.setTenantId(TenantContext.getTenantId());
            h.setProcInstId(procInstId);
            h.setTaskId(taskId);
            h.setNodeKey(nodeKey);
            h.setNodeName(nodeName);
            h.setAction(action);
            h.setOperUserId(operUserId);
            h.setTargetUserId(targetUserId);
            h.setComment(effectiveComment);
            h.setOperTime(LocalDateTime.now());
            // biz_type/biz_id 为 NOT NULL：优先从 wf_instance 视图取（流程变量被引擎清理后取不到）
            WfInstance inst = null;
            try {
                inst = wfQueryService.instanceByProcInstId(procInstId);
            } catch (Exception ignore) {
                // 视图未写入数据库时忽略
            }
            if (inst != null) {
                h.setBizType(inst.getBizType());
                h.setBizId(inst.getBizId());
            } else {
                h.setBizType("UNKNOWN");
                h.setBizId(0L);
            }
            // 记录姓名只认操作人本人。无条件取登录态的话，系统代做的动作（空策略 SKIP/ESCALATE、
            // 超时升级、作废、最终状态收尾）会在记录里挂上"当时恰好点按钮的那个人"：oper_user_id 为空
            // 而姓名却有人，事后既分不清系统放行与人工放行，也会把无关的办理人写成升级人。
            // operUserId 为空即保持为空，与作废记录"id/姓名一律留空"同一规则。
            h.setOperUserName(operUserName(operUserId));
            // duration 列若一直为空，时间线就算不出「本环节压了多久」，超时/瓶颈分析只能靠人肉
            // 比时间戳。这里按 wf_task 创建时刻补齐。
            if (taskId != null) {
                h.setDuration(taskElapsedMinutes(taskId));
            }
            wfQueryService.recordHistory(h);
        } catch (Exception ex) {
            // wf_task_history 是审批历史的唯一业务视图。按"审计失败不影响主流程"放过去，它就成了
            // 静默漂移源——行数比 act_hi_taskinst 少的那部分，正是被吞掉的这些。
            // 所以记缺陷并冒泡：调用方（PROCESS_COMPLETED 监听器 / complete / start）必须看见。
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED, "writeHistory",
                    null, "procInstId=" + procInstId + " action=" + action + " taskId=" + taskId, ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "审批历史保存失败: " + ex.getMessage());
        }
    }

    /**
     * 记录操作人姓名：只按 {@code operUserId} 取值——与登录态同源才复用其姓名，
     * 否则按 id 反查；{@code operUserId} 为空（系统代做的动作）返回 null，绝不借用登录态。
     */
    private String operUserName(Long operUserId) {
        if (operUserId == null) {
            return null;
        }
        LoginUser u = UserContext.get();
        if (u != null && operUserId.equals(u.getUserId())) {
            // 记录列不能只取 username（登录名＝工号）——审批链上显示一串工号读不出是谁；
            // 按真名优先、登录名兜底。
            return u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername();
        }
        try {
            SysUser su = sysUserMapper.selectById(operUserId);
            if (su == null) {
                return null;
            }
            return su.getNickName() != null && !su.getNickName().isBlank() ? su.getNickName() : su.getUserName();
        } catch (Exception ex) {
            log.warn("留痕操作人姓名反查失败 userId={}: {}", operUserId, ex.getMessage());
            return null;
        }
    }

    /** 本环节办理耗时（分钟）＝待办镜像行创建时刻 → 现在；任务行已被引擎清理时返回 null。 */
    private Long taskElapsedMinutes(String taskId) {
        try {
            // wf_task 的唯一键是 (tenant_id, task_id)：不带租户的裸 SQL 用不上它，EXPLAIN 是
            // ALL / 160 万行（418 ms/次，每次办结都走一次）。补租户后走常量查找；取不到租户
            // （作业线程）时保持原写法，宁可慢也不漏行。
            Long tenantId = TenantContext.getTenantId();
            List<LocalDateTime> ts = tenantId == null
                    ? new JdbcTemplate(dataSource).queryForList(
                            "SELECT create_time FROM wf_task WHERE task_id = ?", LocalDateTime.class, taskId)
                    : new JdbcTemplate(dataSource).queryForList(
                            "SELECT create_time FROM wf_task WHERE tenant_id = ? AND task_id = ?",
                            LocalDateTime.class, tenantId, taskId);
            if (ts.isEmpty() || ts.get(0) == null) {
                return null;
            }
            return Duration.between(ts.get(0), LocalDateTime.now()).toMinutes();
        } catch (Exception ex) {
            log.warn("办理耗时取数失败 taskId={}: {}", taskId, ex.getMessage());
            return null;
        }
    }

    /**
     * 发起时解析业务单据号。
     * 表/列取自 {@link BillRouteRegistry}（发起、待办回填、COMPANY 取人三处共用这一份真值）。
     * 解析失败不阻断发起（记 warn 返回 null），与"单号保持空属预期"的类型（TR_BANK_MASTER/TR_SEAL_CHANGE）兼容。
     */
    private String resolveBizNo(String bizType, Long bizId) {
        if (bizType == null || bizId == null) return null;
        try {
            BillRouteRegistry.Route route = BillRouteRegistry.of(bizType);
            if (route == null) return null;
            String table = route.bizNoTableOrDefault();
            String col = route.bizNoColOrDefault();
            if (table == null || col == null) return null;
            List<String> nos = new JdbcTemplate(dataSource)
                    .queryForList("SELECT " + col + " FROM " + table + " WHERE id = ?", String.class, bizId);
            if (nos == null || nos.isEmpty()) return null;
            String no = nos.get(0);
            return (no == null || no.isBlank()) ? null : no;
        } catch (Exception ex) {
            log.warn("resolveBizNo 解析失败 bizType={} bizId={}: {}", bizType, bizId, ex.getMessage());
            return null;
        }
    }

    /**
     * 按实例补解析业务单据号，<b>去重</b>，一次只作用一个实例。
     *
     * <p>{@link #resolveBizNo} 只在发起那一瞬间跑一次，解析失败吞成 warn 返回 null、不阻断发起是
     * 刻意取舍。于是"发起时单号还没写好"／"当时路由表缺这一行"的实例会永久停在
     * {@code biz_no = NULL}——列表页单号列空白，{@code wf_task} 上的同名冗余列一起空。
     * 这个入口用与发起<b>同一规则</b>（同一个 resolveBizNo）补救，不必再手写 UPDATE 改库。</p>
     *
     * <p>刻意不做的两件事：①不批量回填历史 NULL 行（备而未执行）；
     * ②不把已有单号刷成 null——解析不到即返回 0，原值不动。返回 0 的每条路径都落日志说明原因。</p>
     *
     * @param instanceId {@code wf_instance.id}（不是 procInstId）
     * @return 实际更新的 {@code wf_instance} 行数（0 或 1）
     */
    public int refreshBizNo(Long instanceId) {
        if (instanceId == null) {
            return 0;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_id, proc_inst_id, biz_type, biz_id, biz_no FROM wf_instance WHERE id = ?",
                instanceId);
        if (rows.isEmpty()) {
            log.warn("单据号补救：实例不存在 instanceId={}", instanceId);
            return 0;
        }
        Map<String, Object> row = rows.get(0);
        Long instanceTenant = numberOrNull(row.get("tenant_id"));
        Long currentTenant = TenantContext.getTenantId();
        if (instanceTenant == null || !instanceTenant.equals(currentTenant)) {
            log.warn("单据号补救：租户不符，拒绝跨租户写回 instanceId={} 实例租户={} 会话租户={}",
                    instanceId, instanceTenant, currentTenant);
            return 0;
        }
        String bizType = row.get("biz_type") == null ? null : String.valueOf(row.get("biz_type"));
        Long bizId = numberOrNull(row.get("biz_id"));
        String oldNo = row.get("biz_no") == null ? null : String.valueOf(row.get("biz_no"));
        String procInstId = row.get("proc_inst_id") == null ? null : String.valueOf(row.get("proc_inst_id"));
        String resolved = resolveBizNo(bizType, bizId);
        if (resolved == null) {
            log.warn("单据号补救：解析不到单号，保持原值 instanceId={} bizType={} bizId={} 现值={}"
                    + "（该 bizType 可能不在 BillRouteRegistry 白名单，或单据侧单号本身为空）",
                    instanceId, bizType, bizId, oldNo);
            return 0;
        }
        if (resolved.equals(oldNo)) {
            log.info("单据号补救：值已一致，零写入 instanceId={} biz_no={}", instanceId, resolved);
            return 0;
        }
        // WHERE 带上读到的现值：并发下若已被他人改写，这条 UPDATE 命中 0 行而不是覆盖对方结果
        int n = (oldNo == null)
                ? jdbc.update("UPDATE wf_instance SET biz_no = ? WHERE id = ? AND tenant_id = ? AND biz_no IS NULL",
                resolved, instanceId, instanceTenant)
                : jdbc.update("UPDATE wf_instance SET biz_no = ? WHERE id = ? AND tenant_id = ? AND biz_no = ?",
                resolved, instanceId, instanceTenant, oldNo);
        if (n == 0) {
            log.warn("单据号补救：现值在读写之间已变化，本次零写入 instanceId={} 读到={} 欲改为={}",
                    instanceId, oldNo, resolved);
            return 0;
        }
        int taskRows = 0;
        if (procInstId != null) {
            // wf_task.biz_no 是发起时从实例复制的冗余列，实例补好了任务侧不会自动跟上
            taskRows = jdbc.update("UPDATE wf_task SET biz_no = ? WHERE proc_inst_id = ? AND tenant_id = ?"
                    + " AND (biz_no IS NULL OR biz_no <> ?)", resolved, procInstId, instanceTenant, resolved);
        }
        log.info("单据号补救 instanceId={} procInstId={} bizType={} bizId={} biz_no: {} → {}（同步 wf_task {} 行）",
                instanceId, procInstId, bizType, bizId, oldNo, resolved, taskRows);
        return n;
    }

    private static Long numberOrNull(Object v) {
        return v instanceof Number number ? number.longValue() : null;
    }

    /**
     * 路由真值（bizType → [表, 单号列]）只应有一份：{@code WfTaskController.fillBillInfoBatch}、
     * {@code NodeOrgResolver.companyTable} 各写一遍同一件事，三份迟早会漂成三种规则，
     * 所以统一到 {@link BillRouteRegistry}，
     */

}
