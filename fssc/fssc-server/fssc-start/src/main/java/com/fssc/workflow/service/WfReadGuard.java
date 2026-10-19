// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 流程侧只读预览端点的归属闸（D-DEC-229）。
 *
 * <p><b>补的是哪一段</b>：写路径有 {@code canOperate} 越权守卫（V1.3.19，fail-closed），
 * 但同一批「只读预览」端点按 {@code taskId}/{@code procInstId} 取数时<b>不做任何归属校验</b>。
 * 2026-09-26 实测（同一在办 TR_PAY，批次 PB202609260002）：
 * <ul>
 *   <li>公司 31 的普通账号读公司 1 的在办单 ⇒ {@code reject-targets} 1690 B、{@code approval-items} 46 B、
 *       {@code withdraw-preview} 1198 B、{@code query/chain} 1699 B（含各节点审批意见原文），
 *   <li>同一时刻同一账号调单据联查 {@code bill-trace-chain} 被 {@code OrgGuard} 正确拒 403
 *       ⇒ 缺口只在流程侧，TRC-B 那次收口没覆盖到这批后来加的预览端点。</li>
 * </ul>
 *
 * <p><b>口径选择（刻意与 D-TRC-04 已裁决规则对齐，不另立标准）</b>：先认「与本单的关系」，
 * 关系不成立再按单据公司兜底——
 * <ol>
 *   <li><b>可办理</b>：{@link NodeOrgResolver#canOperate}（办理人 / 候选组角色∩公司维度 / 委托受托人 /
 *       GROUP 范围角色），与执行面同源，保证审批弹窗、跨公司受托人不会被这道闸挡在门外；</li>
 *   <li><b>发起人</b>：撤销预览本就是发起人视角；</li>
 *   <li><b>参与人</b>：任一轮次的历史操作人 / 被转办·加签点名对象，或在办任务挂我名下（含候选含我）
 *       ——「曾经办过这张单，回看它走到哪了」是真实需求；</li>
 *   <li><b>集团岗</b>：无公司维度账号或 data_scope=1，流程监控/运营岗据此通行；</li>
 *   <li>以上都不成立 ⇒ 走 {@link OrgGuard#assertVisible}（本公司可见、他公司拒绝），
 *       与 {@code bill-trace-chain}、{@code history/page} 同一把尺。公司维度解析不出来的域
 *       （{@code companyIdOfBill} 返回 null，见 D-B3-06）在 OrgGuard 里是 fail-open，
 *       故此处补一条「无公司维度 ⇒ 仅关系人可见」，与 {@code assertGenericVisible} 同口径。</li>
 * </ol>
 * 也就是说：本闸把<b>跨公司</b>读取封死，<b>同公司非参与人</b>维持现状（那是 D-TRC-04 已裁决的
 * 「本公司单据可见」，不在修复批次里单方面收紧；若要收到「仅参与人可见」，见交付文档 RA-D229a）。
 *
 * <p><b>fail-closed</b>：解析不到流程实例（无 {@code wf_instance} 镜像行）时一律拒绝，
 * 与 {@code canOperate} 的铁律同口径——判不出归属就宁拒不放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfReadGuard {

    private final TaskService taskService;
    private final HistoryService historyService;
    private final NodeOrgResolver nodeOrgResolver;
    private final OrgGuard orgGuard;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfTaskHistoryMapper wfTaskHistoryMapper;

    /** 任务级只读预览（审批项 / 会签进度 / 驳回候选 / 撤销影响面）的归属闸 */
    public void assertTaskReadable(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        Long me = UserContext.getRequired().getUserId();
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null && nodeOrgResolver.canOperate(task, me)) {
            return;
        }
        String procInstId = task != null ? task.getProcessInstanceId() : historicProcInstId(taskId);
        if (procInstId == null || procInstId.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该任务的信息：任务不存在或不属于当前租户");
        }
        assertInstanceReadable(procInstId);
    }

    /** 实例级只读预览（撤销影响面 / 全链节点与意见）的归属闸 */
    public void assertInstanceReadable(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程实例 ID 不能为空");
        }
        Long me = UserContext.getRequired().getUserId();
        List<WfInstance> instances = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>().eq(WfInstance::getProcInstId, procInstId));
        if (instances.isEmpty()) {
            // 判不出归属＝拒绝，而不是放开
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权查看该流程的信息：无法确认本人与该流程实例的归属关系");
        }
        Long starter = instances.stream().map(WfInstance::getStartUserId)
                .filter(Objects::nonNull).findFirst().orElse(null);

        if (isGroupLevel() || Objects.equals(me, starter) || participated(me, procInstId)) {
            return;
        }
        // 候选组/受托人通道：本实例在办任务里任一任务「我能办」即相关（与执行面同源）
        for (Task t : taskService.createTaskQuery().processInstanceId(procInstId).list()) {
            if (nodeOrgResolver.canOperate(t, me)) {
                return;
            }
        }
        Long company = null;
        for (WfInstance inst : instances) {
            company = nodeOrgResolver.companyIdOfBill(inst.getBizType(), inst.getBizId());
            if (company != null) {
                break;
            }
        }
        if (company == null) {
            // 该域没有公司维度可依据 ⇒ 不 fail-open，按「仅关系人」收口（同 bill-trace-chain 的 D-B3-06 处理）
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权查看该单据的流程信息：仅发起人、可办理人、流程参与人与集团岗可见");
        }
        // 与单据联查、审批历史分页同一把尺：本公司可见，他公司拒绝
        orgGuard.assertVisible(company, starter);
    }

    /** 本单流程参与人：任一节点的历史操作人 / 被转办·加签点名对象，或在办任务办理人 / 候选人 */
    private boolean participated(Long userId, String procInstId) {
        Long hits = wfTaskHistoryMapper.selectCount(new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getProcInstId, procInstId)
                .and(w -> w.eq(WfTaskHistory::getOperUserId, userId)
                        .or().eq(WfTaskHistory::getTargetUserId, userId)));
        if (hits != null && hits > 0) {
            return true;
        }
        Long open = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getProcInstId, procInstId)
                .and(w -> w.eq(WfTask::getAssigneeId, userId)
                        .or().apply("find_in_set({0}, candidate_ids)", userId)));
        return open != null && open > 0;
    }

    /** 集团岗：无公司维度（集团级账号）或全部数据权限 */
    private boolean isGroupLevel() {
        LoginUser me = UserContext.get();
        if (me == null) {
            return false;
        }
        return me.getCompanyId() == null || (me.getDataScope() != null && me.getDataScope() == 1);
    }

    private String historicProcInstId(String taskId) {
        try {
            HistoricTaskInstance ht = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
            return ht == null ? null : ht.getProcessInstanceId();
        } catch (Exception ex) {
            log.warn("历史任务反查实例失败 taskId={}: {}", taskId, ex.getMessage());
            return null;
        }
    }
}
