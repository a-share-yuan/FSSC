// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.dto.WfProjectionEvent;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfNodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 流程镜像投影监听器（G1「单一真值：镜像投影化」＋④层「流程处理模式」，2026-09-24）。
 *
 * <p><b>它解决什么</b>：方案 §二 F1~F4 认定"镜像写点分散 + 无事务 + 失败吞掉 + 引擎旁路"是全部问题的根——
 * {@code wf_instance}/{@code wf_task} 的"何时推进"由 {@code WfEngineFacade} 里约 30 处手工调用决定，
 * 任何一处漏写就产生幽灵待办/实例滞留。G1 把这两张表的写路径<b>收口</b>：Flowable 是唯一执行真值，
 * 本监听器把引擎事件翻译成投影事件。
 *
 * <p><b>本类只做两件事</b>（投影动作本身在 {@link WfProjectionService}，全系统仅此一份）：
 * <ol>
 *   <li><b>解析</b>：在事件当场把"只有 Flowable 实体/流程变量还在时才解析得准"的字段解出来装进
 *       {@link WfProjectionEvent}——候选组（{@code NodeOrgResolver}）、到期时间（{@code WfNodeConfigResolver}）、
 *       业务归属（流程变量优先 → 镜像实例行回退）；</li>
 *   <li><b>投递</b>：按 {@code sys_config.wf.process.mode} 决定走哪条路——
 *       {@code SYNC}（缺省）＝内联调 {@link WfProjectionService#apply}；
 *       {@code QUEUE}＝{@link WfProcessOutboxService#enqueue} 入 {@code wf_process_outbox}，
 *       由 {@code WfOutboxConsumerJob} 取出后调<b>同一个</b> {@code apply}。</li>
 * </ol>
 * 两条路共用一份投影实现，因此"同步/异步"不会产生两套子逻辑（那是双账漂移的老成因）。
 *
 * <p><b>注册策略：无条件注册（2026-09-24 由"仅开关打开时注册"改为无条件）</b>。
 * 运营切到 QUEUE 就必须重启才生效——那又是"配置界面向业务承诺引擎没做的事"（方案 §一 症状 2）。
 * 改为无条件注册后，默认态的代价是<b>每个流程事件一次内存布尔判定</b>（{@link WfProcessOutboxService#queueModeEnabled()}
 * 带 5 秒 TTL 缓存，非每次查库），且判定失败一律 fail-closed 到 SYNC：
 * <pre>
 *   if (!queueMode &amp;&amp; !projectionEnabled) return;   // 默认态：不读库、不解析、不写任何行
 * </pre>
 *
 * <p><b>失败语义</b>：与 G0 一致——写失败调 {@link WfMirrorDefectRecorder#record} 记缺陷并冒泡
 * （{@link #isFailOnException()} 返回 true）。<b>唯一例外</b>是"上下文已不可恢复"（引擎已清理变量、
 * 取不到租户）：此时记缺陷后 {@code return}，绝不抛——中间事件上冒泡会让该实例永远无法收尾（死循环）。
 *
 * <h3>字段级映射清单（方案 §六 G1 要求）</h3>
 *
 * <p>字段来源优先级：<b>流程变量优先</b>（{@code runtimeService.getVariables}），缺失回退 {@code wf_instance}
 * 镜像行（变量会被引擎清理，视图行不会）；两者皆无的 NOT NULL 列按兜底值写入。{@code bizNo} 由门面
 * {@code start()} 新注入变量（此前它只在门面里现算、不落变量），故监听器能取到。
 *
 * <p><b>本次搬迁的一处刻意修复（WF-PROCLOG-001 同源）</b>：原实现取 {@code pi.getId()} 作为流程实例 ID，
 * 实测 {@code PROCESS_STARTED} 落成 {@code 457e3bb8-…} 而同单后续事件全是 {@code 457e1494-…}
 * ——该时刻执行实例 id 尚未定型为流程实例 id，写出的是一行<b>既不在 wf_instance 也不在 ACT_HI_PROCINST
 * 的悬空行</b>，使"按实例查时间线"永久丢失「流程发起」。现统一用 {@code pi.getProcessInstanceId()}。
 *
 * <p><b>已知取舍（不粉饰）</b>：
 * <ol>
 *   <li>{@code current_node}/{@code current_assignee} 是"最后一条任务"的近似值，不等价于门面
 *       {@code joinNodeNames} 的并联合集口径；门面对应写点被守卫跳过后，并联节点在实例视图上只显示其一。</li>
 *   <li>{@code wf_task_history} 在过渡期<b>双写</b>：门面仍写（并在 comment 打来源标记），投影也写。
 *       两行内容相近，属方案允许的过渡态，由 G0 对账器比对。</li>
 *   <li>历史行的 {@code action}：投影只认得"通过/驳回"两态（{@code approved} 变量），
 *       机器路径的 {@code AUTO_PASS}/{@code ESCALATE}/{@code TIMEOUT_RELEASE} 精细化动作
 *       仍由门面双写那一行承载——过渡期不丢信息；等门面停写历史后再单独收口。</li>
 *   <li>{@code TIMER_FIRED} 只留痕：当前架构超时走 {@code WfTimeoutJob} 轮询镜像表，引擎定时器未启用
 *       （方案 §二 F5/F7），此处仅把事件记下来供可观测，不做镜像投影、也不入队。</li>
 *   <li>{@code TASK_DELETED} 在方案里是"任务删除"语义，Flowable 8 的 {@code FlowableEngineEventType}
 *       没有该常量，实际由通用 {@code ENTITY_DELETED} 承载，故注册的是后者、在监听器内按实体类型
 *       （{@code instanceof Task}）过滤。副作用是订阅面变宽（其他实体的删除也会到达本监听器），
 *       但非 Task 实体直接忽略。</li>
 *   <li>时间字段经信封传的是 epoch 毫秒（见 {@link WfProjectionEvent}），故 {@code create_time}
 *       精度从纳秒截断到毫秒——对审批流无实际影响。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfMirrorProjectionListener extends AbstractFlowableEventListener {

    /** {@code wf_task.candidate_ids} 列宽 varchar(1000)，与门面同一截断口径 */
    private static final int CANDIDATE_MAX_LEN = 990;

    private final RuntimeService runtimeService;
    private final WfQueryService wfQueryService;
    private final WfMirrorDefectRecorder mirrorDefectRecorder;
    private final NodeOrgResolver nodeOrgResolver;
    private final WfNodeConfigResolver wfNodeConfigResolver;
    /** 投影动作的唯一实现：同步内联与队列消费共用（本类不重复实现任何投影子逻辑） */
    private final WfProjectionService projectionService;
    /** 队列模式投递载体：只有 {@code QUEUE} 模式才真正写表 */
    private final WfProcessOutboxService outboxService;

    /**
     * G1 开关：{@code fssc.wf.mirror.projection-enabled}，默认 {@code false}。
     *
     * <p>本类<b>无条件注册</b>（见类注释），故该开关在本类内的语义是"是否内联投影"：
     * 关闭且模式为 SYNC ⇒ 本监听器什么都不做（门面照旧手工写，行为与改造前逐字一致）。
     */
    @org.springframework.beans.factory.annotation.Value("${fssc.wf.mirror.projection-enabled:false}")
    private boolean projectionEnabled;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
            return;
        }
        if (!(event.getType() instanceof FlowableEngineEventType type)) {
            return;
        }
        Object entity = entityEvent.getEntity();
        // TIMER_FIRED 最早分流：它既不投影也不入队，不必付模式判定的代价
        if (type == FlowableEngineEventType.TIMER_FIRED) {
            log.info("流程定时器事件（留痕，当前超时走轮询、引擎定时器未启用）: entity={}", describe(entity));
            return;
        }
        // Flowable 8 无 TASK_DELETED 常量：任务实体删除由通用 ENTITY_DELETED 承载，按实体类型过滤
        if (type == FlowableEngineEventType.ENTITY_DELETED && !(entity instanceof Task)) {
            return;
        }
        boolean relevant = switch (type) {
            case PROCESS_STARTED, TASK_CREATED, TASK_ASSIGNED, TASK_COMPLETED,
                 ENTITY_DELETED, PROCESS_COMPLETED -> true;
            default -> false;
        };
        if (!relevant) {
            return;
        }
        // 投递方式判定：QUEUE 走发件箱，projectionEnabled 走内联，两者皆否＝默认态直接返回。
        // queueModeEnabled() 带 5 秒 TTL 缓存，非逐事件查库；判定失败 fail-closed 到 SYNC。
        boolean queueMode = outboxService.queueModeEnabled();
        if (!queueMode && !projectionEnabled) {
            return; // 默认态：不解析、不读库、不写任何行
        }
        try {
            WfProjectionEvent envelope = buildEnvelope(type, entity);
            if (envelope == null) {
                return; // 上下文不可恢复/实体不完整：已在构造期记缺陷
            }
            if (queueMode) {
                outboxService.enqueue(envelope);
            } else {
                projectionService.apply(envelope);
            }
        } catch (Exception ex) {
            // G0 口径：投影写失败"记缺陷 + 冒泡"。上下文不可恢复的分支已在构造期 return，
            // 走到这里的都是可恢复的失败（唯一键冲突、租户拦截、库不可用），必须让界面看得见。
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED,
                    "WfMirrorProjectionListener." + type, null, "entity=" + describe(entity), ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.SYSTEM_ERROR, "流程镜像投影失败: " + ex.getMessage());
        }
    }

    @Override
    public boolean isFailOnException() {
        // 与门面 PROCESS_COMPLETED 监听器同口径：投影失败即让引擎命令失败、界面可见、可重试。
        // 唯一的不可恢复分支（取不到租户）已记缺陷后 return，不会在此冒泡成死循环。
        return true;
    }

    // ---------------- 事件信封构造（解析在此完成，写库在 WfProjectionService） ----------------

    private WfProjectionEvent buildEnvelope(FlowableEngineEventType type, Object entity) {
        if (entity instanceof Task task) {
            return buildTaskEnvelope(type, task);
        }
        if (entity instanceof ProcessInstance pi) {
            return buildProcessEnvelope(type, pi);
        }
        return null;
    }

    private WfProjectionEvent buildProcessEnvelope(FlowableEngineEventType type, ProcessInstance pi) {
        // WF-PROCLOG-001 同源修复：必须用 getProcessInstanceId()，不能用 getId()
        // （实测该时刻 getId() 尚未定型为流程实例 id，会写出不在 wf_instance / ACT_HI_PROCINST 的悬空行）
        String procInstId = pi.getProcessInstanceId();
        if (procInstId == null) {
            return null;
        }
        WfInstance inst = instQuietly(procInstId);
        Long tenantId = tenantIdOf(inst);
        if (tenantId == null) {
            recordContextMissing(type.name(), procInstId);
            return null;
        }
        boolean started = type == FlowableEngineEventType.PROCESS_STARTED;
        Map<String, Object> vars = varsQuietly(procInstId);
        WfProjectionEvent e = base(procInstId, tenantId);
        e.setEventType(started ? WfProjectionService.EV_PROCESS_STARTED : WfProjectionService.EV_PROCESS_COMPLETED);
        e.setProcDefId(pi.getProcessDefinitionId());
        e.setModelKey(pi.getProcessDefinitionKey() != null ? pi.getProcessDefinitionKey()
                : keyOfDefinition(pi.getProcessDefinitionId()));
        if (started) {
            // 发起：归属信息只能从变量取（此刻 wf_instance 镜像行尚未建）
            e.setBizType(strVar(vars, "bizType", null));
            e.setBizId(longVar(vars, "bizId", null));
            e.setBizNo(strVar(vars, "bizNo", null));
            e.setTitle(strVar(vars, "title", null));
            e.setAmount(decVar(vars, "amount", null));
            e.setStartUserId(currentUserId());
        } else {
            // 结束：只判结果（approved 变量）；变量可能已被引擎清理，此时按 REJECTED 口径与原实现一致
            e.setApproved(Boolean.TRUE.equals(vars == null ? null : vars.get("approved")));
        }
        return e;
    }

    private WfProjectionEvent buildTaskEnvelope(FlowableEngineEventType type, Task task) {
        if (task.getId() == null) {
            return null;
        }
        String procInstId = task.getProcessInstanceId();
        WfInstance inst = instQuietly(procInstId);
        Long tenantId = tenantIdOf(inst);
        if (tenantId == null) {
            recordContextMissing(type.name(), procInstId);
            return null;
        }
        Map<String, Object> vars = varsQuietly(procInstId);
        WfProjectionEvent e = base(procInstId, tenantId);
        e.setEventType(eventTypeName(type));
        e.setTaskId(task.getId());
        e.setNodeKey(task.getTaskDefinitionKey());
        e.setNodeName(task.getName());
        e.setAssigneeId(toUserIdOrNull(task.getAssignee()));
        e.setAssigneeRaw(task.getAssignee());
        e.setOperUserId(toUserIdOrNull(task.getAssignee()));
        e.setOperUserName(currentUserName());
        e.setComment(strVar(vars, "comment", null));
        e.setApproved(Boolean.TRUE.equals(vars == null ? null : vars.get("approved")));
        // 业务归属：变量优先 → 镜像实例行回退（与改造前 buildTaskRow 的取数口径逐字一致）
        e.setBizType(strVar(vars, "bizType", inst == null ? null : inst.getBizType()));
        e.setBizId(longVar(vars, "bizId", inst == null ? null : inst.getBizId()));
        e.setBizNo(strVar(vars, "bizNo", inst == null ? null : inst.getBizNo()));
        e.setTitle(strVar(vars, "title", inst == null ? null : inst.getTitle()));
        e.setAmount(decVar(vars, "amount", inst == null ? null : inst.getAmount()));
        e.setStartUserId(inst == null ? currentUserId() : inst.getStartUserId());
        e.setModelKey(inst != null && inst.getModelKey() != null && !inst.getModelKey().isBlank()
                ? inst.getModelKey() : keyOfDefinition(task.getProcessDefinitionId()));
        if (type == FlowableEngineEventType.TASK_CREATED) {
            // 仅建行需要：任务创建时刻、候选组、到期时间（其余事件用不到，不付这部分查询代价）
            LocalDateTime createTime = toLocalDateTime(task.getCreateTime());
            e.setTaskCreateTimeMillis(toMillis(createTime));
            if (e.getAssigneeId() == null) {
                e.setCandidateIds(resolveCandidateIds(task));
            }
            e.setDueTimeMillis(resolveDueTimeMillis(e.getModelKey(), task.getTaskDefinitionKey(), createTime));
        }
        return e;
    }

    /** 事件类型归一为投影契约名（{@code ENTITY_DELETED} 在本监听器内已确定实体是 Task ⇒ 任务删除语义） */
    private static String eventTypeName(FlowableEngineEventType type) {
        return switch (type) {
            case PROCESS_STARTED -> WfProjectionService.EV_PROCESS_STARTED;
            case TASK_CREATED -> WfProjectionService.EV_TASK_CREATED;
            case TASK_ASSIGNED -> WfProjectionService.EV_TASK_ASSIGNED;
            case TASK_COMPLETED -> WfProjectionService.EV_TASK_COMPLETED;
            case ENTITY_DELETED -> WfProjectionService.EV_TASK_DELETED;
            case PROCESS_COMPLETED -> WfProjectionService.EV_PROCESS_COMPLETED;
            default -> type.name();
        };
    }

    private WfProjectionEvent base(String procInstId, Long tenantId) {
        WfProjectionEvent e = new WfProjectionEvent();
        e.setTenantId(tenantId);
        e.setProcInstId(procInstId);
        return e;
    }

    // ---------------- 解析工具 ----------------

    private String resolveCandidateIds(Task task) {
        List<Long> operable = nodeOrgResolver.listOperableUserIds(task);
        if (operable == null || operable.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long uid : operable) {
            String next = "," + uid;
            if (sb.length() + next.length() + 1 > CANDIDATE_MAX_LEN) {
                break;
            }
            sb.append(next);
        }
        return sb.length() == 0 ? null : sb.append(',').toString();
    }

    /**
     * 到期时刻（epoch 毫秒）：{@code wf_node_config.timeout_hours}，与门面同一份口径。
     * 解析失败/未配置返回 null（不阻断投影，与改造前 {@code log.debug} 后继续一致）。
     */
    private Long resolveDueTimeMillis(String modelKey, String nodeKey, LocalDateTime createTime) {
        if (modelKey == null || createTime == null) {
            return null;
        }
        try {
            WfNodeConfig cfg = wfNodeConfigResolver.resolve(modelKey, nodeKey);
            if (cfg != null && cfg.getTimeoutHours() != null && cfg.getTimeoutHours() > 0) {
                return toMillis(createTime.plusHours(cfg.getTimeoutHours()));
            }
        } catch (Exception ex) {
            log.debug("投影到期时间解析失败 model={} node={}: {}", modelKey, nodeKey, ex.getMessage());
        }
        return null;
    }

    /**
     * 取镜像实例行（<b>忽略租户</b>）：事件可能脱离请求上下文（作业线程/异步收尾），
     * 而本表读取是探测性的、用它的 tenant_id 反推归属，故必须绕过租户过滤。
     */
    private WfInstance instQuietly(String procInstId) {
        if (procInstId == null) {
            return null;
        }
        WfInstance[] holder = new WfInstance[1];
        try {
            TenantContext.runIgnoreTenant(() -> holder[0] = wfQueryService.instanceByProcInstId(procInstId));
        } catch (Exception ex) {
            log.warn("投影取实例行失败 procInstId={}: {}", procInstId, ex.getMessage());
        }
        return holder[0];
    }

    /**
     * 解析归属租户：镜像实例行优先（事件可能脱离请求上下文），取不到再回退当前线程租户。
     * 两者皆无返回 null（调用方据此判定"上下文不可恢复"，记缺陷后跳过、不冒泡）。
     */
    private Long tenantIdOf(WfInstance inst) {
        if (inst != null && inst.getTenantId() != null) {
            return inst.getTenantId();
        }
        if (TenantContext.hasContext() && !TenantContext.isIgnoreTenant()) {
            try {
                return TenantContext.getTenantId();
            } catch (Exception ignore) {
                // 平台上下文（tenantId=null 且非 ignore）：无归属租户
            }
        }
        return null;
    }

    /** 不可恢复分支：记缺陷（WF-MIRROR-003）后返回，绝不冒泡成死循环。 */
    private void recordContextMissing(String scene, String procInstId) {
        mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_ENGINE_BYPASS,
                "WfMirrorProjectionListener." + scene, null,
                "procInstId=" + procInstId + "（引擎已清理上下文，投影跳过）", null);
    }

    /** 变量取数容错：引擎已清理变量时返回 null（调用方回退镜像实例行）。 */
    private Map<String, Object> varsQuietly(String procInstId) {
        if (procInstId == null) {
            return null;
        }
        try {
            return runtimeService.getVariables(procInstId);
        } catch (Exception ex) {
            log.debug("投影取流程变量失败（引擎已清理）procInstId={}: {}", procInstId, ex.getMessage());
            return null;
        }
    }

    /** 变量优先、缺失回退；空串一律视为缺失（否则会把 NOT NULL 列写成空串）。 */
    private static String strVar(Map<String, Object> vars, String key, String fallback) {
        Object v = vars == null ? null : vars.get(key);
        String s = v == null ? null : String.valueOf(v);
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static Long longVar(Map<String, Object> vars, String key, Long fallback) {
        Object v = vars == null ? null : vars.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static BigDecimal decVar(Map<String, Object> vars, String key, BigDecimal fallback) {
        Object v = vars == null ? null : vars.get(key);
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Flowable 的 assignee 是字符串用户 ID；非数字（角色码等）返回 null。 */
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

    /** 任务创建时间为 null 时兜底为 now（与改造前 {@code toLocalDateTime} 同口径）。 */
    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 流程定义 key（形如 {@code key:version:deployId} 的前缀） */
    private static String keyOfDefinition(String procDefId) {
        return procDefId == null ? null : procDefId.split(":")[0];
    }

    /** 当前登录用户 ID，无则 0（与门面 approverUserIdOrDefault 同口径） */
    private static Long currentUserId() {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.get();
        return u == null || u.getUserId() == null ? 0L : u.getUserId();
    }

    /**
     * 当前登录用户姓名（无登录上下文则 null）。
     *
     * <p>D-DEC-161：这里原来直取 {@code getUsername()}，而 {@code username} 在本系统是<b>工号</b>
     * （sys_user.user_name，如 Y000147），写进 {@code wf_task_history.oper_user_name} 后
     * 审批链上就显示成一串工号；读侧 {@code WfQueryService#namesByIds} 早已是
     * 「nick_name（姓名）优先、空则回落 user_name（工号）」，两条口径不一致会让同一份历史
     * 里既有人名又有工号。改为同口径：realName（=nick_name）优先，空白才回落 username。
     *
     * <p>登录态缺失仍返回 null（D-DEC-280）：定时任务/引擎回调等没有 UserContext 的链路
     * 不能拿工号或姓名冒充经办人，宁可留空由读侧按 oper_user_id 兜底。
     */
    private static String currentUserName() {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.get();
        if (u == null) {
            return null;
        }
        String realName = u.getRealName();
        return realName == null || realName.isBlank() ? u.getUsername() : realName;
    }

    private static String describe(Object entity) {
        if (entity == null) {
            return "null";
        }
        if (entity instanceof Task t) {
            return "task:" + t.getId();
        }
        if (entity instanceof ProcessInstance pi) {
            return "procInst:" + pi.getProcessInstanceId();
        }
        return entity.getClass().getSimpleName();
    }
}