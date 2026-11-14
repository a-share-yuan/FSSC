package com.fssc.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfProcessLog;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流程处理详细日志监听器（G4 ④层治理）——流程配置「详细日志开关」参数 4.3。
 *
 * <p>为什么挂在 Flowable 全局事件上，而不是在门面里插桩：方案 §二 F3/F4 明确指出存在
 * <b>引擎旁路</b>——{@code empty_strategy=SKIP} 直连 {@code taskService.complete()}、
 * 超时 AUTO_PASS 与空节点跳过"不经过本方法"。门面插桩会<b>漏记这些旁路</b>，
 * 而"卡住的单子"恰恰经常死在旁路上。事件监听器与投影监听器同一规则：一个挂点，全路径覆盖。
 *
 * <p><b>注册策略（与镜像投影开关刻意不同）</b>：本监听器<b>无条件注册</b>，是否真正写入数据库由
 * {@link WfProcessLogService#enabled()}（{@code sys_config.wf.log.detailed.enabled}）逐事件判定。
 * 理由：该参数是"运行时勾选即生效"的参数，若像镜像投影那样在启动期决定是否注册，
 * 运营勾选后必须重启才生效——那又是"配置界面向业务承诺引擎没做的事"（方案 §一 症状 2）。
 * 代价是开关关闭时每个流程事件多一次 {@code sys_config} 单行查询，事件频率＝审批动作频率，可接受。
 *
 * <p><b>失败语义（与投影监听器刻意相反）</b>：{@link #isFailOnException()} 返回 {@code false}。
 * 投影失败必须冒泡（镜像缺行会造成幽灵待办等真实业务后果）；详细日志失败只是<b>少一条观测记录</b>，
 * 让它冒泡会把"记日志失败"升级成"审批失败"，属本末倒置。
 *
 * <p><b>零影响</b>：开关关闭时本监听器不写任何行、不改任何状态、不抛异常——与原先逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfProcessLogListener extends AbstractFlowableEventListener {

    private final WfProcessLogService processLogService;
    private final RuntimeService runtimeService;
    private final WfQueryService wfQueryService;

    /**
     * 已办结任务的近期记忆（WF-PROCLOG-002 去噪用）。
     *
     * <p><b>为什么需要</b>：办结也要删除任务实体，故 {@code ENTITY_DELETED} 对办结任务同样触发；
     * 同一个 {@code task_id} 会在毫秒内先 {@code TASK_COMPLETED} 后 {@code TASK_CANCELLED}，
     * 即每笔办结都多一条假取消。
     *
     * <p><b>为什么靠内存而不查库</b>：办结与实体删除发生在<b>同一个引擎命令、同一线程</b>内，且
     * {@code TASK_COMPLETED} 必先于实体删除。而 {@code wf_task} 镜像行此刻尚未置 DONE
     * ——门面是「先 {@code taskService.complete()}、后写镜像」，故按库判据反而判不出。
     * 真取消的任务从未产生 {@code TASK_COMPLETED}，不会被误伤。
     *
     * <p><b>为何有界</b>：条目在实体删除时即被 {@code remove} 消费（正常生命周期内增删配对），
     * 上限只为兜住"办了但实体未被删"的异常路径，防长期运行无界增长。
     */
    private static final int COMPLETED_MEMO_MAX = 4096;

    private final Set<String> recentlyCompleted = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > COMPLETED_MEMO_MAX;
                }
            }));

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
            return;
        }
        if (!(event.getType() instanceof FlowableEngineEventType type)) {
            return;
        }
        Object entity = entityEvent.getEntity();
        try {
            switch (type) {
                case PROCESS_STARTED -> onProcess(entity, "PROCESS_STARTED", "流程发起");
                case TASK_CREATED -> onTask(entity, "TASK_CREATED", "任务生成");
                case TASK_ASSIGNED -> onTask(entity, "TASK_ASSIGNED", "任务指派");
                case TASK_COMPLETED -> {
                    // 先登记再写日志：实体删除紧随其后（WF-PROCLOG-002），供其判"已办结去噪"
                    if (entity instanceof Task task && task.getId() != null) {
                        recentlyCompleted.add(task.getId());
                    }
                    onTask(entity, "TASK_COMPLETED", "任务办结");
                }
                case ENTITY_DELETED -> {
                    // Flowable 8 无 TASK_DELETED 常量，任务删除由通用 ENTITY_DELETED 承载
                    // WF-PROCLOG-002：办结也会删除任务实体，故先判"本任务是否已产生 TASK_COMPLETED"，
                    // 命中即视为办结去噪、不写 TASK_CANCELLED（真取消从未办结，不会被误伤）。
                    if (entity instanceof Task task && !recentlyCompleted.remove(task.getId())) {
                        onTask(task, "TASK_CANCELLED", "任务取消");
                    }
                }
                case PROCESS_COMPLETED -> onProcess(entity, "PROCESS_COMPLETED", "流程结束");
                case TIMER_FIRED -> onProcess(entity, "TIMER_FIRED", "定时器触发");
                default -> {
                    // 未订阅的事件类型
                }
            }
        } catch (Exception ex) {
            // 观测失败绝不冒泡（见类注释"失败语义"）
            log.warn("流程处理详细日志记录失败（忽略）type={}: {}", type, ex.getMessage());
        }
    }

    @Override
    public boolean isFailOnException() {
        // 刻意返回 false：详细日志是观测增强，不能把"记日志失败"升级成"审批失败"
        return false;
    }

    // 事件归一

    private void onProcess(Object entity, String logType, String scene) {
        // 必须用 getProcessInstanceId()，不能用 getId()：PROCESS_STARTED 那一刻执行实例 id
        // 尚未定型为流程实例 id，同单后续事件（TASK_COMPLETED 等）用的是另一个号，
        // 用 getId() 会写入一条既不在 wf_instance 也不在 ACT_HI_PROCINST 的悬空行，
        // 使"按实例查时间线"永久丢失「流程发起」这一行。
        if (!(entity instanceof ProcessInstance pi)) {
            return;
        }
        String procInstId = pi.getProcessInstanceId();
        if (procInstId == null) {
            return;
        }
        if (!processLogService.enabled()) {
            return;
        }
        Map<String, Object> vars = varsQuietly(procInstId);
        WfProcessLog row = base(procInstId);
        row.setLogType(logType);
        fillBiz(row, vars);
        row.setContent(scene + "：" + nvl(row.getBizNo(), pi.getProcessDefinitionKey()));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("scene", scene);
        d.put("processDefinitionKey", nvl(pi.getProcessDefinitionKey(), ""));
        d.put("approved", String.valueOf(vars == null ? null : vars.get("approved")));
        d.put("resultAction", String.valueOf(vars == null ? null : vars.get("ActionResult")));
        row.setDetail(json(d));
        processLogService.write(row);
    }

    private void onTask(Object entity, String logType, String scene) {
        if (!(entity instanceof Task task) || task.getId() == null) {
            return;
        }
        if (!processLogService.enabled()) {
            return;
        }
        Map<String, Object> vars = varsQuietly(task.getProcessInstanceId());
        WfProcessLog row = base(task.getProcessInstanceId());
        row.setLogType(logType);
        row.setTaskId(task.getId());
        row.setNodeKey(task.getTaskDefinitionKey());
        row.setNodeName(task.getName());
        row.setAssigneeId(toUserIdOrNull(task.getAssignee()));
        fillBiz(row, vars);
        row.setContent(scene + "：" + nvl(task.getName(), task.getTaskDefinitionKey())
                + "（办理人 " + nvl(String.valueOf(row.getAssigneeId()), "待认领") + "）");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("scene", scene);
        d.put("assignee", String.valueOf(task.getAssignee()));
        d.put("approved", String.valueOf(vars == null ? null : vars.get("approved")));
        d.put("resultAction", String.valueOf(vars == null ? null : vars.get("ActionResult")));
        d.put("resultActionName", String.valueOf(vars == null ? null : vars.get("ActionResultName")));
        d.put("comment", String.valueOf(vars == null ? null : vars.get("comment")));
        row.setDetail(json(d));
        processLogService.write(row);
    }

    // 工具

    /**
     * 构造基础行：租户从镜像实例行取（事件可能脱离请求上下文，如作业线程），
     * 取不到再回退当前线程租户；两者皆无则写空租户（{@link WfProcessLogService#write} 内会兜底，
     * 租户拦截器拦下时只 warn 不冒泡——日志行丢一条，不影响业务）。
     */
    private WfProcessLog base(String procInstId) {
        WfProcessLog row = new WfProcessLog();
        row.setProcInstId(procInstId);
        Long tenantId = null;
        WfInstance[] holder = new WfInstance[1];
        try {
            TenantContext.runIgnoreTenant(() -> {
                try {
                    holder[0] = wfQueryService.instanceByProcInstId(procInstId);
                } catch (Exception ex) {
                    log.debug("流程日志查实例行失败 procInstId={}: {}", procInstId, ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.debug("流程日志忽略租户上下文执行失败 procInstId={}: {}", procInstId, ex.getMessage());
        }
        WfInstance inst = holder[0];
        if (inst != null) {
            tenantId = inst.getTenantId();
            row.setModelKey(inst.getModelKey());
        }
        if (tenantId == null && TenantContext.hasContext() && !TenantContext.isIgnoreTenant()) {
            try {
                tenantId = TenantContext.getTenantId();
            } catch (Exception ignore) {
                // 平台上下文：无归属租户
            }
        }
        row.setTenantId(tenantId);
        row.setOperUserId(currentUserId());
        return row;
    }

    private void fillBiz(WfProcessLog row, Map<String, Object> vars) {
        if (vars == null) {
            return;
        }
        row.setBizType(strVar(vars, "bizType"));
        row.setBizId(longVar(vars, "bizId"));
        row.setBizNo(strVar(vars, "bizNo"));
        row.setTitle(strVar(vars, "title"));
        if (row.getModelKey() == null) {
            row.setModelKey(strVar(vars, "modelKey"));
        }
    }

    private Map<String, Object> varsQuietly(String procInstId) {
        if (procInstId == null) {
            return null;
        }
        try {
            return runtimeService.getVariables(procInstId);
        } catch (Exception ex) {
            // 实例已结束时引擎会清理变量——属正常，不告警
            return null;
        }
    }

    private static String strVar(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Long longVar(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 明细 JSON 序列化（失败降级为 null，绝不让日志序列化问题影响主链路） */
    private static String json(Map<String, Object> map) {
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long currentUserId() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }

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

    private static String nvl(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}