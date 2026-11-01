// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.compliance.entity.DispatchTask;
import com.fssc.compliance.mapper.DispatchTaskMapper;
import com.fssc.compliance.service.DispatchService;
import com.fssc.compliance.service.SlaMonitorService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 共享运营自动派单定时任务。
 *
 * 每 60 秒扫描一次 PENDING 状态的派单任务（dispatch_task），按 op_dispatch_rule 规则
 * 自动匹配并分配给目标审核组中最优的审核员。
 *
 * 派单策略：
 * - ROUND_ROBIN：轮询（选当前待办数最少的）
 * - LOAD_BALANCE：负载均衡（选负载率最低的）
 * - SKILL_MATCH：技能匹配（选待办数最少且有匹配技能的）
 * - CREDIT_BASED：信用分驱动（优先派给高信用审核员）
 *
 * 触发条件：单据提交审批后产生 dispatch_task 记录（status=PENDING），
 * 本任务负责将其转为 ASSIGNED 并分配审核员。
 *
 * <h3>【租户上下文铁律】</h3>
 * 定时任务跑在 Spring 调度线程，无 {@link TenantContext}。本任务若用
 * {@code runIgnoreTenant} 包住整个处理链，导致两类缺陷：
 * <ol>
 *   <li><b>写入落 null</b>：{@code FsscMetaObjectHandler} 在 ignore 上下文跳过 tenantId 填充，
 *       {@code op_sla_breach.tenant_id} 为 NULL 触发 NOT NULL 约束 → SLA 预警/超时升级 100% 失败；</li>
 *   <li><b>跨租户误匹配</b>：{@code op_dispatch_rule} / {@code op_sla_config} 失去租户过滤，
 *       可能取到其它租户的规则与配置。</li>
 * </ol>
 * 与 {@code RcOverdueJob} 一致的范式：<b>聚合扫描用 {@code runIgnoreTenant}（只读、不写入数据库），
 * 逐租户处理时 {@code callAs(tenantId)} 恢复上下文</b>，使租户过滤与字段填充均恢复正常。
 *
 * @see DispatchService#autoDispatchCheck()
 * @see SlaMonitorService#slaCheck()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDispatchJob {

    private final DispatchService dispatchService;
    private final SlaMonitorService slaMonitorService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /**
     * 每 60 秒执行一次自动派单扫描。
     * initialDelay=30s 等应用启动完成后开始。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void autoDispatch() {
        jobHeartbeatService.run("AutoDispatchJob", () -> {
            try {
                // 1) 跨租户聚合扫描（只读，不写入数据库）：找出"有待派单或有在办派单"的租户
                List<DispatchTask>[] holder = new List[1];
                TenantContext.runIgnoreTenant(() -> holder[0] = dispatchTaskMapper.selectList(
                        new LambdaQueryWrapper<DispatchTask>()
                                .select(DispatchTask::getTenantId)
                                .in(DispatchTask::getStatus, "PENDING", "ASSIGNED")));
                List<DispatchTask> scanned = holder[0];
                if (scanned == null || scanned.isEmpty()) {
                    return;
                }
                Set<Long> tenantIds = scanned.stream()
                        .map(DispatchTask::getTenantId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (tenantIds.isEmpty()) {
                    return;
                }

                // 2) 逐租户在租户上下文内处理（保证 tenant_id 填充与规则/配置隔离）
                for (Long tenantId : tenantIds) {
                    try {
                        TenantContext.callAs(tenantId, () -> {
                            dispatchService.autoDispatchCheck();
                            // SLA 违约检测与自动升级
                            slaMonitorService.slaCheck();
                        });
                    } catch (Exception ex) {
                        log.error("[自动派单] 租户 {} 处理失败", tenantId, ex);
                    }
                }
            } catch (Exception e) {
                log.error("自动派单定时任务异常", e);
            }
        });
    }
}
