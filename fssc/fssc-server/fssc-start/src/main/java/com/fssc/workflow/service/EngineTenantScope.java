// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.fssc.framework.tenant.TenantContext;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;

/**
 * 引擎租户谓词统一出口。
 *
 * <p><b>为什么要有这个类</b>：引擎调用点分散在 17 个类、181 处，若每处各写一遍
 * 「取 TenantContext → 判空 → 拼谓词」，口径必然漂移（改造前全仓 {@code taskTenantId} 命中 0 处
 * 就是漂移的极端形态：一处都没写）。这里把「有租户上下文就加谓词、没有就不加」收敛成一处，
 * 调用方只换构造方法，不各自判断。
 *
 * <p><b>无上下文时不加谓词，是刻意保留的能力</b>：调度线程（{@code WfTimeoutJob}、
 * {@code WfLedgerReconcileJob}）与启动期部署需要跨租户扫描，它们在 {@code runIgnoreTenant} 内执行、
 * {@link TenantContext#getTenantId()} 返回 null。此类调用段属「点名登记的跨租户扫描」，
 * 扫描到行后必须逐条 {@code callAs} 回本租户处置（既有铁律，见 WfTimeoutJob 注释）。
 *
 * <p>不要在这里偷偷回落到某个"默认租户"：无上下文却硬塞一个 tid，会让调度线程只能看到一个租户，
 * 表现为"部分租户的超时任务永远不催办"，比不加谓词更难发现。
 */
public final class EngineTenantScope {

    private EngineTenantScope() {
    }

    /** 当前引擎租户串；无租户上下文（调度/启动期）返回 null */
    public static String tenantStr() {
        // 必须用 peek 而不是 getTenantId：调度线程的 ThreadLocal 为空，
        // getTenantId() 会抛「TenantContext 未初始化」，导致对账/超时等作业整轮失败。
        Long tid = TenantContext.peekTenantId();
        return tid == null ? null : String.valueOf(tid);
    }

    /** 任务查询：带租户谓词（待办、已办、候选组一律经此构造） */
    public static TaskQuery taskQuery(TaskService taskService) {
        TaskQuery q = taskService.createTaskQuery();
        String tid = tenantStr();
        return tid == null ? q : q.taskTenantId(tid);
    }

    /** 历史任务查询：带租户谓词 */
    public static HistoricTaskInstanceQuery historicTaskQuery(HistoryService historyService) {
        HistoricTaskInstanceQuery q = historyService.createHistoricTaskInstanceQuery();
        String tid = tenantStr();
        return tid == null ? q : q.taskTenantId(tid);
    }

    /** 流程定义查询：带租户谓词（{@code latestVersion()} 语义随之从「全局最新」变「本租户最新」） */
    public static ProcessDefinitionQuery definitionQuery(RepositoryService repositoryService) {
        ProcessDefinitionQuery q = repositoryService.createProcessDefinitionQuery();
        String tid = tenantStr();
        return tid == null ? q : q.processDefinitionTenantId(tid);
    }

    /** 在办实例查询：带租户谓词 */
    public static ProcessInstanceQuery processInstanceQuery(RuntimeService runtimeService) {
        ProcessInstanceQuery q = runtimeService.createProcessInstanceQuery();
        String tid = tenantStr();
        return tid == null ? q : q.processInstanceTenantId(tid);
    }

    /** 历史实例查询：带租户谓词 */
    public static HistoricProcessInstanceQuery historicProcessQuery(HistoryService historyService) {
        HistoricProcessInstanceQuery q = historyService.createHistoricProcessInstanceQuery();
        String tid = tenantStr();
        return tid == null ? q : q.processInstanceTenantId(tid);
    }
}
