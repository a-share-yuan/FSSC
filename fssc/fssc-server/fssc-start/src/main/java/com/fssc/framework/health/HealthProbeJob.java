// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.health;

import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.notify.OpsAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 健康探针定时任务（P0-B1）。
 *
 * <p>为什么需要它：健康端点只解决「有人来问才知道」。无人问津的深夜，数据库掉线不会有人发现。
 * 本任务每 5 分钟主动探测一次，仅在<b>状态翻转</b>时告警（UP→DOWN 报故障、DOWN→UP 报恢复），
 * 避免持续故障期间刷屏——这与「同标题限流」是两层防护，一层管翻转、一层管频率。
 *
 * <p>挂 {@link JobHeartbeatService} 包装：自带心跳记录与多实例调度锁（多实例部署下只有一个实例探测），
 * 任务本身失败不崩应用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthProbeJob {

    private final HealthService healthService;
    private final OpsAlertService opsAlertService;
    private final JobHeartbeatService jobHeartbeatService;

    /** 上一次探测状态，用于识别翻转（初始视作 UP，避免启动后第一轮误报恢复） */
    private final AtomicReference<String> lastStatus = new AtomicReference<>("UP");

    /**
     * 每 5 分钟探测一次；initialDelay=120s 等应用与数据源完全就绪后再开始，避免启动误判。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void probe() {
        jobHeartbeatService.run("HealthProbeJob", () -> {
            Map<String, Object> detail = healthService.check();
            String now = String.valueOf(detail.get("status"));
            String prev = lastStatus.getAndSet(now);
            if (now.equals(prev)) {
                log.debug("[健康探针] 状态维持 {}，无需告警", now);
                return;
            }
            if ("DOWN".equals(now)) {
                opsAlertService.alert("ERROR",
                        "健康探针：服务组件不可用",
                        "探测结果：" + now + "\n组件明细：" + detail.get("components")
                                + "\n版本：" + detail.get("version")
                                + "\n建议：立即检查数据库/Redis 连通性与连接池，必要时执行 P0-B2 回滚预案。");
            } else {
                opsAlertService.alert("INFO",
                        "健康探针：服务已恢复",
                        "探测结果：" + now + "（上一状态 " + prev + "）\n组件明细：" + detail.get("components")
                                + "\n运行时长：" + detail.get("uptimeSeconds") + " 秒");
            }
        });
    }
}
