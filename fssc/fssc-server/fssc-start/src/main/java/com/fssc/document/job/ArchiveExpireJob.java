// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.framework.datasource.FsscDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 档案保管到期预警（补齐 §八「到期预警 → 销毁双签 → DESTROYED」闭环首环）。
 *
 * <p>每日扫描保管期即将届满的档案（destroyDate 落在 [today, today+30] 且仍为 ARCHIVED），
 * 按租户聚合后经 {@link NotifyService} 推送站内信给档案管理员(FM_ARCHIVIST)，
 * 提醒其发起销毁审批或办理延期。{@code ArchiveService.listExpiringSoon(int)}
 * 虽已实现查询，但无定时任务、无推送、无前端入口，档案到期无人感知。
 *
 * <p>【去重】到期是持续状态（不同于"新增异常"这类一次性事件），故以
 * {@code 租户+日期} 为键记录已推送，保证同一天最多推一次，避免重启后重复轰炸。
 *
 * <p>【租户上下文】调度线程无 {@link TenantContext}，op_archive 为租户过滤表，
 * 直接查询会触发 fail-closed；故扫描用 {@code runIgnoreTenant}，推送用 {@code callAs(tenantId)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveExpireJob {

    /** 预警目标角色编码：档案管理员 */
    private static final String ALERT_ROLE = "FM_ARCHIVIST";

    /** 提前预警天数（destroyDate 落在该窗口内的档案纳入预警） */
    private static final int AHEAD_DAYS = 30;

    private final OpArchiveMapper archiveMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;

    /** 已推送标记（key = tenantId + ":" + date，进程内防重复） */
    private final Set<String> pushed = ConcurrentHashMap.newKeySet();

    /** 每日 02:30 执行 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void scanAndAlert() {
        jobHeartbeatService.run("ArchiveExpireJob", () -> {
        try {
            LocalDate today = LocalDate.now();
            LocalDate until = today.plusDays(AHEAD_DAYS);
            List<OpArchive>[] holder = new List[1];
            TenantContext.runIgnoreTenant(() ->
                    holder[0] = archiveMapper.selectList(new LambdaQueryWrapper<OpArchive>()
                            .eq(OpArchive::getArchiveStatus, "ARCHIVED")
                            .isNotNull(OpArchive::getDestroyDate)
                            // 只设上界：destroyDate <= today+30 全部纳入。
                            // 不设下界是刻意为之——已到期但仍未销毁的档案恰恰最该预警，
                            // 若加 ge(today) 会把它们排除，导致"已到期"统计恒为 0。
                            .le(OpArchive::getDestroyDate, until)
                            .orderByAsc(OpArchive::getDestroyDate)));
            List<OpArchive> expiring = holder[0];
            if (expiring == null || expiring.isEmpty()) {
                return;
            }
            Map<Long, List<OpArchive>> byTenant = expiring.stream()
                    .filter(a -> a.getTenantId() != null)
                    .collect(Collectors.groupingBy(OpArchive::getTenantId));
            log.info("[档案到期预警] 扫描到 {} 卷即将到期档案，涉及 {} 个租户",
                    expiring.size(), byTenant.size());

            for (Map.Entry<Long, List<OpArchive>> e : byTenant.entrySet()) {
                Long tenantId = e.getKey();
                String key = tenantId + ":" + today;
                if (!pushed.add(key)) {
                    continue;
                }
                try {
                    TenantContext.callAs(tenantId, () -> push(tenantId, e.getValue(), today));
                } catch (Exception ex) {
                    log.error("[档案到期预警] 租户 {} 推送失败", tenantId, ex);
                    pushed.remove(key); // 推送失败允许下次重试
                }
            }
        } catch (Exception ex) {
            log.error("[档案到期预警] 扫描异常", ex);
        }
        });
    }

    /** 组装预警内容并推送至档案管理员（该角色无用户时回退租户全部激活用户） */
    private void push(Long tenantId, List<OpArchive> archives, LocalDate today) {
        long expired = archives.stream()
                .filter(a -> !a.getDestroyDate().isAfter(today))
                .count();
        StringBuilder content = new StringBuilder("未来 ")
                .append(AHEAD_DAYS).append(" 天内有 ").append(archives.size())
                .append(" 卷档案保管期届满（其中已到期 ").append(expired).append(" 卷）：\n");
        int shown = 0;
        for (OpArchive a : archives) {
            if (shown++ >= 20) {
                content.append("  · ……等共 ").append(archives.size()).append(" 卷\n");
                break;
            }
            content.append("  · ").append(a.getArchiveNo() == null ? a.getArchiveCode() : a.getArchiveNo())
                    .append(" 到期日 ").append(a.getDestroyDate())
                    .append(" 保管 ").append(a.getRetentionYears() == null ? "-" : a.getRetentionYears())
                    .append(" 年\n");
        }
        content.append("请及时发起销毁审批或办理延期（档案管理 → 到期预警）。");

        List<Long> targets = archivistUsers(tenantId);
        if (targets.isEmpty()) {
            log.warn("[档案到期预警] 租户 {} 无 FM_ARCHIVIST 角色用户，跳过站内信推送", tenantId);
            return;
        }
        for (Long uid : targets) {
            notifyService.notifyUser(uid, "ARCHIVE_EXPIRE", "档案保管到期预警", content.toString(), null, null);
        }
        log.info("[档案到期预警] 租户 {} 已推送站内信给 {} 位档案管理员", tenantId, targets.size());
    }

    /** 解析档案管理员角色用户；该角色无用户时回退租户全部激活用户 */
    private List<Long> archivistUsers(Long tenantId) {
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
