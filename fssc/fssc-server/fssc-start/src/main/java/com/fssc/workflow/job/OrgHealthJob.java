// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.job;

import com.fssc.common.core.Result;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.spi.DistributedState;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.service.OrgHealthChecker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 组织健康巡检 Job（OrgHealthJob）——把 4 条红线做成<b>每日自动巡检 + 手工触发校验</b>。
 *
 * <p>对应 {@link OrgHealthChecker} 的诊断能力。此Job 只读、只报，<b>不改任何数据</b>：
 * 「R1 补人 / R2 补部门树」属组织治理动作，必须由管理者显式决定，不能由定时任务擅自修改主数据。
 *
 * <p>为什么必须有这个：红线条目原先只是文档里的注意事项，而
 * 绝大多数公司不满足 R1，R2 也成片断裂——靠人工记忆与逐次排查不可持续。
 *
 * <p>手工触发入口（运维/内控用）：
 * <pre>GET /api/tenant/system/org-health/overview 查看体检报告（只读，需 wf:instance:admin）</pre>
 * <pre>POST /api/tenant/system/org-health/run 立即巡检一次（需 wf:instance:admin）</pre>
 */
@Slf4j
@RestController
@Component
@RequiredArgsConstructor
@RequestMapping("/api/tenant/system/org-health")
public class OrgHealthJob {

    private final OrgHealthChecker orgHealthChecker;
    private final JobHeartbeatService jobHeartbeatService;
    private final DistributedState distributedState;

    @Value("${fssc.org-health.enabled:true}")
    private boolean enabled;

    /**
     * 每日 04:10 巡检一次（避开 02:00 资产折旧、03:40 僵尸实例对账、03:xx 关账）。
     * cron：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 10 4 * * ?")
    public void dailyOrgHealthCheck() {
        if (!enabled) {
            log.info("[组织健康巡检] 开关关闭（fssc.org-health.enabled=false），跳过");
            return;
        }
        jobHeartbeatService.run("OrgHealthJob", () -> {
            try {
                if (!distributedState.tryLock("wf:org-health", Duration.ofMinutes(20))) {
                    return;
                }
                runOnce("定时巡检");
            } catch (Exception e) {
                log.warn("[组织健康巡检] 本轮执行异常（不影响业务，下轮重试）: {}", e.getMessage());
            }
        });
    }

    /** 巡检主体；跨租户执行——本检查读的是全局组织主数据（sys_company/sys_dept）。 */
    private void runOnce(String trigger) {
        Map<String, Object> report = checkIgnoreTenant();
        Object s = report.get("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) s;
        long errs = ((Number) summary.getOrDefault("errorCount", 0)).longValue();
        long warns = ((Number) summary.getOrDefault("warnCount", 0)).longValue();
        if (errs == 0 && warns == 0) {
            log.info("[组织健康巡检] {}：全部通过（{} 家公司）", trigger, summary.get("checkedCompanies"));
            return;
        }
        log.warn("[组织健康巡检] {}：ERROR {} 条、WARN {} 条（详见 GET /api/tenant/system/org-health/overview）",
                trigger, errs, warns);
    }

    /** 手工触发一次巡检（立即执行，忽略定时；需 wf:instance:admin）。 */
    @RequiresPerm("wf:instance:admin")
    @OperLog(module = "WF", operType = "OTHER", desc = "手工触发组织健康巡检")
    @PostMapping("/run")
    public Result<Map<String, Object>> runNow() {
        runOnce("手工触发");
        return Result.success(checkIgnoreTenant());
    }

    /** 查看体检报告（只读，每次现算；需 wf:instance:admin）。 */
    @RequiresPerm("wf:instance:admin")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(checkIgnoreTenant());
    }

    /**
     * 跨租户执行体检。
     * {@code TenantContext.runIgnoreTenant} 只接受 {@code Runnable}（无返回值），
     * 故用单元素数组承接结果，而不是假设它有 {@code Supplier} 重载。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkIgnoreTenant() {
        Map<String, Object>[] holder = new Map[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = orgHealthChecker.check());
        return holder[0] == null ? Map.of("summary", Map.of("errorCount", 0, "warnCount", 0), "issues", List.of()) : holder[0];
    }
}