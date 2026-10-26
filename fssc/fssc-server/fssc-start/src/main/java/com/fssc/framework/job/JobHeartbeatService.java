// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.job.mapper.OpJobHeartbeatMapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.notify.OpsAlertService;
import com.fssc.framework.spi.DistributedState;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务心跳包装器：统一记录每个 @Scheduled 任务的最近执行时间/结果/耗时/连续失败次数，
 * 失败时推站内信告警（保持任务原有"失败不崩"语义——异常吞掉不外抛）。
 *
 * <p>用法：{@code heartbeat.run("RcOverdueJob", () -> {原逻辑});}，只包一层，不改任务内部逻辑。
 *
 * <p>【调度锁】多实例下同一任务同一时刻只跑一个实例：互斥统一走 {@link DistributedState#tryLock}——
 * {@code fssc.redis.enabled=true}（多实例）用 Redis SETNX+EX 锁；本地模式由
 * {@code LocalDistributedState} 内部保持原 MySQL {@code GET_LOCK} 语义（行为与现状一致）。
 * 锁异常（如 GET_LOCK 不支持）时降级为直接执行并仅告警一次，保持单机部署零阻塞。
 *
 * <p>【租户】本服务在 Spring 调度线程运行，无 TenantContext。心跳表 {@code op_job_heartbeat} 为全局表
 * （拦截器忽略名单）可直接读写；告警接收人读 {@code sys_config notify.job_alert_user}（默认演示租户1的用户1），
 * 发站内信需租户上下文，故先忽略租户查接收人租户，再 {@code callAs} 建立上下文后推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobHeartbeatService {

    /** 告警接收人配置键（默认 1 = 演示管理员） */
    private static final String ALERT_USER_CONFIG_KEY = "notify.job_alert_user";
    private static final long DEFAULT_ALERT_USER_ID = 1L;
    /** last_error 列长度上限，超出截断 */
    private static final int MAX_ERROR_LEN = 1000;
    /** 调度锁 key 前缀：单机/多实例互斥执行同一 @Scheduled 任务 */
    private static final String JOB_LOCK_PREFIX = "fssc_job_";
    /** Redis 锁最长持有时间（实例崩溃默认处理自动过期；远大于正常任务耗时） */
    private static final Duration JOB_LOCK_TTL = Duration.ofHours(2);

    private final OpJobHeartbeatMapper heartbeatMapper;
    private final NotifyService notifyService;
    private final OpsAlertService opsAlertService;
    private final SysConfigMapper configMapper;
    private final SysUserMapper userMapper;
    private final DistributedState distributedState;
    /** 分布式锁不可用/报错时只降级告警一次的开关 */
    private final AtomicBoolean lockUnsupportedWarned = new AtomicBoolean(false);

    /**
     * 带心跳记录地执行任务体。执行成功记 SUCCESS（耗时 + 清零连续失败），异常记 FAIL
     * （连续失败 +1、记错误摘要、推告警），异常吞掉不外抛。
     *
     * <p>多实例部署下用分布式锁保证同一任务同一时刻只有一个实例执行：
     * 拿不到锁（其他实例在跑）立即跳过本轮，且<b>不更新 last_run_at</b>——只有真正执行的任务才记录心跳。
     * 锁不可用（如本地模式非 MySQL/权限问题）时降级为直接执行并仅告警一次，保持单机部署零阻塞。
     */
    public void run(String jobName, Runnable body) {
        String lockName = JOB_LOCK_PREFIX + jobName;
        boolean acquired;
        try {
            acquired = distributedState.tryLock(lockName, JOB_LOCK_TTL);
        } catch (Exception e) {
            // 锁异常（本地 GET_LOCK 不支持等）：降级为直接执行，避免锁机制本身阻断任务
            if (lockUnsupportedWarned.compareAndSet(false, true)) {
                log.warn("[任务心跳] 分布式锁不可用，降级为不加锁直接执行 job={}", jobName, e);
            } else {
                log.debug("[任务心跳] 分布式锁异常，降级直接执行 job={}", jobName, e);
            }
            doRun(jobName, body);
            return;
        }
        if (!acquired) {
            log.info("[任务心跳] job={} 已被其他实例持有调度锁，跳过本轮（不更新心跳）", jobName);
            return;
        }
        try {
            doRun(jobName, body);
        } finally {
            try {
                distributedState.unlock(lockName);
            } catch (Exception e) {
                log.warn("[任务心跳] 释放调度锁失败 job={}", jobName, e);
            }
        }
    }

    /** 原心跳包装逻辑：记录 RUNNING→执行体→SUCCESS/FAIL，异常吞掉不外抛。 */
    private void doRun(String jobName, Runnable body) {
        long start = System.currentTimeMillis();
        LocalDateTime begin = LocalDateTime.now();
        try {
            markRunning(jobName, begin);
        } catch (Exception e) {
            log.warn("[任务心跳] 记录 RUNNING 失败 job={}", jobName, e);
        }
        try {
            body.run();
            try {
                markSuccess(jobName, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("[任务心跳] 记录 SUCCESS 失败 job={}", jobName, e);
            }
        } catch (Exception ex) {
            int failCount = 0;
            try {
                failCount = markFail(jobName, System.currentTimeMillis() - start, ex);
            } catch (Exception e) {
                log.warn("[任务心跳] 记录 FAIL 失败 job={}", jobName, e);
            }
            sendAlert(jobName, ex, failCount);
            // 保持任务原有"失败不崩"语义：吞掉异常不外抛
            log.error("[任务心跳] 任务执行失败 job={}：{}", jobName, ex.getMessage(), ex);
        }
    }

    /** 分页查询任务心跳（全局表，供「定时任务健康」页签展示，按最近执行时间倒序） */
    public PageResult<OpJobHeartbeat> page(long pageNum, long pageSize) {
        Page<OpJobHeartbeat> p = heartbeatMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<OpJobHeartbeat>().orderByDesc(OpJobHeartbeat::getLastRunAt));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** upsert RUNNING：置最近执行时间与状态，返回当前行（含旧 fail_count） */
    private OpJobHeartbeat markRunning(String jobName, LocalDateTime begin) {
        OpJobHeartbeat exist = selectByJob(jobName);
        if (exist == null) {
            exist = new OpJobHeartbeat();
            exist.setJobName(jobName);
            exist.setLastRunAt(begin);
            exist.setLastResult("RUNNING");
            exist.setFailCount(0);
            exist.setUpdateTime(begin);
            heartbeatMapper.insert(exist);
        } else {
            exist.setLastRunAt(begin);
            exist.setLastResult("RUNNING");
            exist.setUpdateTime(begin);
            heartbeatMapper.updateById(exist);
        }
        return exist;
    }

    /** SUCCESS：记耗时、清零连续失败、清错误（last_error 必须显式置 NULL） */
    private void markSuccess(String jobName, long durationMs) {
        OpJobHeartbeat exist = selectByJob(jobName);
        if (exist == null) {
            return;
        }
        exist.setLastResult("SUCCESS");
        exist.setDurationMs(durationMs);
        exist.setFailCount(0);
        exist.setLastError(null);
        exist.setUpdateTime(LocalDateTime.now());
        // R5-D-037：MyBatis-Plus 默认字段策略「非 null 才写」，updateById 配 setLastError(null) 不会发 SET last_error=NULL，
        // 历史错误会随下一次 SUCCESS 一直残留；改用显式 UpdateWrapper 强制清列，落实「上次失败才填该列」的写入侧不变量。
        heartbeatMapper.update(exist, new LambdaUpdateWrapper<OpJobHeartbeat>()
                .eq(OpJobHeartbeat::getId, exist.getId())
                .set(OpJobHeartbeat::getLastError, null));
    }

    /** FAIL：连续失败 +1、记错误摘要，返回新的连续失败次数 */
    private int markFail(String jobName, long durationMs, Throwable ex) {
        OpJobHeartbeat exist = selectByJob(jobName);
        int fails = 1;
        if (exist == null) {
            exist = new OpJobHeartbeat();
            exist.setJobName(jobName);
            exist.setLastRunAt(LocalDateTime.now());
        } else {
            fails = (exist.getFailCount() == null ? 0 : exist.getFailCount()) + 1;
        }
        exist.setLastResult("FAIL");
        exist.setDurationMs(durationMs);
        exist.setFailCount(fails);
        exist.setLastError(truncate(errMsg(ex)));
        exist.setUpdateTime(LocalDateTime.now());
        if (exist.getId() == null) {
            heartbeatMapper.insert(exist);
        } else {
            heartbeatMapper.updateById(exist);
        }
        return fails;
    }

    private OpJobHeartbeat selectByJob(String jobName) {
        return heartbeatMapper.selectOne(new LambdaQueryWrapper<OpJobHeartbeat>()
                .eq(OpJobHeartbeat::getJobName, jobName).last("limit 1"));
    }

    /** 失败站内信告警（读接收人 → 建立其租户上下文 → 推送），任何异常吞掉不外抛 */
    private void sendAlert(String jobName, Throwable ex, int failCount) {
        String title = "定时任务失败：" + jobName;
        String content = "任务「" + jobName + "」执行失败，连续失败 " + failCount + " 次。\n"
                + "错误摘要：" + truncate(errMsg(ex));
        alert(jobName, title, content);
        // P0-B1：业务站内信只推给配置的接收人（可能无人值守），再补一条运维通道告警（企微群 + 邮件）
        opsAlertService.alert("ERROR", title,
                content + "\n建议：检查 op_job_heartbeat.last_error 与 fssc-error.log（已按 traceId 归档）。");
    }

    /**
     * 向告警接收人推站内信（供失败告警与过期看门狗共用）。任何异常吞掉不外抛，接收人读不到时静默跳过。
     */
    public void alert(String jobName, String title, String content) {
        try {
            long alertUserId = resolveAlertUserId();
            // 接收人所属租户（忽略租户查 sys_user 取 tenant_id），无则回退演示租户1
            Long[] holder = new Long[1];
            TenantContext.runIgnoreTenant(() -> {
                SysUser u = userMapper.selectById(alertUserId);
                holder[0] = u == null ? null : u.getTenantId();
            });
            long tenantId = holder[0] == null ? 1L : holder[0];
            TenantContext.callAs(tenantId,
                    () -> notifyService.notifyUser(alertUserId, "ALERT", title, content, null, "/system/job-heartbeat"));
        } catch (Exception e) {
            log.warn("[任务心跳] 发送告警异常 job={}", jobName, e);
        }
    }

    /** 解析告警接收人用户ID（sys_config notify.job_alert_user，默认演示租户1的用户1） */
    private long resolveAlertUserId() {
        try {
            SysConfig[] holder = new SysConfig[1];
            TenantContext.runIgnoreTenant(() -> holder[0] = configMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, ALERT_USER_CONFIG_KEY)
                            .last("limit 1")));
            SysConfig c = holder[0];
            if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                return Long.parseLong(c.getConfigValue().trim());
            }
        } catch (Exception e) {
            log.warn("[任务心跳] 读取告警接收人配置失败，使用默认 {}", DEFAULT_ALERT_USER_ID, e);
        }
        return DEFAULT_ALERT_USER_ID;
    }

    private String errMsg(Throwable ex) {
        String m = ex.getMessage();
        return (m == null || m.isBlank()) ? ex.getClass().getName() : m;
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
