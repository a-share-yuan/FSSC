// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.l1;

import com.fssc.system.entity.SysRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfProcessConfig;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfProcessConfigMapper;
import com.fssc.workflow.service.WfBpmnChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发布期硬校验单测（金额网关）。
 *
 * <p>为什么必须有正向用例：若"发起未传 amount 会告警"的判据读的是空表，
 * 线上一次都没响过也没人发现。只证"现网 82 个模型 0 error"无法区分
 * "规则对"与"规则永远不触发"，所以这里各造一个坏结构，要求它<b>必须</b>被判下来。
 *
 * <p>三条口径：①假档位=error；②零审批短路=error；③金额升级链的低档直达 End=warning
 * （21 处在用网关即此形态，判 error 会把在用流程挡在发布门外，故这一档按 warning 降级并登记）。
 */
@DisplayName("L1-B-09 WfBpmnChecker 金额网关硬校验")
class L1B09WfBpmnAmountGateTest {

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://fssc\">";

    private static SysRole role(String code) {
        SysRole r = new SysRole();
        r.setRoleCode(code);
        r.setStatus("NORMAL");
        return r;
    }

    /** 只放行这几个角色码，其余按"sys_role 不存在"报错——用例会因此失败，逼我们只写真实角色 */
    private static WfBpmnChecker checker(List<String> roleCodes, List<WfProcessConfig> bindings, List<WfModel> models) {
        SysRoleMapper roles = mock(SysRoleMapper.class);
        when(roles.selectList(any())).thenAnswer(inv -> roleCodes.stream().map(L1B09WfBpmnAmountGateTest::role).toList());
        WfProcessConfigMapper pcm = mock(WfProcessConfigMapper.class);
        when(pcm.selectList(any())).thenReturn(bindings);
        WfModelMapper mm = mock(WfModelMapper.class);
        when(mm.selectList(any())).thenReturn(models);
        return new WfBpmnChecker(roles, pcm, mm);
    }

    private static WfProcessConfig binding(String bizType, String modelKey) {
        WfProcessConfig c = new WfProcessConfig();
        c.setBizType(bizType);
        c.setModelKey(modelKey);
        c.setStatus("NORMAL");
        return c;
    }

    private static String flow(String id, String from, String to, String cond) {
        String body = cond == null ? "/>" : "><conditionExpression>${" + cond + "}</conditionExpression></sequenceFlow>";
        return "<sequenceFlow id=\"" + id + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\"" + body;
    }

    private static String userTask(String id, String name, String role) {
        return "<userTask id=\"" + id + "\" name=\"" + name + "\" flowable:candidateGroups=\"" + role + "\"/>";
    }

    // ①假档位

