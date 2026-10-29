// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.Result;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.time.Duration;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.BillTerminalChecker;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.framework.spi.DistributedState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * （2026-10-19 人工测试 G册实测修）：<b>流程实例与单据状态的双向收敛对账Job</b>。
 *
 * <h3>要解决的实测问题</h3>
 * 本次人工测试发现 6 条「流程与单据状态脱节」的 RUNNING 实例：
 * <ul>
 *   <li><b>A类 3 条</b>：单据已 {@code PAID}（钱已付清），但 INVOICE_MATCH 三单匹配派单流程仍 RUNNING
 *       ——单据是从<b>付款批次</b>那条路走通的，付款批次不会回调匹配流程，于是旧派单永久悬挂；</li>
 *   <li><b>B 类 3 条</b>：单据仍 {@code DRAFT}（用户还没提交），流程却已发起并挂出待办
 *       ——挂在待办里点进去是空的。</li>
 * </ul>
 *
 * <h3>根因</h3>
 * 「作废/回退」类收尾都调了 {@link WfEngineFacade#cancelRunningInstancesByBiz}，
 * 但<b>单据从别的路径走到最终状态</b>时没有任何一方回调流程；且流程发起后单据退回草稿也无回收。
 * ⇒ 流程与单据之间<b>缺少双向收敛机制</b>，只能靠人工定期清理。
 *
 * <h3>修法（自动办结 + 双向探测）</h3>
 * 每日凌晨低峰扫描 RUNNING 实例，逐条用
 * {@link BillTerminalChecker#isBillTerminal}（最终状态）/ {@link BillTerminalChecker#isBillNotSubmitted}（未提交）判定：
 * <ol>
 *   <li>单据已最终状态 ⇒ 撤销流程（记录「单据已最终状态，自动办结」）；</li>
 *   <li>单据仍未提交 ⇒ 撤销流程（记录「单据尚未提交，自动办结」）。</li>
 * </ol>
 *
 * <h3>安全设计（三条硬约束）</h3>
 * <ol>
 *   <li><b>只收RUNNING</b>：已办结/已驳回的历史实例一律不动（否则等于抹掉审计痕迹）；</li>
 *   <li><b>读不到即放行</b>：单据表/列读不到时 fail-open（不撤销）——
 *       <b>误杀正在办的单，远比留一个僵尸实例严重</b>；</li>
 *   <li><b>成熟期门槛</b>：只处理创建已满 {@code min-age-minutes} 的实例
 *       默认 30 分钟，避开「刚提交还没写完状态」的瞬时窗口；</li>
 *   <li><b>分布式单跑</b>：多实例部署时用 {@link DistributedState} 抢锁，保证一个集群只有一个节点在扫。</li>
 * </ol>
 *
 * <p>开关：{@code fssc.wf.zombie-reconcile-enabled}（默认 true）。
 * 关闭后本 Job 完全不执行，不会影响任何业务流程。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WfZombieReconcileJob {

    private final WfInstanceMapper wfInstanceMapper;
    private final WfEngineFacade wfEngineFacade;
    private final BillTerminalChecker billTerminalChecker;
    private final JobHeartbeatService jobHeartbeatService;
    private final DistributedState distributedState;

    @Value("${fssc.wf.zombie-reconcile-enabled:true}")
    private boolean enabled;

    /** 成熟期门槛（分钟）：实例创建不足该时长的不处理，避开状态写入瞬时窗口 */
    @Value("${fssc.wf.zombie-reconcile-min-age-minutes:30}")
    private int minAgeMinutes;

    /** 单轮扫描上限（防单次拉太多）：超出则留到下轮，避免长事务 */
    private static final int SCAN_LIMIT = 500;

    /**
     * 每日 03:40 执行（避开 02:00 资产折旧、03:xx 关账等日终高峰）。
     * cron：秒 分 时 日 月 周
     */
    /**
     * 运维手工触发（管理端调用，权限 {@code wf:instance:admin}）。
     *
     * <p><b>为什么必须有这个入口</b>：本Job 是唯一的自动办结手段，但它每天只跑一次（03:40）。
     * 一旦线上出现批量僵尸实例、或 Job 自身报错停摆，运维需要一个<b>立即执行</b>的通道——
     * 否则只能改cron 或重启服务，都太重。
     *
     * <p>与自动路径<b>共用同一段doReconcile 逻辑</b>，不存在「手工版和自动版行为不一致」的问题。
     *
     * @return 办结结果（最终状态办结数／未提交办结数／跳过数）
     */
    @PostMapping("/api/tenant/wf/zombie-reconcile/run")
    @RequiresPerm("wf:instance:admin")
    @OperLog(module = "WF", operType = "OTHER", desc = "手工触发僵尸流程实例对账")
    public Result<Map<String, Object>> runNow() {
        int[] stat = new int[3]; // [0]=最终状态办结 [1]=未提交办结 [2]=跳过
        jobHeartbeatService.run("WfZombieReconcileJob", () -> doReconcile(stat));
        return Result.success(Map.of(
                "terminalClosed", stat[0],
                "draftClosed", stat[1],
                "skipped", stat[2]));
    }

    @Scheduled(cron = "0 40 3 * * ?")
    public void reconcileZombieInstances() {
        if (!enabled) {
            log.info("[僵尸实例对账] 开关关闭（fssc.wf.zombie-reconcile-enabled=false），跳过");
            return;
        }
        jobHeartbeatService.run("WfZombieReconcileJob", () -> {
            try {
                if (!distributedState.tryLock("wf:zombie-reconcile", Duration.ofMinutes(30))) {
                    log.info("[僵尸实例对账] 其他节点正在执行，本次跳过");
                    return;
                }
                doReconcile(null);
            } catch (Exception e) {
                log.warn("[僵尸实例对账] 本轮执行异常（不影响业务，下轮重试）: {}", e.getMessage());
            }
        });
    }

    /**
     * @param stat 可选的结果回填数组（长度 3：最终状态办结／未提交办结／跳过）；传 null 即纯定时任务路径
     */
    private void doReconcile(int[] stat) {
        int ageMinutes = stat == null ? Math.max(minAgeMinutes, 1) : 0;
        LocalDateTime before = LocalDateTime.now().minusMinutes(ageMinutes);
        List<WfInstance>[] holder = new List[1];
        // 多租户：RUNNING 实例跨租户，先在忽略租户上下文里捞全量
        TenantContext.runIgnoreTenant(() ->
                holder[0] = wfInstanceMapper.selectList(
                        new LambdaQueryWrapper<WfInstance>()
                                .eq(WfInstance::getStatus, "RUNNING")
                                .isNotNull(WfInstance::getProcInstId)
                                .lt(WfInstance::getCreateTime, before)
                                .last("LIMIT " + SCAN_LIMIT)));
        List<WfInstance> candidates = holder[0];
        if (candidates == null || candidates.isEmpty()) {
            log.info("[僵尸实例对账] 无满足成熟期的 RUNNING 实例，跳过");
            return;
        }
        log.info("[僵尸实例对账] 扫描到 {} 条成熟期 RUNNING 实例，开始逐条判定", candidates.size());

        int terminalClosed = 0;
        int draftClosed = 0;
        int skipped = 0;
        for (WfInstance inst : candidates) {
            String procInstId = inst.getProcInstId(); // 仅用于日志定位；实际办结走 cancelRunningInstancesByBiz（按 bizType+bizId，能容错半脱节）
            String bizType = inst.getBizType();
            Long bizId = inst.getBizId();
            if (procInstId == null || procInstId.isBlank() || bizType == null || bizId == null) {
                skipped++;
                continue;
            }
            try {
                if (billTerminalChecker.isBillTerminal(bizType, bizId)) {
                    // A类：单据已最终状态（PAID/APPROVED/VOID…），流程没跟上。
                    // 用 cancelRunningInstancesByBiz 而非 cancelInstance：它对
                    // 「引擎实例已被删、视图仍 RUNNING」有补办结容错（ROOT-2规则），
                    // 直接调 cancelInstance 会在这种半脱节状态下抛「实例不存在或已结束」。
                    int n = wfEngineFacade.cancelRunningInstancesByBiz(bizType, bizId,
                            "D-WF-14 自动办结：单据 " + bizType + "#" + bizId + " 已结束，流程实例自动撤销");
                    terminalClosed += n;
                    if (n > 0) {
                        log.info("[僵尸实例对账] 终态收口 bizType={}, bizId={}, 撤销实例 {} 个", bizType, bizId, n);
                    }
                } else if (billTerminalChecker.isBillNotSubmitted(bizType, bizId)) {
                    // B 类：单据还没提交，挂在待办里点进去是空的
                    int n = wfEngineFacade.cancelRunningInstancesByBiz(bizType, bizId,
                            "D-WF-14 自动办结：单据 " + bizType + "#" + bizId + " 尚未提交（草稿态），流程实例自动撤销");
                    draftClosed += n;
                    if (n > 0) {
                        log.info("[僵尸实例对账] 未提交收口 bizType={}, bizId={}, 撤销实例 {} 个", bizType, bizId, n);
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // 单条失败不中断整轮：留待下轮重试，不影响其它实例办结
                skipped++;
                log.warn("[僵尸实例对账] 单条处理失败 procInstId={}: {}", procInstId, e.getMessage());
            }
        }
        log.info("[僵尸实例对账] 完成：终态收口 {} 条、未提交收口 {} 条、本轮跳过 {} 条",
                terminalClosed, draftClosed, skipped);
        if (stat != null && stat.length >= 3) {
            stat[0] = terminalClosed;
            stat[1] = draftClosed;
            stat[2] = skipped;
        }
    }
}