// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.job;

import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.system.gateway.IntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集成外呼失败重投扫描 Job。
 *
 * <p>周期扫描 {@code integration_call_log} 中 success=0 且未达重试上限的记录，逐条在原租户上下文内重投，
 * 复用同一 log 行（retry_count 自增封顶），避免无限重试；重投安全护栏由
 * {@link IntegrationGateway#retryFailed(int, int)} 内联实现（只读类通道或带唯一键的记录才纳入）。</p>
 *
 * <p><b>默认关闭</b>（{@code fssc.connector.retry.enabled=false}）：本 Job 会主动向第三方发起外呼，
 * 属对外部系统的副作用动作，按"对外动作先确认"原则默认不启用；确认联调环境就绪后置 true 开启，
 * 无需重启逻辑代码（配置热生效）。</p>
 *
 * <p>心跳复用 {@link JobHeartbeatService}：多实例互斥、失败计数与告警由框架统一处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationRetryScanJob {

    private final IntegrationGateway integrationGateway;
    private final JobHeartbeatService jobHeartbeatService;

    /** 重投总开关（默认 false，需显式开启） */
    @Value("${fssc.connector.retry.enabled:false}")
    private boolean enabled;

    /** 单条记录最大重投次数 */
    @Value("${fssc.connector.retry.max:3}")
    private int maxRetry;

    /** 只重投最近 N 小时内失败的记录 */
    @Value("${fssc.connector.retry.lookback-hours:24}")
    private int lookbackHours;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void scan() {
        if (!enabled) {
            return;
        }
        jobHeartbeatService.run("IntegrationRetryScanJob", () -> {
            try {
                int ok = integrationGateway.retryFailed(maxRetry, lookbackHours);
                if (ok > 0) {
                    log.info("P0-3 外呼失败重投成功 {} 条（maxRetry={} lookbackHours={}）", ok, maxRetry, lookbackHours);
                }
            } catch (Exception e) {
                log.error("P0-3 外呼重投扫描异常", e);
            }
        });
    }
}