    @Test
    @DisplayName("出口阈值倒挂：高档分支永远不会被选中 ⇒ error 假档位")
    void 倒挂阈值的高档分支判为假档位() {
        String xml = HEAD + "<process id=\"DEAD_TIER\">"
                + "<startEvent id=\"Start\"/>"
                + userTask("TaskPre", "资产管理员审核", "FA_ASSET_ADMIN")
                + "<exclusiveGateway id=\"Gw\" name=\"金额授权\" default=\"flowLow\"/>"
                + userTask("TaskMid", "总经理审批（超10万）", "GM")
                + userTask("TaskHigh", "总裁审批（超50万）", "PRESIDENT")
                + "<endEvent id=\"End\"/>"
                + flow("f0", "Start", "TaskPre", null)
                + flow("f1", "TaskPre", "Gw", null)
                + flow("flowMid", "Gw", "TaskMid", "amount > 100000")
                + flow("flowHigh", "Gw", "TaskHigh", "amount > 500000")
                + flow("flowLow", "Gw", "End", null)
                + flow("f2", "TaskMid", "End", null)
                + flow("f3", "TaskHigh", "End", null)
                + "</process></definitions>";
        WfBpmnChecker.Report r = checker(List.of("FA_ASSET_ADMIN", "GM", "PRESIDENT"), List.of(), List.of()).check(xml);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("假档位") && e.contains("flowHigh")),
                "应报假档位，实际 errors=" + r.errors);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("总裁审批")), "假档位应点名收不到单的节点，实际=" + r.errors);
        assertTrue(r.amountTiered, "引用 amount 的网关应标记为分档流程");
        assertEquals(List.of("100000", "500000"), r.amountThresholds, "阈值应按表达式原文登记");
    }

    @Test
    @DisplayName("阈值从小到大排布：两档都可达 ⇒ 不报假档位")
    void 正序阈值不误报() {
        String xml = HEAD + "<process id=\"OK_TIER\">"
                + "<startEvent id=\"Start\"/>"
                + userTask("TaskPre", "资产管理员审核", "FA_ASSET_ADMIN")
                + "<exclusiveGateway id=\"Gw\" name=\"金额授权\" default=\"flowLow\"/>"
                + userTask("TaskHigh", "总裁审批（超50万）", "PRESIDENT")
                + userTask("TaskMid", "总经理审批（超10万）", "GM")
                + "<endEvent id=\"End\"/>"
                + flow("f0", "Start", "TaskPre", null)
                + flow("f1", "TaskPre", "Gw", null)
                + flow("flowHigh", "Gw", "TaskHigh", "amount > 500000")
                + flow("flowMid", "Gw", "TaskMid", "amount > 100000")
                + flow("flowLow", "Gw", "End", null)
                + flow("f2", "TaskHigh", "End", null)
                + flow("f3", "TaskMid", "End", null)
                + "</process></definitions>";
        WfBpmnChecker.Report r = checker(List.of("FA_ASSET_ADMIN", "GM", "PRESIDENT"), List.of(), List.of()).check(xml);
        assertTrue(r.errors.isEmpty(), "正序档位不该报 error，实际=" + r.errors);
    }

    // ②零审批短路

    @Test
    @DisplayName("网关前无人工审批且低档直达结束 ⇒ error 零审批通过")
    void 零审批短路判为error() {
        String xml = HEAD + "<process id=\"ZERO_APPROVAL\">"
                + "<startEvent id=\"Start\"/>"
                + "<exclusiveGateway id=\"Gw\" name=\"金额授权\" default=\"flowLow\"/>"
                + userTask("TaskHigh", "总裁审批（超50万）", "PRESIDENT")
                + "<endEvent id=\"End\"/>"
                + flow("f0", "Start", "Gw", null)
                + flow("flowHigh", "Gw", "TaskHigh", "amount > 500000")
                + flow("flowLow", "Gw", "End", null)
                + flow("f1", "TaskHigh", "End", null)
                + "</process></definitions>";
        WfBpmnChecker.Report r = checker(List.of("PRESIDENT"), List.of(), List.of()).check(xml);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("零审批")),
                "不传 amount 时整单无人审批，应判 error，实际=" + r.errors);
        assertFalse(r.valid());
    }

    // ③升级链低档直达 End = 只提示

    @Test
    @DisplayName("金额升级链（网关前已有审批）⇒ 只 warning，不挡发布")
    void 升级链低档直达结束只告警() {
        String xml = HEAD + "<process id=\"LADDER\">"
                + "<startEvent id=\"Start\"/>"
                + userTask("TaskPre", "财务经理复核", "FIN_MANAGER")
                + "<exclusiveGateway id=\"Gw\" name=\"金额授权\" default=\"flowLow\"/>"
                + userTask("TaskHigh", "总裁审批（超50万）", "PRESIDENT")
                + "<endEvent id=\"End\"/>"
                + flow("f0", "Start", "TaskPre", null)
                + flow("f1", "TaskPre", "Gw", null)
                + flow("flowHigh", "Gw", "TaskHigh", "amount > 500000")
                + flow("flowLow", "Gw", "End", null)
                + flow("f2", "TaskHigh", "End", null)
                + "</process></definitions>";
        WfBpmnChecker.Report r = checker(List.of("FIN_MANAGER", "PRESIDENT"), List.of(), List.of()).check(xml);
        assertTrue(r.errors.isEmpty(), "升级链形态不该拦发布，实际=" + r.errors);
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("低档出口") && w.contains("直达结束")),
                "应给出低档跳档提示，实际=" + r.warnings);
    }

    // 规则①：金额来源核对

    @Test
    @DisplayName("分档流程绑定的 bizType 在注册表无金额列 ⇒ warning 点名，但不拦发布")
    void 注册表看不出金额来源时告警不拦截() {
        String xml = ladder("MEETING");
        WfBpmnChecker.Report r = checker(List.of("FIN_MANAGER", "PRESIDENT"),
                List.of(binding("MEETING", "MEETING")), List.of()).check(xml);
        assertTrue(r.errors.isEmpty(), "MEETING 是批次2 已发布在用流程，规则①不得拦发布");
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("MEETING") && w.contains("BillRouteRegistry")),
                "应点名待核对的 bizType，实际=" + r.warnings);
    }

    @Test
    @DisplayName("同一形态但 bizType 有金额列（CLAIM）⇒ 不产生规则①告警")
    void 有金额来源的分档流程不告警() {
        String xml = ladder("CLAIM");
        WfBpmnChecker.Report r = checker(List.of("FIN_MANAGER", "PRESIDENT"),
                List.of(binding("CLAIM", "CLAIM")), List.of()).check(xml);
        assertTrue(r.warnings.stream().noneMatch(w -> w.contains("BillRouteRegistry")),
                "CLAIM 注册表金额列＝exp_bill.total_amount，不该报，实际=" + r.warnings);
        assertEquals(List.of("CLAIM"), r.bizTypes);
    }

    /** 升级链模板：提交审批 → 金额网关 →（超50万）总裁 → 结束；只有 process id 随用例变 */
    private static String ladder(String processId) {
        return HEAD + "<process id=\"" + processId + "\">"
                + "<startEvent id=\"Start\"/>"
                + userTask("TaskPre", "财务经理复核", "FIN_MANAGER")
                + "<exclusiveGateway id=\"Gw\" name=\"金额授权\" default=\"flowLow\"/>"
                + userTask("TaskHigh", "总裁审批（超50万）", "PRESIDENT")
                + "<endEvent id=\"End\"/>"
                + flow("f0", "Start", "TaskPre", null)
                + flow("f1", "TaskPre", "Gw", null)
                + flow("flowHigh", "Gw", "TaskHigh", "amount > 500000")
                + flow("flowLow", "Gw", "End", null)
                + flow("f2", "TaskHigh", "End", null)
                + "</process></definitions>";
    }
}
