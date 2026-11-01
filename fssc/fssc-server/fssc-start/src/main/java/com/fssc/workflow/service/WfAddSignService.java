// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.workflow.entity.WfAddSign;
import com.fssc.workflow.mapper.WfAddSignMapper;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 流程加签的<b>持久化层</b>（wf_add_sign）。
 *
 * <p>职责边界：本类只做库操作与纯函数式的链式推进计算，<b>不碰 Flowable</b>
 * （改派/回跳由 {@code WfEngineFacade} 编排）。这样既避免与引擎形成循环依赖，
 * 也让"回跳目标"这类规则可以离线单测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfAddSignService {

    private final WfAddSignMapper addSignMapper;

    /** 新增一条加签记录（调用前请由引擎完成权限校验）。 */
    public WfAddSign create(WfAddSign rec) {
        if (rec.getSeqIndex() == null) {
            rec.setSeqIndex(0);
        }
        if (rec.getStatus() == null) {
            rec.setStatus(WfAddSign.STATUS_PENDING);
        }
        addSignMapper.insert(rec);
        return rec;
    }

    public WfAddSign byId(Long id) {
        return id == null ? null : addSignMapper.selectById(id);
    }

    /** 该任务上"进行中"的加签记录（不含 PENDING 后加签）。一条任务同时只允许一条，故取最近一条。 */
    public WfAddSign activeByTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        List<WfAddSign> rows = addSignMapper.selectList(new LambdaQueryWrapper<WfAddSign>()
                .eq(WfAddSign::getTaskId, taskId)
                .eq(WfAddSign::getStatus, WfAddSign.STATUS_ACTIVE)
                .orderByDesc(WfAddSign::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 一批任务上"正在进行"的加签记录（待办列表加签标识）。
     *
     * <p>为什么要批量版：待办每页最多 200 行，逐行调 {@link #activeByTask} 就是 200 次查询。
     * 这里一次 {@code task_id IN (...)} 取回，由调用方按 taskId 归堆。
     *
     * <p>只取 {@link WfAddSign#STATUS_ACTIVE}：前加签在发起时就把记录绑到被改派的任务上，
     * 后加签则在推进生效时绑到新任务上（{@code applyPendingAfterAddSign}）——两种加签人在
     * 待办里看到的那一行都命中本查询。审结/取消后记录转 DONE/CANCELLED ⇒ 标识自动消失，
     * 原办理人的待办回到"普通环节"规则，不会长期挂着加签角标。
     */
    public List<WfAddSign> activeByTaskIds(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return addSignMapper.selectList(new LambdaQueryWrapper<WfAddSign>()
                .in(WfAddSign::getTaskId, taskIds)
                .eq(WfAddSign::getStatus, WfAddSign.STATUS_ACTIVE)
                .orderByDesc(WfAddSign::getId));
    }

    /** 该任务上"进行中"且当前该由 {@code userId} 签的记录（complete 钩子用）。 */
    public WfAddSign activeByTaskAndUser(String taskId, Long userId) {
        WfAddSign rec = activeByTask(taskId);
        if (rec == null || userId == null) {
            return null;
        }
        Long cur = currentSigner(rec);
        return userId.equals(cur) ? rec : null;
    }

    /** 某流程实例上待生效的后加签（等 {@code srcTaskId} 办结推进后落任务）。 */
    public List<WfAddSign> pendingAfterByProc(String procInstId, String srcTaskId) {
        return addSignMapper.selectList(new LambdaQueryWrapper<WfAddSign>()
                .eq(WfAddSign::getProcInstId, procInstId)
                .eq(WfAddSign::getSrcTaskId, srcTaskId)
                .eq(WfAddSign::getPosition, WfAddSign.POSITION_AFTER)
                .eq(WfAddSign::getStatus, WfAddSign.STATUS_PENDING)
                .orderByAsc(WfAddSign::getId));
    }

    /** 某流程实例上所有进行中/待生效的加签（前端展示"当前在加签"用）。 */
    public List<WfAddSign> openByProc(String procInstId) {
        return addSignMapper.selectList(new LambdaQueryWrapper<WfAddSign>()
                .eq(WfAddSign::getProcInstId, procInstId)
                .in(WfAddSign::getStatus, Arrays.asList(WfAddSign.STATUS_PENDING, WfAddSign.STATUS_ACTIVE))
                .orderByAsc(WfAddSign::getId));
    }

    /** 加签人链（按录入顺序）。 */
    public List<Long> signerIds(WfAddSign rec) {
        List<Long> out = new ArrayList<>();
        if (rec == null || rec.getSignUsers() == null) {
            return out;
        }
        for (String s : rec.getSignUsers().split(",")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException ex) {
                log.warn("加签记录 {} 含非数字用户ID，已跳过: {}", rec.getId(), t);
            }
        }
        return out;
    }

    /** 当前该签的加签人（越界返回 null）。 */
    public Long currentSigner(WfAddSign rec) {
        List<Long> all = signerIds(rec);
        int idx = rec == null || rec.getSeqIndex() == null ? 0 : rec.getSeqIndex();
        return idx >= 0 && idx < all.size() ? all.get(idx) : null;
    }

    /**
     * 链式推进到下一位加签人。
     *
     * @return 还有下一位则返回该加签人 ID，链已走完返回 {@code null}
     */
    public Long advanceToNext(WfAddSign rec) {
        List<Long> all = signerIds(rec);
        int next = (rec.getSeqIndex() == null ? 0 : rec.getSeqIndex()) + 1;
        if (next >= all.size()) {
            return null;
        }
        addSignMapper.update(null, new LambdaUpdateWrapper<WfAddSign>()
                .eq(WfAddSign::getId, rec.getId())
                .set(WfAddSign::getSeqIndex, next)
                .set(WfAddSign::getUpdateTime, LocalDateTime.now()));
        rec.setSeqIndex(next);
        return all.get(next);
    }

    /** 落最终状态（DONE / CANCELLED / FAILED）。 */
    public void markStatus(Long id, String status) {
        if (id == null) {
            return;
        }
        addSignMapper.update(null, new LambdaUpdateWrapper<WfAddSign>()
                .eq(WfAddSign::getId, id)
                .set(WfAddSign::getStatus, status)
                .set(WfAddSign::getDoneTime, LocalDateTime.now())
                .set(WfAddSign::getUpdateTime, LocalDateTime.now()));
    }

    /** 后加签生效时回填实际被加签的任务ID。 */
    public void bindTask(Long id, String taskId, String exitAssignee) {
        addSignMapper.update(null, new LambdaUpdateWrapper<WfAddSign>()
                .eq(WfAddSign::getId, id)
                .set(WfAddSign::getTaskId, taskId)
                .set(WfAddSign::getExitAssignee, exitAssignee)
                .set(WfAddSign::getStatus, WfAddSign.STATUS_ACTIVE)
                .set(WfAddSign::getUpdateTime, LocalDateTime.now()));
    }

    /** 该流程实例上是否已有进行中的加签（防止同一任务重复加签）。 */
    public boolean hasOpenOnTask(String taskId) {
        if (taskId == null) {
            return false;
        }
        return addSignMapper.selectCount(new LambdaQueryWrapper<WfAddSign>()
                .eq(WfAddSign::getTaskId, taskId)
                .in(WfAddSign::getStatus, Arrays.asList(WfAddSign.STATUS_PENDING, WfAddSign.STATUS_ACTIVE))) > 0;
    }
}
