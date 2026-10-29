// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.mapper.WfFlowConfigMapper;
import com.fssc.workflow.service.WfNodeConfigResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批超时管理（/api/tenant/wf/timeout/**）。
 * 提供超时统计、超时任务列表、节点超时配置查询。
 */
@RestController
@RequestMapping("/api/tenant/wf/timeout")
@RequiredArgsConstructor
@Slf4j
public class WfTimeoutController {

    private final WfTaskMapper wfTaskMapper;
    private final WfNodeConfigMapper wfNodeConfigMapper;
    private final WfFlowConfigMapper wfFlowConfigMapper;
    /** 节点键解析（含 Task_N → 业务节点默认处理映射），与超时催办 Job 共用同一实现 */
    private final WfNodeConfigResolver wfNodeConfigResolver;

    /**
     * 超时统计：返回总待办数、已超时数、未超时数、平均处理时长。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = buildOverdueStats();
        return Result.success(stats);
    }

    /**
     * 超时任务列表（分页）：返回超时任务详情，含 taskId / title / nodeName / assigneeId / createTime / overdueHours / timeoutAction。
     */
    @GetMapping("/overdue-list")
    public Result<PageResult<Map<String, Object>>> overdueList(PageQuery q) {
        PageResult<Map<String, Object>> result = queryOverdueList(q);
        return Result.success(result);
    }

    /**
     * 查询某流程各节点的超时配置。
     */
    @GetMapping("/config/{modelKey}")
    public Result<List<Map<String, Object>>> config(@PathVariable String modelKey) {
        List<WfNodeConfig> configs = wfNodeConfigMapper.selectList(
                new LambdaQueryWrapper<WfNodeConfig>()
                        .eq(WfNodeConfig::getModelKey, modelKey));
        List<Map<String, Object>> list = configs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("nodeKey", c.getNodeKey());
            m.put("nodeName", c.getNodeName());
            m.put("timeoutHours", c.getTimeoutHours());
            m.put("timeoutAction", c.getTimeoutAction());
            return m;
        }).collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 构建超时统计数据。
     */
    private Map<String, Object> buildOverdueStats() {
        List<WfTask> pendingList = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getStatus, "PENDING"));

        // 构建节点配置索引（modelKey:nodeKey -> WfNodeConfig，含 Task_N 默认处理）
        Map<String, WfNodeConfig> nodeConfigMap = wfNodeConfigResolver.index(pendingList);

        long totalPending = pendingList.size();
        long overdueCount = 0;
        double totalHours = 0;

        for (WfTask t : pendingList) {
            if (t.getCreateTime() != null) {
                long elapsedHours = Duration.between(t.getCreateTime(), LocalDateTime.now()).toHours();
                totalHours += elapsedHours;

                // 判断是否超时
                String key = t.getModelKey() + ":" + t.getNodeKey();
                WfNodeConfig nc = nodeConfigMap.get(key);
                if (nc != null && nc.getTimeoutHours() != null && nc.getTimeoutHours() > 0
                        && elapsedHours >= nc.getTimeoutHours()) {
                    overdueCount++;
                }
            }
        }

        long withinSlaCount = totalPending - overdueCount;
        double avgProcessingHours = totalPending > 0 ? totalHours / (double) totalPending : 0;
        // 保留两位小数
        avgProcessingHours = Math.round(avgProcessingHours * 100.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalPending", totalPending);
        result.put("overdueCount", overdueCount);
        result.put("withinSlaCount", withinSlaCount);
        result.put("avgProcessingHours", avgProcessingHours);
        return result;
    }

    /**
     * 查询超时任务列表（分页）。
     */
    private PageResult<Map<String, Object>> queryOverdueList(PageQuery q) {
        Page<WfTask> page = wfTaskMapper.selectPage(
                Page.of(q.getPageNum(), q.getPageSize()),
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getStatus, "PENDING")
                        .orderByAsc(WfTask::getCreateTime));

        List<WfTask> records = page.getRecords();
        // 构建节点配置索引
        Map<String, WfNodeConfig> nodeConfigMap = wfNodeConfigResolver.index(records);

        List<Map<String, Object>> list = new ArrayList<>();
        for (WfTask t : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getTaskId());
            m.put("title", t.getTitle());
            m.put("nodeName", t.getNodeName());
            m.put("assigneeId", t.getAssigneeId());
            m.put("createTime", t.getCreateTime());

            long overdueHours = 0;
            String timeoutAction = null;
            if (t.getCreateTime() != null) {
                String key = t.getModelKey() + ":" + t.getNodeKey();
                WfNodeConfig nc = nodeConfigMap.get(key);
                if (nc != null && nc.getTimeoutHours() != null && nc.getTimeoutHours() > 0) {
                    long elapsed = Duration.between(t.getCreateTime(), LocalDateTime.now()).toHours();
                    if (elapsed >= nc.getTimeoutHours()) {
                        overdueHours = elapsed - nc.getTimeoutHours();
                        timeoutAction = nc.getTimeoutAction();
                    }
                }
            }
            m.put("overdueHours", overdueHours);
            m.put("timeoutAction", timeoutAction);
            list.add(m);
        }

        return PageResult.of(list, page.getTotal(), q.getPageNum(), q.getPageSize());
    }
}