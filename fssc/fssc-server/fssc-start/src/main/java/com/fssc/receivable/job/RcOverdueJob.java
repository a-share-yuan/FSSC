// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.receivable.service.RcCollectTodoService;
import com.fssc.receivable.service.RcReceivableService;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应收逾期日终批处理（补齐 §4.1「账龄自动分期 + 逾期自动 OVERDUE 进催收池」闭环）。
 *
 * <p>链路：到期未结清应收 → 打卡 OVERDUE → 生成催收待办(rc_collect_todo，逾期即进池)
 * → 推站内信给催收专员(AR_COLLECTOR)。此前
 * {@link RcReceivableService#accrueOverdueBadDebt()} 与
 * {@link RcCollectTodoService#createOverdueTodos(int)} 虽已实现，但全项目无调度引用，
 * 只能靠人工调接口触发，导致下游账龄分期与坏账计提（依赖 OVERDUE 状态）实际未生效。
 *
 * <p>【租户上下文】定时任务跑在 Spring 调度线程，无 {@link TenantContext}，
 * 直接查 rc_receivable（租户过滤表）会触发 fail-closed 抛 TENANT_CONTEXT_MISSING。
 * 故扫描用 {@code runIgnoreTenant} 聚合（仅扫描不落库），逐租户处理时 {@code callAs(tenantId)} 恢复上下文。
 * 只处理"确实存在逾期应收"的租户，避免全租户空跑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RcOverdueJob {

    /** 告警目标角色编码：信贷/催收专员 */
    private static final String ALERT_ROLE = "AR_COLLECTOR";

    private final RcReceivableMapper receivableMapper;
    private final RcReceivableService receivableService;
    private final RcCollectTodoService collectTodoService;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;

    /**
     * 日终 01:20 执行（早于报表刷新 01:30，保证当日报表口径含最新逾期状态）。
     * <p>基准日＝系统当前日；补跑历史／未来账期走
     * {@link #markOverdueAndCollect(java.time.LocalDate)}（D-DEC-407）。
     */
    @Scheduled(cron = "0 20 1 * * ?")
    public void markOverdueAndCollect() {
        markOverdueAndCollect(null);
    }

    /**
     * 同一条链路的可指定基准日版本（D-DEC-407）：扫描、打卡、催收待办三步共用同一个
     * {@code baseline}，避免"扫描按 A 日、打卡按系统日"的两把尺子。
     *
     * @param asOfDate 跑批基准日；null＝系统当前日（与改造前逐字等价）
     */
    public void markOverdueAndCollect(LocalDate asOfDate) {
        final LocalDate baseline = asOfDate == null ? LocalDate.now() : asOfDate;
        jobHeartbeatService.run("RcOverdueJob", () -> {
        try {
            List<RcReceivable>[] holder = new List[1];
            TenantContext.runIgnoreTenant(() ->
                    holder[0] = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                            // D-DEC-408：本处过滤必须与 accrueOverdueBadDebt 的准入集同源，
                            // 否则"租户发现"仍只认 NONE，已确认（CONFIRMED）／开票派生（NORMAL）
                            // ／部分收款（PARTIAL）的逾期行根本进不了后续逐租户处理
                            .in(RcReceivable::getStatus, RcReceivableService.OVERDUE_MARKABLE_STATUSES)
                            .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                            .isNotNull(RcReceivable::getDueDate)
                            .lt(RcReceivable::getDueDate, baseline)));
            List<RcReceivable> overdue = holder[0];
            if (overdue == null || overdue.isEmpty()) {
                return;
            }
            Set<Long> tenantIds = overdue.stream()
                    .map(RcReceivable::getTenantId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            log.info("[应收逾期批处理] 基准日 {}，扫描到 {} 笔到期未结清应收，涉及 {} 个租户",
                    baseline, overdue.size(), tenantIds.size());

            for (Long tenantId : tenantIds) {
                try {
                    TenantContext.callAs(tenantId, () -> process(tenantId, baseline));
                } catch (Exception ex) {
                    log.error("[应收逾期批处理] 租户 {} 处理失败", tenantId, ex);
                }
            }
        } catch (Exception ex) {
            log.error("[应收逾期批处理] 扫描异常", ex);
        }
        });
    }

    /** 单租户：打卡 OVERDUE → 生成催收待办 → 推送催收专员（三步共用同一基准日） */
    private void process(Long tenantId, LocalDate baseline) {
        int marked = receivableService.accrueOverdueBadDebt(baseline);
        // days=0：逾期即进催收池（accrueOverdueBadDebt 只打卡 dueDate<基准日，故实际至少逾期 1 天）
        int created = collectTodoService.createOverdueTodos(0, baseline);
        if (marked == 0 && created == 0) {
            return;
        }
        log.info("[应收逾期批处理] 租户 {} 打卡逾期 {} 笔，新增催收待办 {} 条", tenantId, marked, created);

        List<Long> targets = collectorUsers(tenantId);
        if (targets.isEmpty()) {
            log.warn("[应收逾期批处理] 租户 {} 无 AR_COLLECTOR 角色用户，跳过站内信推送", tenantId);
            return;
        }
        String title = "应收逾期提醒";
        String content = "日终批处理完成：本次打卡逾期应收 " + marked + " 笔，新增催收待办 " + created
                + " 条。请及时跟进催收（应收管理 → 催收待办）。";
        for (Long uid : targets) {
            notifyService.notifyUser(uid, "RC_OVERDUE", title, content, null, null);
        }
        log.info("[应收逾期批处理] 租户 {} 已推送站内信给 {} 位催收专员", tenantId, targets.size());
    }

    /** 解析催收专员角色用户；该角色无用户时回退租户全部激活用户 */
    private List<Long> collectorUsers(Long tenantId) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, ALERT_ROLE)
                .eq(SysRole::getStatus, "NORMAL")
                .last("limit 1"));
        List<Long> uids = new ArrayList<>();
        if (role != null) {
            uids = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()))
                    .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
        }
        if (!uids.isEmpty()) {
            return uids;
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "NORMAL"))
                .stream().map(SysUser::getId).collect(Collectors.toList());
    }
}
