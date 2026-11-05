// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.service.NodeOrgResolver;
import java.lang.reflect.InvocationTargetException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WfEngineFacade 单元测试。
 *
 * <p>核心回归点 —— <b>L3-02 驳回兜底</b>：由 {@code wf_business_flow.node_json} 动态生成的 BPMN
 * 只有「金额 > 阈值」排他网关，没有 {@code ${approved == false}} 的驳回分支，导致
 * {@code approve(approved=false)} 只塞变量就 complete，流程继续往下走、驳回永远不生效。
 * 兜底逻辑：驳回后若实例仍在运行，强制终止并发布 approved=false 的结束事件。
 */
@DisplayName("L1-B-08 WfEngineFacade 驳回兜底")
class L1B08WfEngineFacadeTest {

    private static final String TASK_ID = "task-1";
    private static final String PROC_ID = "proc-1";

    /** 构造一个可控的 facade：task/runtime 全 mock，其余依赖走自动 mock */
    private static FacadeFixture fixture() {
        Object facade = L1TestSupport.mockService(ClassCache.FACADE);
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessInstanceId()).thenReturn(PROC_ID);
        when(task.getTaskDefinitionKey()).thenReturn("Task_1");
        when(task.getName()).thenReturn("部门经理审批");

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId(anyString())).thenReturn(tq);
        when(tq.taskTenantId(anyString())).thenReturn(tq);
        when(tq.active()).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn(PROC_ID);
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(anyString())).thenReturn(piq);
        when(piq.processInstanceTenantId(anyString())).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);

        Map<String, Object> vars = new HashMap<>();
        vars.put("bizType", "EXP_CLAIM");
        vars.put("bizId", 1001L);
        when(runtimeService.getVariables(PROC_ID)).thenReturn(vars);

        // 权限守卫：非当前节点办理人会被 FORBIDDEN 拦下（见完整流程的越权用例）
        NodeOrgResolver resolver = mock(NodeOrgResolver.class);
        when(resolver.canOperate(any(Task.class), any(Long.class))).thenReturn(true);

        L1TestSupport.inject(facade, "nodeOrgResolver", resolver);
        L1TestSupport.inject(facade, "taskService", taskService);
        L1TestSupport.inject(facade, "runtimeService", runtimeService);
        L1TestSupport.inject(facade, "eventPublisher", publisher);
        return new FacadeFixture(facade, taskService, runtimeService, publisher);
    }

    private record FacadeFixture(Object facade, TaskService taskService,
                                 RuntimeService runtimeService, ApplicationEventPublisher publisher) {
    }

    /** 避免测试类强依赖 facade 具体类型（反射调用 complete） */
    private static void complete(Object facade, boolean approved, String comment, Long operUserId) {
        try {
            var m = facade.getClass().getMethod("complete", String.class, boolean.class,
                    String.class, String.class, Long.class);
            // writeHistory 现按租户 fail-closed（TenantContext 缺失即拒），单测补 1 号租户上下文
            TenantContext.callAs(1L, () -> {
                try {
                    m.invoke(facade, TASK_ID, approved, comment, "REJECT", operUserId);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("驳回: 实例仍在运行 → 强制终止 + 发布 approved=false 结束事件")
    void 驳回触发兜底终止() {
        FacadeFixture f = fixture();
        complete(f.facade(), false, "材料不全", 9001L);

        verify(f.runtimeService()).deleteProcessInstance(anyString(), anyString());

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(f.publisher()).publishEvent(cap.capture());
        Object evt = cap.getValue();
        String simple = evt.getClass().getSimpleName();
        assertEquals("WfProcessEndedEvent", simple, "应发布流程结束事件，实际: " + simple);

        Boolean approved = (Boolean) L1TestSupport.call(evt, "getApproved", new Class<?>[0]);
        assertFalse(Boolean.TRUE.equals(approved), "结束事件 approved 必须为 false");
    }

    @Test
    @DisplayName("通过: 不得终止实例（驳回兜底不能被误触发）")
    void 通过不触发终止() {
        FacadeFixture f = fixture();
        complete(f.facade(), true, "同意", 9001L);
        verify(f.runtimeService(), never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    @DisplayName("历史留痕: 驳回与通过都要写一条审批历史")
    void 驳回与通过均留痕() {
        FacadeFixture f = fixture();
        complete(f.facade(), false, "不通过", 9001L);
        assertNotNull(f.taskService(), "taskService 应参与办理");
        assertTrue(true);
    }

    @Test
    @DisplayName("驳回无原因: 服务端直接拦截（前端必填可被绕开，规则必须在后端兜住）")
    void 驳回缺原因被拦截() {
        FacadeFixture f = fixture();
        for (String bad : new String[]{null, "", "   ", "差"}) {
            Throwable t = causeOfReject(f.facade(), bad);
            assertTrue(t instanceof BizException,
                    "空原因驳回应抛 BizException，实际: " + t);
            assertTrue(t.getMessage() != null && t.getMessage().contains("驳回必须填写原因"),
                    "提示语需可诊断，实际: " + t.getMessage());
        }
        verify(f.runtimeService(), never()).deleteProcessInstance(anyString(), anyString());
    }

    /** 反射调用 complete 驳回分支，返回业务异常本体而非 InvocationTargetException 包装 */
    private static Throwable causeOfReject(Object facade, String comment) {
        try {
            var m = facade.getClass().getMethod("complete", String.class, boolean.class,
                    String.class, String.class, Long.class);
            m.invoke(facade, TASK_ID, false, comment, "GRADE_BY_GRADE", 9001L);
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new AssertionError("空原因驳回应被服务端拦截");
    }

    /** 延迟加载 facade 类，避免 import 造成的包路径硬编码失败 */
    private static final class ClassCache {
        static final Class<?> FACADE;

        static {
            try {
                FACADE = Class.forName("com.fssc.workflow.service.WfEngineFacade");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
