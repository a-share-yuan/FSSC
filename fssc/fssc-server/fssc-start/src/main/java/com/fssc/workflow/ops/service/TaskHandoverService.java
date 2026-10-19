// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.ops.dto.HandoverRequest;
import com.fssc.workflow.ops.entity.OpTransferLog;
import com.fssc.workflow.ops.mapper.OpTransferLogMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * <p><b>为什么走引擎既有改派/转办通道，而不是直接 {@code setAssignee}</b>：直接改受理人会漏掉三件事——
 * {@code wf_task} 镜像同步、{@code wf_task_history} 留痕、接收人通知，正是方案 §二 F1/F13 描述的
 * "双真值 + 时间线断节"来源。故本类只做两件事：<b>挑单</b>（按原办理人反查在办任务）与
 * <b>逐单调用引擎</b>，其余交给 {@link WfEngineFacade}。
 *
 * <p><b>两条通道的分工</b>：
 * <ul>
 *   <li>操作人<b>就是</b>原办理人 → 走 {@code transfer}（转办通道）：{@code canOperate} 成立，
 *       留 TRANSFER 痕，且尊重节点「允许转办」开关；</li>
 *   <li>操作人是流程管理员（代为移交）→ 走 {@code reassign}（改派通道）：转办通道的
 *       {@code canOperate} 必然拒绝管理员，改派是"离职/换岗/无人可办"的正式出口
 *       （对应用友 NC 流程移交、SAP Execute Work Items Without Agent Check）。</li>
 * </ul>
 *
 * <p><b>不加 {@code @Transactional} 是刻意的</b>：批量移交必须"逐单独立成败"。
 * 若整批共用一个事务，一条失败就会把已成功的移交一起回滚，而日志已按逐单结果写下——
 * 那才是真正的账实不符。因此本类逐单 try/catch，失败明细照实落 {@code op_transfer_log.failed_detail}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskHandoverService {

    private final WfTaskMapper taskMapper;
    private final OpTransferLogMapper transferLogMapper;
    private final WfEngineFacade wfEngineFacade;
    private final SysUserMapper sysUserMapper;

    /** 单次批量移交的任务数上限：一次请求不设限就等于让管理员把整个共享中心搬一次 */
    private static final int MAX_BATCH = 200;

    /**
     * 批量移交。
     *
     * @param operUserId 操作人（流程管理员或原办理人本人），由控制器从登录态取
     * @return 移交结果：请求条数 / 成功 / 失败清单 / 日志 ID
     */
    public Map<String, Object> handover(HandoverRequest req, Long operUserId) {
        if (req == null || req.toUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人（toUserId）必填");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "移交原因必填（如：原办理人离职/换岗/工作交接）");
        }
        if (operUserId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法执行流程移交");
        }
        if (req.toUserId().equals(req.fromUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "移交接收人不能与原办理人相同");
        }

        List<String> taskIds = resolveTaskIds(req);
        if (taskIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "没有可移交的在办任务：请确认原办理人是否仍有 PENDING 待办，或直接指定 taskIds");
        }

        List<String> succeeded = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (String taskId : taskIds) {
            try {
                handoverOne(taskId, req.toUserId(), req.reason(), operUserId);
                succeeded.add(taskId);
            } catch (Exception ex) {
                // 逐单失败不影响其余单子；原因照实带回，前端可直接展示"哪几条没移交成功、为什么"
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("taskId", taskId);
                f.put("reason", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                failed.add(f);
                log.warn("G3 流程移交失败 taskId={} to={}: {}", taskId, req.toUserId(), ex.getMessage());
            }
        }

        Map<String, String> names = userNames(req.fromUserId(), req.toUserId());
        Long logId = writeLog(req, operUserId, taskIds.size(), succeeded.size(), failed, names);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromUserId", req.fromUserId());
        out.put("fromUserName", names.get(req.fromUserId()));
        out.put("toUserId", req.toUserId());
        out.put("toUserName", names.get(req.toUserId()));
        out.put("requested", taskIds.size());
        out.put("successCount", succeeded.size());
        out.put("failCount", failed.size());
        out.put("succeededTaskIds", succeeded);
        out.put("failed", failed);
        out.put("transferLogId", logId);
        out.put("note", failed.isEmpty() ? "全部移交成功，每条任务已在引擎侧留 TRANSFER/REASSIGN 痕并通知接收人"
                : "部分任务未移交成功，原因见 failed；失败单子仍在原办理人名下，请逐条处理后重试");
        return out;
    }

    /**
     * 挑单：优先用请求指定的 taskIds（校验确实在办），否则按原办理人反查其名下 PENDING 待办。
     * 反查结果按 id 升序（先老后新，与"先交接最久的单"的直觉一致）。
     */
    private List<String> resolveTaskIds(HandoverRequest req) {
        if (req.taskIds() != null && !req.taskIds().isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (String t : req.taskIds()) {
                if (t != null && !t.isBlank() && !ids.contains(t.trim())) {
                    ids.add(t.trim());
                }
            }
            if (ids.size() > MAX_BATCH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单次移交上限 " + MAX_BATCH + " 条，请分批执行");
            }
            return ids;
        }
        if (req.fromUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请提供 taskIds 或 fromUserId 之一");
        }
        List<WfTask> rows = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getAssigneeId, req.fromUserId())
                .eq(WfTask::getStatus, "PENDING")
                .orderByAsc(WfTask::getId)
                .last("limit " + MAX_BATCH));
        List<String> ids = new ArrayList<>();
        for (WfTask t : rows) {
            if (t.getTaskId() != null && !t.getTaskId().isBlank()) {
                ids.add(t.getTaskId());
            }
        }
        return ids;
    }

    /**
     * 单条移交：按"操作人是否即原办理人"选择引擎通道（见类注释的分工说明）。
     * 通道选择依据取自 {@code wf_task} 镜像的 assignee_id（与引擎 assignee 同步，见
     * {@code WfEngineFacade.syncTaskViewAfterAssigneeChange}）。
     */
    private void handoverOne(String taskId, Long toUserId, String reason, Long operUserId) {
        WfTask mirror = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING")
                .last("limit 1"));
        if (mirror == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务不在待办中（已办结/已取消或 taskId 有误）");
        }
        if (operUserId.equals(mirror.getAssigneeId())) {
            // 本人移交：转办通道（尊重节点「允许转办」开关 + canOperate 守卫，留 TRANSFER 痕）
            wfEngineFacade.transfer(taskId, toUserId, reason, operUserId);
        } else {
            // 管理员代为移交：改派通道（无 canOperate 要求，留 REASSIGN 痕 + 通知）
            wfEngineFacade.reassign(taskId, toUserId, reason, operUserId);
        }
    }

    /** 落一条批量移交日志（写失败必须可见，不得静默——方案 §二 F2 的教训） */
    private Long writeLog(HandoverRequest req, Long operUserId, int requested, int success,
                          List<Map<String, Object>> failed, Map<String, String> names) {
        OpTransferLog row = new OpTransferLog();
        row.setFromUserId(req.fromUserId());
        row.setFromUserName(names.get(req.fromUserId()));
        row.setToUserId(req.toUserId());
        row.setToUserName(names.get(req.toUserId()));
        row.setTaskIds(joinTaskIds(req, requested));
        row.setTaskCount(requested);
        row.setSuccessCount(success);
        row.setFailCount(failed.size());
        row.setFailedDetail(failedDetail(failed));
        row.setReason(req.reason());
        row.setOperUserId(operUserId);
        row.setOperUserName(names.get(operUserId));
        row.setOperTime(LocalDateTime.now());
        row.setRemark(req.remark());
        try {
            transferLogMapper.insert(row);
            return row.getId();
        } catch (Exception ex) {
            log.error("G3 移交日志写入失败（移交结果已生效，请人工核对）from={} to={} 条数={}",
                    req.fromUserId(), req.toUserId(), requested, ex);
            return null;
        }
    }

    /** 日志里的任务清单：指定 taskIds 时原样记录；按人反查时记录该人名下当时的在办集合（截断防超长） */
    private String joinTaskIds(HandoverRequest req, int requested) {
        List<String> ids = req.taskIds() == null ? List.of() : req.taskIds();
        if (ids.isEmpty()) {
            return "按原办理人反查在办任务 " + requested + " 条";
        }
        String joined = String.join(",", ids);
        return joined.length() > 2000 ? joined.substring(0, 2000) + "…" : joined;
    }

    private String failedDetail(List<Map<String, Object>> failed) {
        if (failed.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> f : failed) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(f.get("taskId")).append(':').append(f.get("reason"));
        }
        return sb.length() > 2000 ? sb.substring(0, 2000) + "…" : sb.toString();
    }

    /** 移交日志分页（按操作时间倒序，可按原办理人/接收人过滤） */
    public PageResult<OpTransferLog> logPage(PageQuery q, Long fromUserId, Long toUserId) {
        LambdaQueryWrapper<OpTransferLog> w = new LambdaQueryWrapper<>();
        if (fromUserId != null) {
            w.eq(OpTransferLog::getFromUserId, fromUserId);
        }
        if (toUserId != null) {
            w.eq(OpTransferLog::getToUserId, toUserId);
        }
        w.orderByDesc(OpTransferLog::getId);
        Page<OpTransferLog> p = transferLogMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    private Map<String, String> userNames(Long... userIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
            out.put(String.valueOf(u.getId()), u.getNickName());
        }
        return out;
    }
}
