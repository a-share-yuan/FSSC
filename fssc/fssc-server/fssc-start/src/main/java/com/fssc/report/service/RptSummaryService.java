// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.report.entity.RptSummary;
import com.fssc.report.mapper.RptSummaryMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报表预聚合（需求文档 12.9）。
 * 按 租户+期间+维度（单据类型/公司/部门）预计算单据量与金额合计，替代实时 selectMaps 聚合。
 * 定时每日刷新近 3 个月 + 手动触发；查询读预聚合表，无数据回源实时聚合兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RptSummaryService {

    private final ExpBillMapper expBillMapper;
    private final RptSummaryMapper summaryMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /** 每日 01:30 刷新近 3 个月聚合。写动作显式回主库——只读副本不能写。 */
    @Scheduled(cron = "0 30 1 * * ?")
    @FsscDataSource("master")
    public void scheduledRefresh() {
        jobHeartbeatService.run("RptSummaryService", () -> {
            // 缺陷D6-3(20260919)：调度线程无租户上下文，aggregate 查 exp_bill 触发
            // fail-closed「业务SQL缺少租户条件」，任务每天 01:30 必刷 ERROR。
            // 报表预聚合是跨租户聚合（汇总各租户单据量/金额），用框架既定的 runIgnoreTenant。
            // (20261004)：runIgnoreTenant 只放开 SELECT 的租户过滤；写入必须按单据自带
            // tenant_id 显式赋值（见 aggregate/upsert），否则 rpt_summary.tenant_id 落 NULL 撞 NOT NULL。
            // 同批修复「假 SUCCESS」：原先此处 catch(Exception) 只 log.error，导致 JobHeartbeatService
            // 拿到正常返回→markSuccess，心跳页永远显示 SUCCESS（ERROR 行与
            // last_result=SUCCESS/duration_ms=16454 同时存在即为实证）。改为记日志后原样上抛，
            // 由心跳包装层 markFail＋告警，失败可见。
            TenantContext.runIgnoreTenant(() -> {
                LocalDate now = LocalDate.now();
                for (int i = 0; i < 3; i++) {
                    LocalDate d = now.minusMonths(i);
                    refresh(d.getYear(), d.getMonthValue());
                }
                log.info("报表预聚合定时刷新完成");
            });
        });
    }

    /** 手动刷新指定期间，返回写入条数。写动作显式回主库——只读副本不能写。 */
    @FsscDataSource("master")
    public int refresh(Integer year, Integer month) {
        String period = year + String.format("%02d", month);
        int total = 0;
        // 按单据类型
        total += aggregate(period, "EXPENSE_TYPE", "bill_type", year, month, null);
        // 按公司
        total += aggregate(period, "COMPANY", "company_id", year, month, "company_id");
        // 按部门
        total += aggregate(period, "DEPT", "dept_id", year, month, "dept_id");
        return total;
    }

    /**
     * 按维度聚合 exp_bill（排除草稿/删除）并 upsert rpt_summary。
     * groupField 为 null 时按空维度聚合（占位，前端主要用有值维度）。
     * <p>聚合与分组都带 tenant_id —— 跨租户刷新（runIgnoreTenant）时
     * 写入必须落回单据自己的租户，否则 rpt_summary.tenant_id=NULL 撞 NOT NULL 约束；
     * 同租户单条刷新（界面手动触发）时该列即当前上下文租户，行为不变。
     */
    private int aggregate(String period, String dimType, String groupField, Integer year, Integer month, String nameField) {
        QueryWrapper<ExpBill> qw = new QueryWrapper<ExpBill>()
                .select("tenant_id AS tenant_key", groupField + " AS dim_key", "COUNT(*) AS cnt", "SUM(total_amount) AS amt")
                .eq("period_year", year)
                .eq("period_month", month)
                .ne("status", "DRAFT")
                .isNotNull(groupField)
                .isNotNull("tenant_id")
                .groupBy("tenant_id", groupField);
        List<Map<String, Object>> rows = expBillMapper.selectMaps(qw);
        int count = 0;
        for (Map<String, Object> r : rows) {
            String dimKey = String.valueOf(r.get("dim_key"));
            Long cnt = ((Number) r.get("cnt")).longValue();
            BigDecimal amt = r.get("amt") == null ? BigDecimal.ZERO : (BigDecimal) r.get("amt");
            Object tk = r.get("tenant_key");
            Long tenantId = tk == null ? null : ((Number) tk).longValue();
            if (tenantId == null) {
                // 理论上不会发生（已 isNotNull 过滤）；宁可跳过也不要把 NULL 写进 NOT NULL 列
                log.warn("报表预聚合跳过无租户归属的聚合行 period={} dimType={} dimKey={}", period, dimType, dimKey);
                continue;
            }
            upsert(period, dimType, dimKey, null, cnt, amt, tenantId);
            count++;
        }
        return count;
    }

    private void upsert(String period, String dimType, String dimKey, String dimName, Long cnt, BigDecimal amt, Long tenantId) {
        // 命中判断必须同租户，否则 uk_rpt(tenant_id,period,dim_type,dim_key) 下会串改他租户汇总行
        RptSummary exist = summaryMapper.selectOne(new LambdaQueryWrapper<RptSummary>()
                .eq(RptSummary::getTenantId, tenantId)
                .eq(RptSummary::getPeriod, period)
                .eq(RptSummary::getDimType, dimType)
                .eq(RptSummary::getDimKey, dimKey)
                .last("limit 1"));
        if (exist != null) {
            RptSummary upd = new RptSummary();
            upd.setId(exist.getId());
            upd.setBillCount(cnt);
            upd.setTotalAmount(amt);
            upd.setRefreshTime(LocalDateTime.now());
            summaryMapper.updateById(upd);
        } else {
            RptSummary s = new RptSummary();
            // 显式赋租户：FsscMetaObjectHandler 在 ignore 模式下不填 tenantId（见其 insertFill 分支），
            // 自动填充指望不上，只能由聚合结果带入。
            s.setTenantId(tenantId);
            s.setPeriod(period);
            s.setDimType(dimType);
            s.setDimKey(dimKey);
            s.setDimName(dimName);
            s.setBillCount(cnt);
            s.setTotalAmount(amt);
            s.setRefreshTime(LocalDateTime.now());
            summaryMapper.insert(s);
        }
    }

    /** 查询预聚合（dimType 为空返回全部维度） */
    public List<RptSummary> query(String period, String dimType) {
        LambdaQueryWrapper<RptSummary> qw = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) {
            qw.eq(RptSummary::getPeriod, period);
        }
        if (dimType != null && !dimType.isBlank()) {
            qw.eq(RptSummary::getDimType, dimType);
        }
        qw.orderByAsc(RptSummary::getDimType).orderByDesc(RptSummary::getTotalAmount);
        return summaryMapper.selectList(qw);
    }

    /** 最近有聚合数据的期间列表 */
    public List<String> periods() {
        return summaryMapper.selectList(new LambdaQueryWrapper<RptSummary>()
                        .select(RptSummary::getPeriod)
                        .groupBy(RptSummary::getPeriod)
                        .orderByDesc(RptSummary::getPeriod))
                .stream().map(RptSummary::getPeriod).distinct().toList();
    }
}
