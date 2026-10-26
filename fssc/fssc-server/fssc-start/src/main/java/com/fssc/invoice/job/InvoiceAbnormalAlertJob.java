// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 发票异常告警推送（需求 7 税务发票管理体验项）。
 * 每 5 分钟扫描异常发票（验真 FAILED 或 异常标记非 NORMAL：红冲/销方黑名单/作废），
 * 按租户聚合后通过 {@link NotifyService} 多渠道（站内信+企微+邮件）推送至税务经理(TAX_MANAGER)；
 * 该角色无用户时回退推送给租户全部激活用户，保证演示/冒烟可感知。
 *
 * <p>去重：已推送过的发票 id 记录进进程内 {@link #alerted}，跨调度不重复轰炸；
 * 仅对新增异常发票重新告警（重启后按需重推属预期）。
 *
 * <p>【租户上下文】定时任务跑在 Spring 调度线程，无 {@link TenantContext}。
 * inv_invoice/sys_role 为租户过滤表，直接查询会触发 fail-closed 抛 TENANT_CONTEXT_MISSING。
 * 故扫描用 {@code runIgnoreTenant} 聚合（仅扫描不写入数据库），逐租户推送时用 {@code callAs(tenantId)} 恢复上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceAbnormalAlertJob {

    /** 告警目标角色编码：税务经理 */
    private static final String ALERT_ROLE = "TAX_MANAGER";

    private final InvInvoiceMapper invoiceMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final NotifyService notifyService;
    private final JobHeartbeatService jobHeartbeatService;

    /** 已告警发票 id（进程内存活，防重复推送） */
    private final Map<Long, Boolean> alerted = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000) // 首次 30 秒后，之后每 5 分钟
    public void scanAndAlert() {
        jobHeartbeatService.run("InvoiceAbnormalAlertJob", () -> {
        try {
            // 跨租户扫描异常发票（仅扫描不写入数据库）
            List<InvInvoice>[] holder = new List[1];
            TenantContext.runIgnoreTenant(() ->
                    holder[0] = invoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                            .and(w -> w.eq(InvInvoice::getVerifyStatus, "FAILED")
                                    .or(inner -> inner.isNotNull(InvInvoice::getAnomalyFlag)
                                            .ne(InvInvoice::getAnomalyFlag, "NORMAL")))));
            List<InvInvoice> abnormal = holder[0];
            if (abnormal == null || abnormal.isEmpty()) {
                return;
            }
            // 过滤已告警；按租户分组
            List<InvInvoice> fresh = abnormal.stream()
                    .filter(inv -> !alerted.containsKey(inv.getId()))
                    .collect(Collectors.toList());
            if (fresh.isEmpty()) {
                return;
            }
            Map<Long, List<InvInvoice>> byTenant = fresh.stream()
                    .collect(Collectors.groupingBy(InvInvoice::getTenantId));
            log.info("[发票异常告警] 扫描到 {} 张新增异常发票，涉及 {} 个租户", fresh.size(), byTenant.size());

            for (Map.Entry<Long, List<InvInvoice>> e : byTenant.entrySet()) {
                Long tenantId = e.getKey();
                try {
                    TenantContext.callAs(tenantId, () -> push(tenantId, e.getValue()));
                    e.getValue().forEach(inv -> alerted.put(inv.getId(), Boolean.TRUE));
                } catch (Exception ex) {
                    log.error("[发票异常告警] 租户 {} 推送失败", tenantId, ex);
                }
            }
        } catch (Exception ex) {
            log.error("[发票异常告警] 扫描异常", ex);
        }
        });
    }

    /** 组装告警内容并多渠道推送至目标角色用户（无则回退租户全部激活用户） */
    private void push(Long tenantId, List<InvInvoice> invoices) {
        String title = "发票异常告警";
        StringBuilder content = new StringBuilder("本次扫描发现 ").append(invoices.size()).append(" 张异常发票：\n");
        for (InvInvoice inv : invoices) {
            content.append("  · ").append(inv.getInvoiceNo())
                    .append(" 金额").append(inv.getAmount())
                    .append(" 异常[").append(reason(inv)).append("]\n");
        }
        content.append("请及时处理（红冲/作废/补验真等）。");
        List<Long> targets = taxManagerUsers(tenantId);
        if (targets.isEmpty()) {
            log.warn("[发票异常告警] 租户 {} 无 TAX_MANAGER 角色用户，跳过站内信推送", tenantId);
            return;
        }
        for (Long uid : targets) {
            notifyService.notifyUser(uid, "ABNORMAL_INVOICE", title, content.toString(), tenantId, null);
        }
        log.info("[发票异常告警] 租户 {} 已推送站内信给 {} 位税务经理", tenantId, targets.size());
    }

    /** 解析异常原因 */
    private String reason(InvInvoice inv) {
        if ("FAILED".equals(inv.getVerifyStatus())) {
            return "验真失败" + (StringUtils.hasText(inv.getVerifyMsg()) ? "：" + inv.getVerifyMsg() : "");
        }
        String flag = inv.getAnomalyFlag();
        if ("RED_FLUSH".equals(flag)) return "已红冲";
        if ("SELLER_BLACK".equals(flag)) return "销方黑名单";
        if ("INVALID".equals(flag)) return "已作废";
        if ("DUP_GLOBAL".equals(flag)) return "跨企业重复";
        return flag == null ? "标记异常" : flag;
    }

    /** 解析税务经理角色用户；该角色无用户时回退租户全部激活用户 */
    private List<Long> taxManagerUsers(Long tenantId) {
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
        // 回退：租户全部激活用户
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "NORMAL"))
                .stream().map(SysUser::getId).collect(Collectors.toList());
    }
}