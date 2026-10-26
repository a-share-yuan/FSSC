// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.service.FxRevaluationService;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ④ 月末外币汇兑重估定时任务（CAS 19）。
 *
 * <p>每月 1 日 00:10 重估"昨天所在月"（即刚结束的会计期间）。定时线程无 {@link TenantContext}，
 * 故先用 {@code runIgnoreTenant} 跨租户扫描存在外币货币性项目的租户集合，再逐租户 {@code callAs} 执行重估，
 * 只处理"确有外币应收/应付/借款"的租户，避免全租户空跑。逻辑仿 {@code RcOverdueJob}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxRevalJob {

    private static final List<String> AP_BILL_TYPES = Arrays.asList("PAY", "CLAIM", "CLAIM_TRAVEL");
    private static final String HOME = "CNY";
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final ExpBillMapper expBillMapper;
    private final RcReceivableMapper rcReceivableMapper;
    private final FxRevaluationService fxRevaluationService;
    private final JobHeartbeatService jobHeartbeatService;

    /** 每月 1 日 00:10 重估上月。 */
    @Scheduled(cron = "0 10 1 * * ?")
    public void revalLastMonth() {
        jobHeartbeatService.run("FxRevalJob", () -> {
            try {
                String period = LocalDate.now().minusDays(1).format(YM);
                Set<Long> tenantIds = scanTenants();
                if (tenantIds.isEmpty()) {
                    log.info("[汇兑重估] 未发现存在外币货币性项目的租户，period={} 空跑", period);
                    return;
                }
                log.info("[汇兑重估] 期间 {}，涉及 {} 个租户", period, tenantIds.size());
                for (Long tenantId : tenantIds) {
                    try {
                        TenantContext.callAs(tenantId, () -> fxRevaluationService.revalMonth(period));
                    } catch (Exception ex) {
                        log.error("[汇兑重估] 租户 {} 重估失败 period={}", tenantId, period, ex);
                    }
                }
            } catch (Exception ex) {
                log.error("[汇兑重估] 跨租户扫描异常", ex);
            }
        });
    }

    /** 跨租户扫描：存在外币应付/未还外币借款/外币应收的租户集合。 */
    private Set<Long> scanTenants() {
        Set<Long> ids = new HashSet<>();
        TenantContext.runIgnoreTenant(() -> {
            List<ExpBill> apBills = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                    .select(ExpBill::getTenantId)
                    .in(ExpBill::getBillType, AP_BILL_TYPES)
                    .eq(ExpBill::getStatus, "APPROVED")
                    .gt(ExpBill::getPayableAmount, BigDecimal.ZERO));
            List<ExpBill> loans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                    .select(ExpBill::getTenantId)
                    .eq(ExpBill::getBillType, "LOAN")
                    .gt(ExpBill::getLoanBalance, BigDecimal.ZERO));
            List<RcReceivable> recv = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                    .select(RcReceivable::getTenantId)
                    .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                    .ne(RcReceivable::getCurrencyCode, HOME));
            ids.addAll(apBills.stream().map(ExpBill::getTenantId).filter(Objects::nonNull).collect(Collectors.toSet()));
            ids.addAll(loans.stream().map(ExpBill::getTenantId).filter(Objects::nonNull).collect(Collectors.toSet()));
            ids.addAll(recv.stream().map(RcReceivable::getTenantId).filter(Objects::nonNull).collect(Collectors.toSet()));
        });
        return ids;
    }
}
