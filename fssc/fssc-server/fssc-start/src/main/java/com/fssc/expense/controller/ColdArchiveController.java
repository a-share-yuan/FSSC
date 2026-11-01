// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.controller;

import com.fssc.common.core.Result;
import com.fssc.expense.service.ColdArchiveService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 冷热归档运维入口。
 *
 * <p><b>为什么补这个口</b>：归档逻辑原本只有定时任务 {@code ColdArchiveJob} 触发，
 * 导致两个现实问题——① 运维无法在不等待调度周期的前提下验证归档链是否真的能跑；
 * ② {@code archive.enabled} 默认关，且无「现在会搬哪些单」的预演能力，
 * 真要开归档只能盲开。本 Controller 补齐 预演（dry-run，只 SELECT COUNT） 与
 * <b>手动执行</b> 两个动作。</p>
 *
 * <p><b>危险动作提示</b>：{@code /run} 是<b>单向</b>的——归档把单据从热表整行搬进
 * {@code exp_bill_cold} 等冷表并<b>删除热表</b>，系统<b>没有 restore 接口</b>。
 * 因此必须先用 {@code /dry-run} 看清张数，确认后再执行。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/expense/cold-archive")
@RequiredArgsConstructor
public class ColdArchiveController {

    private final ColdArchiveService coldArchiveService;

    /**
     * 预演：按当前配置统计「若现在执行会搬多少张」，只 SELECT，不改任何数据。
     * 返回逐租户的 enabled / coldMonths / count，便于先判断开关与阈值是否配置到位。
     */
    @RequiresPerm("system:archive:dry-run")
    @PostMapping("/dry-run")
    public Result<Map<String, Object>> dryRun() {
        return Result.success(coldArchiveService.dryRunCount());
    }

    /**
     * 立即执行一轮归档（等价于定时任务跑一次）。
     *
     * @param batchNo 批次号（落 {@code exp_cold_archive_log.batch_no}，便于回溯定位）；缺省按时间生成
     */
    @RequiresPerm("system:archive:run")
    @OperLog(module = "COLD_ARCHIVE", operType = "OTHER", desc = "手工触发冷热归档")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam(required = false) String batchNo) {
        String bn = (batchNo == null || batchNo.isBlank())
                ? "MANUAL-" + System.currentTimeMillis()
                : batchNo.trim();
        Map<String, Object> summary = coldArchiveService.archive(bn);
        log.warn("[冷热归档] 手工触发 batch={} 结果={}", bn, summary);
        return Result.success(summary);
    }
}
