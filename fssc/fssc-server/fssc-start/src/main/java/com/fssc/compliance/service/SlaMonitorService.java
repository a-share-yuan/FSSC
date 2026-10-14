// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.compliance.entity.DispatchTask;
import com.fssc.compliance.entity.OpSlaBreach;
import com.fssc.compliance.entity.OpSlaConfig;
import com.fssc.compliance.mapper.DispatchTaskMapper;
import com.fssc.compliance.mapper.OpSlaBreachMapper;
import com.fssc.compliance.mapper.OpSlaConfigMapper;
import com.fssc.compliance.mapper.ReviewerMapper;
import com.fssc.compliance.entity.Reviewer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA 监控服务（P1-5 共享运营实质化）。
 * <p>
 * 职责：
 * 1. 按单据类型配置 SLA（目标时长、预警时长）
 * 2. 派单时设置 SLA 到期时间
 * 3. 定时扫描 ASSIGNED 任务，检测预警/超时
 * 4. 超时自动升级派工
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaMonitorService {

    private final OpSlaConfigMapper slaConfigMapper;
    private final OpSlaBreachMapper slaBreachMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final ReviewerMapper reviewerMapper;

    /**
     * 根据单据类型获取 SLA 配置。
     */
    public OpSlaConfig getConfigByBillType(String billType) {
        return slaConfigMapper.selectOne(
                new LambdaQueryWrapper<OpSlaConfig>()
                        .eq(OpSlaConfig::getBillType, billType)
                        .eq(OpSlaConfig::getStatus, "NORMAL")
                        .last("limit 1"));
    }

    /**
     * 查询全部 SLA 配置。
     */
    public java.util.List<OpSlaConfig> listAllConfigs() {
        return slaConfigMapper.selectList(
                new LambdaQueryWrapper<OpSlaConfig>()
                        .orderByAsc(OpSlaConfig::getPriority));
    }

    /**
     * 派单时设置 SLA 到期时间。
     */
    public LocalDateTime setSlaDueTime(DispatchTask task) {
        OpSlaConfig config = getConfigByBillType(task.getBillType());
        if (config == null) {
            return null;
        }
        LocalDateTime due = LocalDateTime.now().plusHours(config.getTargetHours());
        DispatchTask upd = new DispatchTask();
        upd.setId(task.getId());
        upd.setSlaDueTime(due);
        upd.setSlaStatus("NORMAL");
        dispatchTaskMapper.updateById(upd);
        return due;
    }

    /**
     * SLA 定时巡检：扫描 ASSIGNED 任务，检测预警/超时。
     */
    @Transactional(rollbackFor = Exception.class)
    public void slaCheck() {
        List<DispatchTask> assignedTasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getStatus, "ASSIGNED")
                        .isNotNull(DispatchTask::getSlaDueTime));

        LocalDateTime now = LocalDateTime.now();
        int warnings = 0;
        int breaches = 0;

        for (DispatchTask task : assignedTasks) {
            LocalDateTime due = task.getSlaDueTime();
            String currentStatus = task.getSlaStatus() == null ? "NORMAL" : task.getSlaStatus();

            if ("BREACHED".equals(currentStatus)) {
                continue;
            }

            if (now.isAfter(due)) {
                handleBreach(task, now);
                breaches++;
            } else if ("NORMAL".equals(currentStatus)) {
                OpSlaConfig config = getConfigByBillType(task.getBillType());
                if (config != null && config.getWarningHours() != null) {
                    LocalDateTime warningTime = due.minusHours(config.getWarningHours());
                    if (now.isAfter(warningTime)) {
                        handleWarning(task, now);
                        warnings++;
                    }
                }
            }
        }

        if (warnings > 0 || breaches > 0) {
            log.info("SLA巡检完成: {}个预警, {}个超时", warnings, breaches);
        }
    }

    private void handleWarning(DispatchTask task, LocalDateTime now) {
        DispatchTask upd = new DispatchTask();
        upd.setId(task.getId());
        upd.setSlaStatus("WARNING");
        dispatchTaskMapper.updateById(upd);

        OpSlaBreach breach = new OpSlaBreach();
        // 显式继承任务租户，避免在 ignore 租户上下文（定时任务）下 MetaObjectHandler 跳过填充导致 tenant_id 为 NULL
        breach.setTenantId(task.getTenantId());
        breach.setTaskId(task.getId());
        breach.setBillId(task.getBillId());
        breach.setBillType(task.getBillType());
        breach.setBreachType("WARNING");
        breach.setBreachTime(now);
        breach.setAssignedReviewerId(task.getAssignedReviewerId());
        breach.setRemark("SLA预警: 任务即将超时");
        slaBreachMapper.insert(breach);
    }

    private void handleBreach(DispatchTask task, LocalDateTime now) {
        DispatchTask upd = new DispatchTask();
        upd.setId(task.getId());
        upd.setSlaStatus("BREACHED");
        dispatchTaskMapper.updateById(upd);

        OpSlaBreach breach = new OpSlaBreach();
        // 显式继承任务租户，避免在 ignore 租户上下文（定时任务）下 MetaObjectHandler 跳过填充导致 tenant_id 为 NULL
        breach.setTenantId(task.getTenantId());
        breach.setTaskId(task.getId());
        breach.setBillId(task.getBillId());
        breach.setBillType(task.getBillType());
        breach.setBreachType("BREACH");
        breach.setBreachTime(now);
        breach.setAssignedReviewerId(task.getAssignedReviewerId());
        breach.setRemark("SLA超时: 任务未在规定时间内完成");

        OpSlaConfig config = getConfigByBillType(task.getBillType());
        if (config != null && config.getEscalationEnabled() != null && config.getEscalationEnabled() == 1
                && config.getEscalationGroupId() != null) {
            List<Reviewer> candidates = reviewerMapper.selectList(
                    new LambdaQueryWrapper<Reviewer>()
                            .eq(Reviewer::getReviewerGroupId, config.getEscalationGroupId())
                            .eq(Reviewer::getStatus, 1));
            if (!candidates.isEmpty()) {
                Reviewer selected = candidates.stream()
                        .min(java.util.Comparator.comparingInt(r -> r.getCurrentPending() != null ? r.getCurrentPending() : 0))
                        .orElse(candidates.get(0));
                breach.setEscalatedTo(selected.getId());
                breach.setRemark("SLA超时, 升级至: " + selected.getReviewerName());

                DispatchTask escalateUpd = new DispatchTask();
                escalateUpd.setId(task.getId());
                escalateUpd.setAssignedReviewerId(selected.getId());
                escalateUpd.setAssignedGroupId(config.getEscalationGroupId());
                escalateUpd.setUpgradedAt(now);
                escalateUpd.setUpgradeReason("SLA超时自动升级");
                dispatchTaskMapper.updateById(escalateUpd);
                log.warn("SLA超时升级: taskId={} -> reviewer={}", task.getId(), selected.getReviewerName());
            }
        }
        slaBreachMapper.insert(breach);
    }
}
