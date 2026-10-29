// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfDelegate;
import com.fssc.workflow.mapper.WfDelegateMapper;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 委托代理到期自动回收（方案 §1.4-1，2026-09-23 补）。
 *
 * <p><b>为什么需要它</b>：{@code wf_delegate} 的状态列只有 {@code NORMAL}（启用）与
 * {@code DISABLED}（人工取消）两个写入位置——<b>没有任何代码路径会写 {@code EXPIRED}</b>。
 *
 * <p>功能上"到期即失效"其实已经成立：两处生效判定
 * （{@code WfDelegateService.activeDelegateTo} / {@code NodeOrgResolver.activeDelegateFromUserIds}）
 * 都用 SQL 的 {@code begin_time <= now <= end_time}，过期行自然查不出来。**但状态列永远停在 NORMAL**，
 * 于是：
 * <ol>
 *   <li>前端「我的委托代理」页永远显示「启用」——而 {@code MyDelegate.vue} 早已写好
 *       {@code EXPIRED → 「已过期」} 的标签分支，属于"前端就绪、后端不产出"的镜像缺口；</li>
 *   <li>运维/审计按状态列盘点时，把"早已失效"的行算成"生效中"，规则与实际不符；</li>
 *   <li>用户看不到"什么时候失效的"，只能靠肉眼比对时间区间。</li>
 * </ol>
 *
 * <p>本 Job 每 10 分钟把 {@code status=NORMAL 且 end_time < now} 的行置为 {@code EXPIRED}，
 * 使状态列与真实生效面一致。<b>不改变任何功能行为</b>（过期行本就查不出），只修正规则与展示。
 *
 * <p><b>为何不给用户发通知</b>：到期是委托的**预期语义**（用户在建立委托时就选定了结束时间），
 * 逐个推送只会制造噪音；此处仅落日志 + 心跳记录。需要"到期提醒"属产品决策，如后续要加，
 * 建议改为"到期前 1 天提醒"而非"到期后告知"。
 *
 * <p><b>与超时 Job 的关系</b>：本 Job 的第二段（委托改派）确实会动 Flowable 的办理人，
 * 但只在 {@code wf.delegate.mode=REASSIGN} 且该流程在灰度清单内时；缺省（该键库里不存在）
 * 两段都短路返回，行为与改造前逐字一致。回跳/改派一律经引擎 {@code setAssignee} + 镜像同步 + 记录，
 * 绝不直写 {@code wf_task}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfDelegateExpireJob {

    private final WfDelegateMapper delegateMapper;
    private final JobHeartbeatService jobHeartbeatService;
    /** 委托到期时的待办回跳通道（与"取消委托"同一实现，避免两处规则漂移） */
    private final WfEngineFacade wfEngineFacade;
    /** 改派档是否启用（缺省关＝本 Job 的委托改派两段全部短路） */
    private final NodeOrgResolver nodeOrgResolver;

    /** 单轮回收上限：防御性上限，避免历史脏数据一次性全表回写 */
    private static final int BATCH_LIMIT = 2000;

    /** 10 分钟一轮（到期精度足够：生效判定本就是按时间比较，与状态列无关） */
    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    public void expireDelegates() {
        jobHeartbeatService.run("WfDelegateExpireJob", () -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                // 调度线程无租户上下文：先跨租户扫出到期行，再逐行回到它自己的租户更新
                List<WfDelegate> expired = ignoreTenant(() -> delegateMapper.selectList(
                        new LambdaQueryWrapper<WfDelegate>()
                                .eq(WfDelegate::getStatus, "NORMAL")
                                .lt(WfDelegate::getEndTime, now)
                                .orderByAsc(WfDelegate::getId)
                                .last("LIMIT " + BATCH_LIMIT)));
                if (expired == null || expired.isEmpty()) {
                    return;
                }
                Map<Long, Integer> byTenant = new HashMap<>();
                int ok = 0;
                for (WfDelegate d : expired) {
                    Long tenantId = d.getTenantId();
                    try {
                        // 条件更新：并发下用户可能刚把它取消（DISABLED），不能覆盖成 EXPIRED。
                        // TenantContext.callAs 只收 Runnable（无带返回值重载），故用单元素数组把结果带出来。
                        WfDelegate upd = new WfDelegate();
                        upd.setStatus("EXPIRED");
                        Boolean[] changed = new Boolean[1];
                        TenantContext.callAs(tenantId, () -> changed[0] = delegateMapper.update(upd,
                                new LambdaUpdateWrapper<WfDelegate>()
                                        .eq(WfDelegate::getId, d.getId())
                                        .eq(WfDelegate::getStatus, "NORMAL")) > 0);
                        if (Boolean.TRUE.equals(changed[0])) {
                            ok++;
                            byTenant.merge(tenantId, 1, Integer::sum);
                            // 到期回跳：只在改派档下有意义（借资格档从未把单子移出委托人名下，无单可回）。
                            // 配置读取与待办改派都在该委托自己的租户里做——缺上下文会被 fail-closed 拦下，
                            // 表现就是"到期了单子却永久留在受托人待办里"。
                            int[] back = new int[1];
                            try {
                                TenantContext.callAs(tenantId, () -> {
                                    if (!nodeOrgResolver.delegateReassignActive()) {
                                        return;
                                    }
                                    // A3（人工裁决 RA-F4a 前置）：到期后未办任务是否返还原办理人＝参数控制，
                                    // 缺省返还原办理人（保守侧）；配成 KEEP 才留在受托人名下。
                                    if (!nodeOrgResolver.delegateRevertOnEndEnabled()) {
                                        log.info("委托到期按参数不回跳（KEEP）: delegate={} 未办任务留在受托人 {} 名下",
                                                d.getId(), d.getToUserId());
                                        return;
                                    }
                                    back[0] = wfEngineFacade.revertDelegatedTasks(
                                            d.getFromUserId(), d.getToUserId(), d.getBeginTime(), d.getEndTime());
                                });
                                if (back[0] > 0) {
                                    log.info("委托到期回跳: delegate={} {} <- {} 共 {} 条",
                                            d.getId(), d.getFromUserId(), d.getToUserId(), back[0]);
                                }
                            } catch (Exception ex) {
                                log.error("委托到期回跳失败 id={} tenant={}: {}", d.getId(), tenantId, ex.getMessage(), ex);
                            }
                        }
                    } catch (Exception ex) {
                        log.error("委托到期回收失败 id={} tenant={}", d.getId(), tenantId, ex);
                    }
                }
                if (ok > 0) {
                    log.info("委托到期自动回收：扫描到 {} 条到期未回收，成功置 EXPIRED {} 条，按租户={}",
                            expired.size(), ok, byTenant);
                }
                if (expired.size() >= BATCH_LIMIT) {
                    log.warn("委托到期回收本轮触及上限 {} 条，剩余将在下一轮继续", BATCH_LIMIT);
                }
            } catch (Exception ex) {
                log.error("委托到期回收扫描异常", ex);
            }
        });
    }

    /**
     * 委托窗口开启时的历史改派扫描（的第二段）。
     *
     * <p><b>为什么需要</b>：委托可以在窗口尚未开始时就建立（例如 06:47 建、09-27 00:00 才开始生效）。
     * 只在"建立委托那一刻"改派会漏掉这类预约——窗口开了一直没人把历史待办转过去，
     * 表现正是体检 E-07 的"承诺自动转办、实际永远不转"。本扫描每 10 分钟把已开窗口内的委托各补做一次。
     *
     * <p>改派天然去重：一条待办被转走后其 assignee 就不再等于委托人，下一轮查不出来＝不会二次改派。
     *
     * <p>缺省（{@code wf.delegate.mode} 不存在或非 REASSIGN）时，每行都在租户内被
     * {@link NodeOrgResolver#delegateReassignActive()} 短路，
     * 不查引擎、不动待办，与改造前行为一致。
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 180_000)
    public void activateDelegatedWindows() {
        jobHeartbeatService.run("WfDelegateTakeoverSweep", () -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                List<WfDelegate> open = ignoreTenant(() -> delegateMapper.selectList(
                        new LambdaQueryWrapper<WfDelegate>()
                                .eq(WfDelegate::getStatus, "NORMAL")
                                .le(WfDelegate::getBeginTime, now)
                                .ge(WfDelegate::getEndTime, now)
                                .orderByAsc(WfDelegate::getId)
                                .last("LIMIT " + BATCH_LIMIT)));
                if (open == null || open.isEmpty()) {
                    return;
                }
                int moved = 0;
                for (WfDelegate d : open) {
                    int[] r = new int[1];
                    try {
                        TenantContext.callAs(d.getTenantId(), () -> {
                            if (!nodeOrgResolver.delegateReassignActive()) {
                                return;
                            }
                            r[0] = wfEngineFacade.takeOverDelegatedTasks(
                                    d.getFromUserId(), d.getToUserId(), d.getId());
                        });
                    } catch (Exception ex) {
                        log.error("委托窗口生效改派失败 id={} tenant={}: {}",
                                d.getId(), d.getTenantId(), ex.getMessage(), ex);
                        continue;
                    }
                    if (r[0] > 0) {
                        moved += r[0];
                        log.info("委托窗口生效改派: delegate={} {} -> {} 共 {} 条",
                                d.getId(), d.getFromUserId(), d.getToUserId(), r[0]);
                    }
                }
                if (moved > 0) {
                    log.info("委托窗口生效扫描：{} 条生效委托，本轮改派 {} 条存量待办", open.size(), moved);
                }
            } catch (Exception ex) {
                log.error("委托窗口生效扫描异常", ex);
            }
        });
    }

    /** 跨租户读取：{@code runIgnoreTenant} 只收 Runnable，用单元素数组把结果带出来（同 WfTimeoutJob）。 */
    private <T> T ignoreTenant(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T r = (T) holder[0];
        return r;
    }

    /** 供单测/运维手动触发时读取到期行（只读，不修改）。 */
    public List<WfDelegate> listExpired(LocalDateTime now) {
        List<WfDelegate> list = ignoreTenant(() -> delegateMapper.selectList(
                new LambdaQueryWrapper<WfDelegate>()
                        .eq(WfDelegate::getStatus, "NORMAL")
                        .lt(WfDelegate::getEndTime, now)));
        return list == null ? new ArrayList<>() : list;
    }
}
