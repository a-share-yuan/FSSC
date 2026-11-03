// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.delegate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.job.WfTimeoutJob;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.EngineTenantScope;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * G2 第 3 条：BPMN 边界定时器的超时处置 Delegate —— 超时的<b>第一触发点</b>，
 * 替代原先"只能靠 60 秒轮询发现"的被动模式。
 *
 * <p><b>为什么是一个薄壳</b>：本类<b>不复制任何处置逻辑</b>，只做三件事——
 * ① 从 {@code currentActivityId} 反解出节点键（服务任务 id 生成为 {@code TimeoutAct_<userTaskId>}）；
 * ② 找到该节点在办的 Flowable 任务与 {@code wf_task} 镜像行；
 * ③ 交回 {@link WfTimeoutJob#handleTimeoutTask} 执行三档语义（催办 / 自动通过 / 升级转办）。
 * 这样"定时器触发"与"兜底轮询触发"走的是同一段代码，白名单门槛、零 admin 铁律、
 * {@code due_time} 优先规则全都一致。
 *
 * <p><b>为什么定时器是非中断的</b>：三档动作都不需要打断原任务——催办只发通知、
 * 转办只改办理人、自动通过由门面走机器路径完成。若用中断式边界事件，任务会被引擎直接取消，
 * 反而制造出"待办凭空消失"的幽灵任务。故生成端固定 {@code cancelActivity="false"}。
 *
 * <p><b>为什么不向外抛异常</b>：本 Delegate 由 Flowable 异步执行器调用，抛异常会让作业进入重试队列
 * （反复重试同一张单，甚至触发引擎的"死信"策略）。超时处置本身是"尽力而为的治理动作"，
 * 失败记日志即可；真正的兜底由 {@code WfTimeoutJob} 的 60 秒扫描承担。
 */
@Slf4j
@Component("wfTimeoutBoundaryDelegate")
@RequiredArgsConstructor
public class WfTimeoutBoundaryDelegate implements JavaDelegate {

    /** 生成端约定的服务任务 id 前缀（{@code WfBpmnTemplateService.appendTimeout}） */
    static final String ACT_PREFIX = "TimeoutAct_";

    private final TaskService taskService;
    private final WfTaskMapper wfTaskMapper;
    private final WfTimeoutJob wfTimeoutJob;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            String activityId = execution.getCurrentActivityId();
            if (activityId == null || !activityId.startsWith(ACT_PREFIX)) {
                log.warn("超时边界 Delegate 收到非预期活动: {}", activityId);
                return;
            }
            String nodeKey = activityId.substring(ACT_PREFIX.length());
            String procInstId = execution.getProcessInstanceId();
            List<Task> tasks = EngineTenantScope.taskQuery(taskService)
                    .processInstanceId(procInstId)
                    .taskDefinitionKey(nodeKey)
                    .list();
            if (tasks == null || tasks.isEmpty()) {
                // 定时器排队期间任务已被办结/实例已终止：无事可做，静默返回（不制造幽灵动作）
                log.debug("超时边界触发但节点已无在办任务 procInst={} node={}", procInstId, nodeKey);
                return;
            }
            Task ftask = tasks.get(0);
            WfTask mirror = ignoreTenant(() -> wfTaskMapper.selectOne(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getTaskId, ftask.getId())
                            .last("LIMIT 1")));
            if (mirror == null) {
                // 镜像行缺失（投影延迟/历史数据）：交给兜底扫描与双账对账，这里不猜
                log.warn("超时边界触发但缺 wf_task 镜像行 taskId={}", ftask.getId());
                return;
            }
            Long tenantId = mirror.getTenantId();
            TenantContext.callAs(tenantId, () -> wfTimeoutJob.handleTimeoutTask(mirror, List.of()));
        } catch (Exception ex) {
            log.error("超时边界处置失败 execution={}", execution == null ? null : execution.getId(), ex);
        }
    }

    /** 跨租户读取镜像行：引擎线程通常不带租户上下文，先忽略租户再按行上的 tenant_id 建上下文处置 */
    private <T> T ignoreTenant(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
