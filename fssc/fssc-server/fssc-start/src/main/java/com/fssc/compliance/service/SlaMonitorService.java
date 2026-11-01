// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.DispatchTask;
import com.fssc.compliance.entity.OpSlaBreach;
import com.fssc.compliance.entity.OpSlaConfig;
import com.fssc.compliance.mapper.DispatchTaskMapper;
import com.fssc.compliance.mapper.OpSlaBreachMapper;
import com.fssc.compliance.mapper.OpSlaConfigMapper;
import com.fssc.compliance.mapper.ReviewerMapper;
import com.fssc.compliance.entity.Reviewer;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA 监控服务（共享运营实质化）。
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
    public List<OpSlaConfig> listAllConfigs() {
        return slaConfigMapper.selectList(
                new LambdaQueryWrapper<OpSlaConfig>()
                        .orderByAsc(OpSlaConfig::getPriority));
    }

    // 维护面（补上写入口；原先只有 list/check，SLA 阈值改一次只能改库）

    /**
     * 新增 SLA 配置。
     *
     * <p>{@code billType} 是业务唯一键（{@link #getConfigByBillType} 按它取配置，且
     * {@code status='NORMAL'} 生效），重复配置会让运行期取到哪一条变得不确定 ⇒ 这里直接拒。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpSlaConfig cfg) {
        normalize(cfg);
        if (cfg.getBillType() != null && findByBillType(cfg.getBillType(), null) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据类型已存在 SLA 配置：" + cfg.getBillType() + "，请直接编辑该条");
        }
        cfg.setId(null);
        slaConfigMapper.insert(cfg);
        log.info("[sla] 新增配置 billType={} target={}h warn={}h",
                cfg.getBillType(), cfg.getTargetHours(), cfg.getWarningHours());
        return cfg.getId();
    }

    /** 更新 SLA 配置；{@code id} 为空视为新增，防止前端漏传 id 时静默插一条脏数据。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(OpSlaConfig cfg) {
        if (cfg.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 SLA 配置 id，无法更新");
        }
        normalize(cfg);
        if (cfg.getBillType() != null && findByBillType(cfg.getBillType(), cfg.getId()) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据类型已被其它 SLA 配置占用：" + cfg.getBillType());
        }
        slaConfigMapper.updateById(cfg);
        log.info("[sla] 更新配置 id={} billType={}", cfg.getId(), cfg.getBillType());
    }

    /** 删除 SLA 配置。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OpSlaConfig old = slaConfigMapper.selectById(id);
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SLA 配置不存在：" + id);
        }
        slaConfigMapper.deleteById(id);
        log.info("[sla] 删除配置 id={} billType={}", id, old.getBillType());
    }

    /** 查同一 billType 的其它配置（excludeId 用于更新时排除自身）。 */
    private OpSlaConfig findByBillType(String billType, Long excludeId) {
        return slaConfigMapper.selectOne(new LambdaQueryWrapper<OpSlaConfig>()
                .eq(OpSlaConfig::getBillType, billType)
                .ne(excludeId != null, OpSlaConfig::getId, excludeId)
                .last("limit 1"));
    }

    /** 入库前归一：去空格、默认值、校验预警不晚于目标。 */
    private void normalize(OpSlaConfig cfg) {
        if (cfg.getBillType() != null) {
            cfg.setBillType(cfg.getBillType().trim().toUpperCase());
        }
        if (cfg.getTargetHours() == null || cfg.getTargetHours() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SLA 目标时长（小时）必须为正数");
        }
        // 预警晚于目标 = 永远不预警，属于配置自相矛盾，直接拦下而不是静默生效
        if (cfg.getWarningHours() != null && cfg.getWarningHours() >= cfg.getTargetHours()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "SLA 预警时长（" + cfg.getWarningHours() + "h）必须小于目标时长（"
                            + cfg.getTargetHours() + "h），否则永不触发预警");
        }
        if (cfg.getStatus() == null || cfg.getStatus().isBlank()) {
            cfg.setStatus("NORMAL");
        }
        if (cfg.getPriority() == null) {
            cfg.setPriority(0);
        }
        if (cfg.getEscalationEnabled() == null) {
            cfg.setEscalationEnabled(0);
        }
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
                        .min(Comparator.comparingInt(r -> r.getCurrentPending() != null ? r.getCurrentPending() : 0))
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
