// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.mapper.TaxDeclarationMapper;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 纳税申报到期提醒。
 *
 * <p>改造前 {@code tax_declaration.pay_deadline} 与 {@code attachment_url} 两列全仓零读写，
 * 申报没有"税期"概念、更没有到期提醒——税期一过就是滞纳金，属"系统不提醒＝人必须记得"的典型。
 *
 * <p>本作业每天扫一次：缴款截止日在 <b>未来 {@value #AHEAD_DAYS} 天内</b>且尚未办结
 * （DRAFT/PENDING/APPROVED/FILED 且未缴）的申报单，按租户聚合推送税务经理（TAX_MANAGER）。
 *
 * <p>【租户上下文】定时线程无 {@link TenantContext}：扫描段用 {@code runIgnoreTenant}（只读），
 * 推送段逐租户 {@code callAs} 回本租户——与 {@code InvoiceAbnormalAlertJob} 同一做法。
 *
 * <p>去重：同一 (申报单, 截止日) 只在当天提醒一次，进程内记住键，重启后重推属预期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxDeclarationDueJob {

    /** 提前提醒天数 */
    private static final int AHEAD_DAYS = 3;

    private final TaxDeclarationMapper declarationMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;

    @Scheduled(cron = "0 10 9 * * ?") // 每天 09:10
    public void scanAndRemind() {
        jobHeartbeatService.run("TaxDeclarationDueJob", () -> {
            try {
                LocalDate today = LocalDate.now();
                LocalDate until = today.plusDays(AHEAD_DAYS);
                List<TaxDeclaration>[] holder = new List[1];
                TenantContext.runIgnoreTenant(() ->
                        holder[0] = declarationMapper.selectList(new LambdaQueryWrapper<TaxDeclaration>()
                                .isNotNull(TaxDeclaration::getPayDeadline)
                                .between(TaxDeclaration::getPayDeadline, today, until)
                                .in(TaxDeclaration::getStatus, "DRAFT", "PENDING", "APPROVED", "FILED")));
                List<TaxDeclaration> due = holder[0];
                if (due == null || due.isEmpty()) {
                    return;
                }
                Map<Long, List<TaxDeclaration>> byTenant = due.stream()
                        .filter(d -> d.getTenantId() != null)
                        .collect(Collectors.groupingBy(TaxDeclaration::getTenantId));
                log.info("[税金到期提醒] 扫描到 {} 张临近/到期申报单，涉及 {} 个租户", due.size(), byTenant.size());
                for (Map.Entry<Long, List<TaxDeclaration>> e : byTenant.entrySet()) {
                    try {
                        TenantContext.callAs(e.getKey(), () -> push(e.getValue()));
                    } catch (Exception ex) {
                        log.error("[税金到期提醒] 租户 {} 推送失败", e.getKey(), ex);
                    }
                }
            } catch (Exception ex) {
                log.error("[税金到期提醒] 扫描异常", ex);
            }
        });
    }

    private void push(List<TaxDeclaration> rows) {
        List<Long> targets = taxManagerUsers();
        if (targets.isEmpty()) {
            log.warn("[税金到期提醒] 本租户无 TAX_MANAGER 角色用户，跳过推送（{} 张待办）", rows.size());
            return;
        }
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("以下纳税申报即将/已到缴款截止日：\n");
        for (TaxDeclaration d : rows) {
            long days = ChronoUnit.DAYS.between(today, d.getPayDeadline());
            sb.append("  · ").append(d.getDeclarationNo())
                    .append(" 属期").append(d.getPeriod())
                    .append(" ").append(d.getTaxType())
                    .append(" 应纳").append(d.getTaxAmount())
                    .append(" 截止").append(d.getPayDeadline())
                    .append(days >= 0 ? "（剩 " + days + " 天）" : "（已逾期 " + (-days) + " 天）")
                    .append(" 状态").append(d.getStatus())
                    .append("\n");
        }
        sb.append("请及时确认申报并走对公付款（付款单填「缴纳税种/税期」），逾期将产生滞纳金。");
        for (Long uid : targets) {
            notifyService.notifyUser(uid, "TAX_DECLARATION_DUE", "纳税申报到期提醒", sb.toString(), null, null);
        }
        log.info("[税金到期提醒] 已推送 {} 位税务经理，涉及申报单 {} 张", targets.size(), rows.size());
    }

    /** 税务经理角色用户；无该角色时不回退到全员（税金事项群发会变成噪声，宁可只落日志） */
    private List<Long> taxManagerUsers() {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "TAX_MANAGER")
                .eq(SysRole::getStatus, "NORMAL")
                .last("limit 1"));
        if (role == null) {
            return new ArrayList<>();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId()))
                .stream().map(SysUserRole::getUserId).distinct().collect(Collectors.toList());
    }
}
