// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.purchase.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P0-5 采购订单终态推进 Job（X-20 裁定甲＋乙之「甲」）。
 *
 * <p>累计量达标型终态（APPROVED/INVOICED→PAID、PAID→CLOSED 业务关闭）不由人工按钮触发，
 * 改为本 Job 每日扫描推进；人工决策型终态（终止）另有显式端点
 * {@code POST /api/tenant/purchase/order/{id}/terminate}。
 *
 * <p>ARCHIVED 属归档域出口（document/**），本 Job 不越界推进。
 * 幂等：跃迁原判据（状态前置 + 绝对量比较）保持不变，重跑只可能 0 推进，不会叠加。
 * 多租户：无登录上下文，先跨租户取待办租户集，再逐租户 {@code callAs} 执行（租户过滤由拦截器接管）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoOrderFinalStateJob {

    private final PurchaseService purchaseService;
    private final PoOrderMapper orderMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /** 每日 03:10 执行（避开 02:xx 折旧/对账/归档批处理高峰） */
    @Scheduled(cron = "0 10 3 * * ?")
    public void advanceFinalStates() {
        jobHeartbeatService.run("PoOrderFinalStateJob", () -> {
            try {
                List<Long> tenantIds = new ArrayList<>();
                TenantContext.runIgnoreTenant(() -> {
                    List<PoOrder> rows = orderMapper.selectList(new LambdaQueryWrapper<PoOrder>()
                            .select(PoOrder::getTenantId)
                            .eq(PoOrder::getDelFlag, 0)
                            .in(PoOrder::getStatus, "APPROVED", "INVOICED", "PAID"));
                    tenantIds.addAll(rows.stream().map(PoOrder::getTenantId)
                            .filter(Objects::nonNull).distinct().toList());
                });
                if (tenantIds.isEmpty()) {
                    log.info("[采购终态推进] 无 APPROVED/INVOICED/PAID 订单，跳过");
                    return;
                }
                for (Long tenantId : tenantIds) {
                    try {
                        TenantContext.callAs(tenantId, () -> purchaseService.autoAdvanceFinalStates());
                    } catch (Exception ex) {
                        log.error("[采购终态推进] 租户 {} 推进失败", tenantId, ex);
                    }
                }
            } catch (Exception ex) {
                log.error("[采购终态推进] 扫描异常", ex);
            }
        });
    }
}
