// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.mapper.OpJobHeartbeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务过期看门狗：每小时扫描 {@code op_job_heartbeat}，对「未按期望频率执行」或「连续失败」的任务
 * 推站内信告警（复用 {@link JobHeartbeatService#alert} 同一告警通道）。
 *
 * <p>期望频率（每任务最大允许间隔）：cron 型给 26h（周任务给 8d），fixedDelay 型给 15min。
 * 内置任务名须与各 {@code @Scheduled} 方法包装时使用的类名一致。
 *
 * <p>防重：同一 job 同一告警类型 4 小时内不重复推送（进程内 map）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobWatchdog {

    private static final Duration HOUR_26 = Duration.ofHours(26);
    private static final Duration DAY_8 = Duration.ofDays(8);
    private static final Duration DAY_33 = Duration.ofDays(33);
    private static final Duration MIN_15 = Duration.ofMinutes(15);
    private static final Duration MIN_30 = Duration.ofMinutes(30);
    /** 同 job 同类型告警最小间隔 */
    private static final Duration ALERT_DEDUP = Duration.ofHours(4);
    /** 连续失败达到该次数即告警 */
    private static final int FAIL_STREAK_THRESHOLD = 3;

    private final OpJobHeartbeatMapper heartbeatMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /**
     * 期望表：任务名 -> 最大允许执行间隔。
     *
     * <p>D-DEC-163 同族的「统计口径漏」腿（现库实测 2026-10-01）：代码侧
     * {@code jobHeartbeatService.run(...)} 包装的任务名共 <b>20</b> 个，本表原先只登记 <b>9</b> 个，
     * 其余 <b>11</b> 个有心跳行却不在期望表里——它们停摆了不会有任何告警。
     * 现按各自 {@code @Scheduled} 注解的真实频率补齐（月度型给 33 天）。
     * 频率取值依据＝类上注解，非猜测：见每行注释。
     */
    private static final Map<String, Duration> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("RcOverdueJob", HOUR_26);             // cron 每日 01:20
        EXPECTED.put("ArchiveExpireJob", HOUR_26);         // cron 每日 02:30
        EXPECTED.put("ReconcileService", HOUR_26);         // cron 每日 02:00
        EXPECTED.put("RptSummaryService", HOUR_26);        // cron 每日 01:30
        EXPECTED.put("ArchiveService", HOUR_26);           // cron 每日 02:00（共享归档）
        // D-DEC-163：本任务已从「周日 03:00 一瞬」改为「每日 03:30 tick + 6 天节奏闸门」，
        // 最大间隔＝6 天闸门 + 1 天 tick = 7 天，8 天的容忍度仍然够用（不改，避免放宽成漏检）。
        EXPECTED.put("WfHistoryArchiveService", DAY_8);
        EXPECTED.put("InvoiceAbnormalAlertJob", MIN_15);   // fixedDelay 5min
        EXPECTED.put("InvoiceVerifyScheduler", MIN_15);    // fixedDelay 5min
        EXPECTED.put("WfTimeoutJob", MIN_15);              // fixedDelay 1min
        // —— 以下 10 行为本轮补登记（原先有心跳行却不在期望表 = 停摆零告警的漏检面）——
        EXPECTED.put("AutoDispatchJob", MIN_15);           // compliance/job fixedDelay 1min
        EXPECTED.put("HealthProbeJob", MIN_15);            // framework/health fixedDelay 5min
        EXPECTED.put("StabilityLogJob", HOUR_26);          // cron 每日 23:55（另有 5min tick，取慢的那条）
        EXPECTED.put("WfDelegateExpireJob", MIN_30);       // fixedDelay 10min
        EXPECTED.put("WfDelegateTakeoverSweep", MIN_30);   // fixedDelay 10min
        EXPECTED.put("OpTaskDueTimeScanJob", MIN_15);      // fixedDelay 5min
        EXPECTED.put("WfLedgerReconcileJob", MIN_30);      // fixedDelayString 默认 600000ms＝10min
        EXPECTED.put("WfInstanceArchiveJob", HOUR_26);     // fixedDelay 24h + initialDelay 5min
        EXPECTED.put("ColdArchiveJob", HOUR_26);           // cron 每日 03:40
        EXPECTED.put("FxRevalJob", HOUR_26);               // cron 每日 01:10
        // 折旧自动批次＝每月 1 日 02:00，容忍度必须按"月"给，否则每天误报 STALE
        EXPECTED.put("AssetDeprAutoJob", DAY_33);          // cron 0 0 2 1 * ?（每月 1 日）
        EXPECTED.put("PoOrderFinalStateJob", HOUR_26);     // purchase/job cron 每日 03:10（X-20 采购终态推进，心跳名＝类名）
    }

    /** 进程内告警防重：job:类型 -> 上次告警时间 */
    private final Map<String, LocalDateTime> lastAlertAt = new ConcurrentHashMap<>();

    @Scheduled(cron = "0 0 * * * ?")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Duration> e : EXPECTED.entrySet()) {
            String jobName = e.getKey();
            Duration maxInterval = e.getValue();
            try {
                OpJobHeartbeat hb = heartbeatMapper.selectOne(new LambdaQueryWrapper<OpJobHeartbeat>()
                        .eq(OpJobHeartbeat::getJobName, jobName).last("limit 1"));
                checkOne(jobName, hb, maxInterval, now);
            } catch (Exception ex) {
                log.warn("[任务看门狗] 检查 job={} 异常", jobName, ex);
            }
        }
    }

    private void checkOne(String jobName, OpJobHeartbeat hb, Duration maxInterval, LocalDateTime now) {
        String type;
        String detail;
        if (hb == null) {
            type = "MISSING";
            // D-DEC-163：把"无行"说清楚——心跳行是 JobHeartbeatService.doRun 进方法第一件事就写的，
            // 跑成功、跑失败、清理 0 条，三种结局都会留下一行（例外只有"写心跳本身报错"与
            // "调度锁被他实例持有"，两者在日志里都有独立 warn/info 痕迹）。所以缺行最可能的解释是：
            // 该 @Scheduled 方法<b>从未被调度器触发</b>（调度未生效 / 触发窗口从未被进程存活期覆盖），
            // 而不是"执行了但失败"。原文案"可能从未执行或调度未生效"两种可能并列，值班无法据此定位。
            detail = "op_job_heartbeat 无 job_name=" + jobName + " 行 ⇒ 大概率 @Scheduled 方法从未被触发"
                    + "（成功/失败/零处理量三种结局都会留行，故不是「执行了但失败」）。"
                    + "请确认：该任务是否已被注册、其触发窗口（cron 时刻或 fixedDelay）是否被进程存活期覆盖过、"
                    + "以及日志里是否有「分布式锁不可用」「已被其他实例持有调度锁」两类例外。";
        } else if ("FAIL".equals(hb.getLastResult())
                && hb.getFailCount() != null && hb.getFailCount() >= FAIL_STREAK_THRESHOLD) {
            type = "FAIL_STREAK";
            detail = "连续失败 " + hb.getFailCount() + " 次"
                    + (hb.getLastError() == null ? "" : "，最近错误：" + hb.getLastError());
        } else if (hb.getLastRunAt() == null || hb.getLastRunAt().plus(maxInterval).isBefore(now)) {
            type = "STALE";
            detail = "距上次执行已超 " + humanize(maxInterval) + "（最近执行 " + hb.getLastRunAt() + "）";
        } else {
            return; // 健康
        }
        if (shouldSuppress(jobName, type, now)) {
            return;
        }
        String title = "定时任务健康告警：" + jobName;
        String content = "任务「" + jobName + "」异常。" + detail + "。请检查「系统监控 → 定时任务健康」。";
        jobHeartbeatService.alert(jobName, title, content);
        log.warn("[任务看门狗] job={} 告警 type={}：{}", jobName, type, detail);
    }

    /** 同 job 同类型 4h 内不重推；放行则记录本次时间 */
    private boolean shouldSuppress(String jobName, String type, LocalDateTime now) {
        String key = jobName + ":" + type;
        LocalDateTime prev = lastAlertAt.get(key);
        if (prev != null && prev.plus(ALERT_DEDUP).isAfter(now)) {
            return true;
        }
        lastAlertAt.put(key, now);
        return false;
    }

    private String humanize(Duration d) {
        // 分钟级容忍度（MIN_15 / MIN_30）走 toHours() 会算成 0 ⇒ 告警文案写成"已超 0 小时"，
        // 值班同学无法据此判断该不该处理。先按分钟出可读值，整天/整小时再升档。
        long minutes = d.toMinutes();
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        long hours = d.toHours();
        if (hours >= 24 && hours % 24 == 0) {
            return (hours / 24) + " 天";
        }
        return hours + " 小时";
    }
}
