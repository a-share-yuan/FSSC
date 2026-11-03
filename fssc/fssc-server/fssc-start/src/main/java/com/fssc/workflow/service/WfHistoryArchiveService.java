// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.fssc.framework.job.JobHeartbeatService;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flowable 历史数据归档（需求文档 12.5）。
 * 按保留月数定时清理结束超过保留期的引擎账 {@code ACT_HI_*}；
 * 业务侧 wf_instance/wf_task_history 不由本类清理（单据追溯依赖，见 {@code WfInstanceArchiveJob}）。
 * 配置：fssc.wf.history-retention-months（默认 6）
 *
 * <p><b>保留策略改「归档后删」</b>：本类原先对超期实例<b>直接物理删除</b>
 * {@code ACT_HI_*}，而业务账 {@code wf_*} 要等 {@code WfInstanceArchiveJob} 且开关
 * {@code sys_config.wf.archive.enabled} 缺省为 0 ⇒ <b>引擎账可能在业务账尚未异地留存时就被销毁，
 * 不可恢复</b>。现加一道校验：只有该实例已落冷表 {@code wf_instance_cold} 才允许清理其引擎账；
 * 未落冷一律<b>跳过</b>（不删），并在本轮回合给出跳过条数与推进办法。
 *
 * <p><b>为什么校验查"冷表有没有"而不是本类自己去搬</b>：冷表主键即在线表主键，本类再插一次会主键冲突；
 * 且搬移的原子性（插冷→删在线同事务）与租户上下文已由 {@code WfInstanceArchiveJob} 负责。
 * 本类只做<b>前置校验</b>，判据即"业务账已异地留存"。二者月数天然有序（业务 3 个月 &le; 引擎 6 个月），
 * 故正常推进下校验不会成为阻塞点。
 *
 * <p><b>fail-closed</b>：校验查询自身异常（冷表未建、权限异常等）一律按"未落冷"处理——宁可不清理，
 * 也不销毁不可恢复的引擎账。零 DDL：复用 {@code V2.2.0_g4_archive_20260924.sql} 已建的三张冷表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfHistoryArchiveService {

    private final JdbcTemplate jdbcTemplate;
    private final JobHeartbeatService jobHeartbeatService;

    @Value("${fssc.wf.history-retention-months:6}")
    private int retentionMonths;

    /**
     * 调度补偿：本任务曾用 {@code @Scheduled(cron = "0 0 3 ? * SUN")}——全周只有<b>周日 03:00 那一分钟</b>
     * 是触发窗口。而 {@code op_job_heartbeat} 里有心跳的任务比代码侧
     * {@code jobHeartbeatService.run(...)} 包装的任务名少一个，<b>缺的那一行正是本任务</b>；而心跳行是在 {@code JobHeartbeatService.doRun} 的第一行无条件写入的
     * 跑成功/跑失败/清理 0 条都会留下行 ⇒ 无行＝本方法的 {@code @Scheduled} 从未触发，
     * 看门狗报"从未执行"是<b>真没跑</b>，不是统计方式漏。
     *
     * <p>根因＝触发面只有一周一瞬：Spring cron 无 missed-fire 补偿，进程只要没恰好覆盖住周日 03:00 那一分钟
     * 重启、发版、停机、周末不起服，整周的那一次就永久丢失，且下一周还得再赌同一个窗口。
     *
     * <p>修法＝把"每周一次"从<b>日历时刻</b>改成<b>节奏校验</b>：日频 tick（每天 03:30 判定一次，
     * 与同族 ColdArchiveJob 03:40 错开 10 分钟）＋ "距上次执行不足 6 天就跳过"。
     * 这样进程只要在本周内活过任意一个 03:30 槽位就会执行一次；节奏仍是周频，
     * 清理语义与保留期不变。判据读 {@code op_job_heartbeat.last_run_at}（全局表、无租户维度），零新增表零 DDL。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void scheduledArchive() {
        if (!weeklyWindowPassed()) {
            log.info("Flowable 历史归档：距上次执行未满 6 天（周频闸门），本轮跳过");
            return;
        }
        jobHeartbeatService.run("WfHistoryArchiveService", () -> {
            try {
                int n = archive();
                log.info("Flowable 历史归档完成，清理流程实例 {} 条（保留 {} 个月）", n, retentionMonths);
            } catch (Exception e) {
                log.error("Flowable 历史归档失败", e);
            }
        });
    }

    /**
     * 周频校验：{@code op_job_heartbeat} 里本任务的最近执行时间是否已超过 6 天（无行＝首次，直接放行）。
     * 读表异常时<b>放行</b>——本服务只做"归档后删"的前置校验，多跑一轮无害（去重），
     * 但因读不到校验而永不执行才是真正的风险。
     */
    private boolean weeklyWindowPassed() {
        try {
            Timestamp last = jdbcTemplate.queryForObject(
                    "/* tenant-ok: 按 proc_inst_id 全局唯一键判冷归档 */ SELECT last_run_at FROM op_job_heartbeat WHERE job_name = 'WfHistoryArchiveService' LIMIT 1",
                    Timestamp.class);
            if (last == null) {
                return true;
            }
            return last.toLocalDateTime().plusDays(6).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            log.debug("读取 WfHistoryArchiveService 心跳失败，按已过窗口放行: {}", e.getMessage());
            return true;
        }
    }

    /** 清理超期历史：返回清理的流程实例数 */
    public int archive() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(Math.max(1, retentionMonths));
        int total = 0;
        int skipped = 0;
        // 分批取超期流程实例（避免一次性内存过大）
        List<Long> ids = queryExpiredIds(cutoff, 500);
        while (!ids.isEmpty()) {
            int purgedInBatch = 0;
            for (Long id : ids) {
                try {
                    // 校验：业务账未落冷表则跳过——引擎账删了不可恢复，必须先异地留存
                    if (!coldArchived(id)) {
                        skipped++;
                        continue;
                    }
                    deleteProcess(id);
                    total++;
                    purgedInBatch++;
                } catch (Exception e) {
                    log.warn("归档清理失败 procInstId={}", id, e);
                }
            }
            // 本批零进展 ⇒ 队首实例均"未落冷"（超期集按 END_TIME_ 升序取，天然形成队首阻塞），
            // 继续重取只会拿到同一批 ⇒ 必须退出，否则死循环
            if (purgedInBatch == 0) {
                break;
            }
            ids = queryExpiredIds(cutoff, 500);
        }
        if (skipped > 0) {
            log.warn("Flowable 历史归档（保留策略＝归档后删）：本轮跳过 {} 条超期实例——其业务账尚未落冷表"
                            + "wf_instance_cold，故引擎账 ACT_HI_* 暂不清理。推进办法：先开 sys_config.wf.archive.enabled，"
                            + "由 WfInstanceArchiveJob 把 wf_* 搬入冷表（months 硬钳 ≤3）后，下一轮本服务即会清理其引擎账。",
                    skipped);
        }
        return total;
    }

    private List<Long> queryExpiredIds(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.queryForList(
                "/* tenant-ok: 按 proc_inst_id 全局唯一键判冷归档 */ SELECT ID_ FROM ACT_HI_PROCINST WHERE END_TIME_ IS NOT NULL AND END_TIME_ < ? ORDER BY END_TIME_ ASC LIMIT ?",
                Long.class, cutoff, limit);
    }

    /**
     * 校验：该流程实例的业务账是否已落冷表（{@code wf_instance_cold}）。
     *
     * <p>只有已落冷才允许物理清理其 {@code ACT_HI_*}——判据即"已异地留存，销毁不可恢复的引擎账是安全的"。
     * 查询异常（冷表未建／权限异常等）一律按"未落冷"返回（fail-closed，宁留不删）；
     * 此处只记 debug，避免冷表不可用时按实例刷屏，全局规则由 {@link #archive()} 的跳过告警统一给出。
     */
    private boolean coldArchived(Long procInstId) {
        try {
            Long n = jdbcTemplate.queryForObject(
                    "/* tenant-ok: 按 proc_inst_id 全局唯一键判冷归档 */ SELECT COUNT(*) FROM wf_instance_cold WHERE proc_inst_id = ?", Long.class, procInstId);
            return n != null && n > 0;
        } catch (Exception e) {
            log.debug("查询 wf_instance_cold 失败，按未落冷处理 procInstId={}: {}", procInstId, e.getMessage());
            return false;
        }
    }

    /** 按流程实例删除各历史子表（顺序：先子表后主表，避免外键残留） */
    private void deleteProcess(Long procInstId) {
        jdbcTemplate.update("DELETE FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_DETAIL WHERE PROC_INST_ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_ACTINST WHERE PROC_INST_ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_TASKINST WHERE PROC_INST_ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_VARINST WHERE PROC_INST_ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_PROCINST WHERE ID_ = ?", procInstId);
        jdbcTemplate.update("DELETE FROM ACT_HI_IDENTITYLINK WHERE PROC_INST_ID_ = ?", procInstId);
    }
}
