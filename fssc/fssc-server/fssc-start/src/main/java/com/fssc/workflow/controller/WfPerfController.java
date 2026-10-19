// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.service.WfPerfService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流程绩效与积压分析（/api/tenant/wf/perf/**，方案 G3-2 真口径）。
 *
 * <p>权限与监控大屏同一档（{@code wf:instance:list}）：这里给的是全公司在途/办结的聚合读数，
 * 业务角色不需要、也不应该看到别人的时效。
 */
@RestController
@RequestMapping("/api/tenant/wf/perf")
@RequiredArgsConstructor
public class WfPerfController {

    private final WfPerfService wfPerfService;
    /** G0 止血（F13）：双账对账最近一次报告（只报不修，供运维核对差异样本） */
    private final com.fssc.workflow.job.WfLedgerReconcileJob wfLedgerReconcileJob;
    /** G4 第 6 条：流程实例归档只读预览（不触发任何搬移） */
    private final com.fssc.workflow.job.WfInstanceArchiveJob wfInstanceArchiveJob;

    /**
     * 流程实例归档预览：按当前配置回答"当前会归档多少条、N 是多少"。
     *
     * <p>纯只读：只做 COUNT，不搬移、不删除任何数据。权限与监控大屏同一档（{@code wf:instance:list}），
     * 因为返回的是流程侧归档口径读数。
     */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/archive-preview")
    public Result<Map<String, Object>> archivePreview() {
        return Result.success(wfInstanceArchiveJob.preview());
    }

    /** 双账对账报告：引擎账 ACT_* vs 业务账 wf_* 的三向量差异（只读，不触发修复） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/ledger-reconcile")
    public Result<Map<String, Object>> ledgerReconcile() {
        return Result.success(wfLedgerReconcileJob.lastReport());
    }

    /** 大屏顶部卡片：在途/今日办结/超时未办/积压>7天/SLA 达成率/平均节点停留 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(defaultValue = "30") int days) {
        return Result.success(wfPerfService.overview(days));
    }

    /**
     * R5-D-036：实例状态<b>全量</b>计数（{@code /wf/board} 顶部四卡的口径源）。
     *
     * <p>与 {@code /overview} 同一把权限闸（该页的实例列表本就要求 {@code wf:instance:list}），
     * 因此本端点不引入新的授权面；只做 5 次 COUNT，不跑 SLA/桑基的重聚合。</p>
     */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/instance-status")
    public Result<Map<String, Object>> instanceStatus() {
        return Result.success(wfPerfService.instanceStatusSummary());
    }

    /** 桑基：实例真实走过的节点路径（不再是拼造的 7 节点链） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/paths")
    public Result<Map<String, Object>> paths(@RequestParam(defaultValue = "30") int days) {
        return Result.success(wfPerfService.paths(days));
    }

    /** 热力：真实节点名 × 停留时长分桶（task_history 节点命中） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/node-dwell")
    public Result<Map<String, Object>> nodeDwell(@RequestParam(defaultValue = "30") int days) {
        return Result.success(wfPerfService.nodeDwell(days));
    }

    /** 积压账龄：在途单堆在哪个节点、堆了多久、超时未办榜 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/backlog")
    public Result<Map<String, Object>> backlog() {
        return Result.success(wfPerfService.backlog());
    }

    /** 按人时效 Top 榜：办结量/平均停留/超时办结 + 在办与超时未办 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/handler-top")
    public Result<Map<String, Object>> handlerTop(@RequestParam(defaultValue = "30") int days) {
        return Result.success(wfPerfService.handlerTop(days));
    }
}
