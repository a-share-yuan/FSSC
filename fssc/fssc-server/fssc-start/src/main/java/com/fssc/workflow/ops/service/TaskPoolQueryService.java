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
import com.fssc.workflow.service.WfNodeConfigResolver;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * 本系统"流程消息"已由 {@code wf_cc} 独立承载、"已办"已由 {@code wf_task_history} 承载，
 * 而共享作业最缺的是"池"这一层，因此按 G3 要求落成<b>作业语义</b>的五视图：
 * <ol>
 *   <li>{@code MY_TODO} 我的待办：PENDING 且（受理人=我 或 我是候选人）——含池内未锁定的；</li>
 *   <li>{@code CLAIMABLE} 可认领：PENDING 且未指派 且 我是候选人 且 节点是任务池（POOL）——真正能抢的；</li>
 *   <li>{@code LOCKED_BY_OTHERS} 被他人锁定：PENDING 且已指派给他人 且 我是候选人（POOL 软锁可见性，
 *       与 {@code WfQueryService.poolTasksClaimedByOthers} 同口径：行看得见、办不了、看得到锁定者）；</li>
 *   <li>{@code MY_CLAIMED} 我已认领：PENDING 且受理人=我 且 节点是 POOL；</li>
 *   <li>{@code POOL_ALL} 池内全部：PENDING 且节点是 POOL（不限候选人，供共享中心主管总览）。</li>
 * </ol>
 *
 * <p><b>超时判定读 {@code wf_task.due_time} 真值</b>（G3 双时限落地），不再用 create_time 现算；
 * 尚无 due_time 的历史存量行按"未判超时"处理并单独标注 {@code dueTimeMissing=true}，
 * 避免把"没落时限"静默当成"没超时"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPoolQueryService {

    private final WfTaskMapper taskMapper;
    private final SysUserMapper sysUserMapper;
    private final WfNodeConfigResolver nodeConfigResolver;
    private final TaskTimeLimitService timeLimitService;

    /** 五视图名（对外常量，前端下拉直接用） */
    public static final List<String> VIEWS =
            List.of("MY_TODO", "CLAIMABLE", "LOCKED_BY_OTHERS", "MY_CLAIMED", "POOL_ALL");

    /** 单次查询行数上限（防止一次请求把整租户待办读进内存） */
    private static final long MAX_ROWS = 500;

    /**
     * 五视图分页查询。
     *
     * @param view    视图名，见 {@link #VIEWS}
     * @param userId  当前用户（POOL_ALL 之外必填）
     * @param keyword 单号/标题模糊
     */
    public PageResult<Map<String, Object>> view(String view, PageQuery q, Long userId, String keyword) {
        String v = view == null ? "" : view.trim().toUpperCase();
        if (!VIEWS.contains(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知任务池视图：" + view + "，可选 " + VIEWS);
        }
        if (!"POOL_ALL".equals(v) && userId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "视图 " + v + " 需要当前登录用户");
        }
        long pageSize = Math.min(Math.max(q.getPageSize(), 1), MAX_ROWS);

        LambdaQueryWrapper<WfTask> w = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING");
        switch (v) {
            case "MY_TODO" -> w.and(x -> x.eq(WfTask::getAssigneeId, userId)
                    .or().like(WfTask::getCandidateIds, "," + userId + ","));
            case "CLAIMABLE" -> {
                w.isNull(WfTask::getAssigneeId).like(WfTask::getCandidateIds, "," + userId + ",");
                restrictToPool(w);
            }
            case "LOCKED_BY_OTHERS" -> {
                w.isNotNull(WfTask::getAssigneeId)
                        .ne(WfTask::getAssigneeId, userId)
                        .like(WfTask::getCandidateIds, "," + userId + ",");
                restrictToPool(w);
            }
            case "MY_CLAIMED" -> {
                w.eq(WfTask::getAssigneeId, userId);
                restrictToPool(w);
            }
            default -> restrictToPool(w);   // POOL_ALL
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(x -> x.like(WfTask::getBizNo, kw).or().like(WfTask::getTitle, kw));
        }
        // 排期紧迫的排前面：有 due_time 的按到期升序，无 due_time 的排最后（MySQL 无 NULLS LAST 语法）
        w.last("order by (due_time is null), due_time asc, id desc");

        Page<WfTask> p = taskMapper.selectPage(Page.of(q.getPageNum(), pageSize), w);
        return PageResult.of(enrich(p.getRecords()), p.getTotal(), q.getPageNum(), pageSize);
    }

    /**
     * 限定"节点是任务池（POOL）"。
     *
     * <p>为什么用嵌套 OR 而不是一次 IN：{@code wf_node_config} 的节点口径是
     * {@code model_key:node_key} 复合键，SQL 里没有这一列可 IN；按 model_key 分组后展开成
     * {@code (model_key=? AND node_key IN (…)) OR …}，与 {@code WfNodeConfigResolver.poolNodeKeys()}
     * 的判定完全同源（不另立一套 POOL 定义）。
     */
    private void restrictToPool(LambdaQueryWrapper<WfTask> w) {
        Set<String> poolKeys = nodeConfigResolver.poolNodeKeys();
        Map<String, Set<String>> byModel = new LinkedHashMap<>();
        for (String key : poolKeys) {
            int idx = key.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            byModel.computeIfAbsent(key.substring(0, idx), k -> new LinkedHashSet<>()).add(key.substring(idx + 1));
        }
        if (byModel.isEmpty()) {
            // 无任何 POOL 节点：池视图必然为空。用恒假条件表达，避免退化成"整表待办"
            w.eq(WfTask::getNodeKey, "__NO_POOL_NODE__");
            return;
        }
        w.and(outer -> {
            boolean first = true;
            for (Map.Entry<String, Set<String>> e : byModel.entrySet()) {
                if (first) {
                    outer.and(x -> x.eq(WfTask::getModelKey, e.getKey()).in(WfTask::getNodeKey, e.getValue()));
                    first = false;
                } else {
                    outer.or(x -> x.eq(WfTask::getModelKey, e.getKey()).in(WfTask::getNodeKey, e.getValue()));
                }
            }
        });
    }

    /** 行补充：受理人姓名、优先级、按 due_time 真值判定的超时标记与剩余小时 */
    private List<Map<String, Object>> enrich(List<WfTask> tasks) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            return out;
        }
        List<String> taskIds = new ArrayList<>();
        Set<Long> userIds = new LinkedHashSet<>();
        for (WfTask t : tasks) {
            if (t.getTaskId() != null) {
                taskIds.add(t.getTaskId());
            }
            if (t.getAssigneeId() != null) {
                userIds.add(t.getAssigneeId());
            }
            userIds.addAll(WfQueryService.parseCandidateIds(t.getCandidateIds()));
        }
        Map<String, String> priorities = timeLimitService.priorityIndex(taskIds);
        Map<Long, String> names = userNameIndex(userIds);
        LocalDateTime now = LocalDateTime.now();

        for (WfTask t : tasks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", t.getTaskId());
            row.put("procInstId", t.getProcInstId());
            row.put("modelKey", t.getModelKey());
            row.put("nodeKey", t.getNodeKey());
            row.put("nodeName", t.getNodeName());
            row.put("bizType", t.getBizType());
            row.put("bizId", t.getBizId());
            row.put("bizNo", t.getBizNo());
            row.put("title", t.getTitle());
            row.put("amount", t.getAmount());
            row.put("assigneeId", t.getAssigneeId());
            row.put("assigneeName", t.getAssigneeId() == null ? null : names.get(t.getAssigneeId()));
            row.put("locked", t.getAssigneeId() != null);
            List<Long> candidates = WfQueryService.parseCandidateIds(t.getCandidateIds());
            row.put("candidateIds", candidates);
            row.put("candidateNames",
                    candidates.stream().map(c -> names.getOrDefault(c, String.valueOf(c))).toList());
            row.put("createTime", t.getCreateTime());
            row.put("dueTime", t.getDueTime());
            row.put("priority", priorities.getOrDefault(t.getTaskId(), "NORMAL"));
            row.put("urgedCount", t.getUrgedCount());
            // 超时判定：唯一真值是 due_time；缺失则明确标注，不当作"未超时"
            boolean dueTimeMissing = t.getDueTime() == null;
            row.put("dueTimeMissing", dueTimeMissing);
            row.put("overdue", !dueTimeMissing && t.getDueTime().isBefore(now));
            row.put("remainingHours", dueTimeMissing ? null : Duration.between(now, t.getDueTime()).toHours());
            out.add(row);
        }
        return out;
    }

    private Map<Long, String> userNameIndex(Set<Long> userIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (SysUser u : sysUserMapper.selectBatchIds(userIds)) {
            out.put(u.getId(), u.getNickName());
        }
        return out;
    }
}
