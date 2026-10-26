// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.job;

import com.fssc.expense.service.ColdArchiveService;
import com.fssc.framework.job.JobHeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 单据冷热分离归档日任务：把最终状态超期的报销单搬到 exp_bill*_cold 冷表。
 *
 * <p>每日凌晨 03:40 执行（避开 01:20 应收逾期 / 02:00 影像批处理窗口），
 * 由 {@link JobHeartbeatService#run} 统一记录心跳 + MySQL GET_LOCK 调度锁 + 失败告警。
 *
 * <p>【开关】sys_config {@code archive.enabled=0} 时直接 return（log.debug），
 * 演示环境默认关闭防误动。多租户下任一租户 enabled=1 即触发；逐租户在 service 内部按 enabled 分流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColdArchiveJob {

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ColdArchiveService coldArchiveService;
    private final JobHeartbeatService jobHeartbeatService;

    @Scheduled(cron = "0 40 3 * * ?")
    public void archive() {
        jobHeartbeatService.run("ColdArchiveJob", () -> {
            if (!coldArchiveService.hasAnyEnabled()) {
                log.debug("[冷热归档] 所有租户 archive.enabled=0，跳过本轮");
                return;
            }
            String batchNo = "ARC" + LocalDateTime.now().format(BATCH_FMT);
            coldArchiveService.archive(batchNo);
        });
    }
}
