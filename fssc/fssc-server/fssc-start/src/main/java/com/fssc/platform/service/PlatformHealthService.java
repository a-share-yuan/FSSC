// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.service;

import com.fssc.framework.tenant.TenantContext;
import java.sql.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台健康看板：全租户维度的只读运营聚合。
 * 【平台端跨租户统计】所有查询在 {@link TenantContext#runIgnoreTenant} 下用 JdbcTemplate 聚合，
 * 严禁明细外泄，只回 COUNT / SUM / DISTINCT 等聚合结果。
 */
@Service
@RequiredArgsConstructor
public class PlatformHealthService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> dashboard() {
        final Map<String, Object> result = new LinkedHashMap<>();
        TenantContext.runIgnoreTenant(() -> {
            LocalDate today = LocalDate.now();

            // 租户概览（pf_tenant，平台表白名单，无需租户条件）
            result.put("totalTenants", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from pf_tenant where del_flag = 0"));
            result.put("normalTenants", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from pf_tenant where del_flag = 0 and status = 'NORMAL'"));
            result.put("frozenTenants", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from pf_tenant where del_flag = 0 and status = 'FROZEN'"));
            result.put("expiringSoon", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from pf_tenant where del_flag = 0 and status = 'NORMAL' "
                    + "and expire_time >= now() and expire_time <= date_add(now(), interval 30 day)"));

            // 活跃租户读数不启用：pf_tenant_stat 没有采集作业写入，
            // "近 7 天活跃租户" 会恒为 0——恒 0 的指标比不给更容易误导。
            // 用量采集作业上线之前，对外只报"尚未采集"。
            result.put("activeTenants7d", null);
            result.put("activeTenantsNote", "未启用：用量采集作业尚未上线，该读数不代表真实活跃度");

            // 今日操作量（跨租户 sys_oper_log 今日计数）
            result.put("todayOperCount", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from sys_oper_log where date(oper_time) = curdate()"));

            // 运行中流程实例（wf_instance status=RUNNING，等价 ACT_RU_EXECUTION 在途规则）
            result.put("runningInstances", count("/* tenant-ok: 平台端跨租户聚合计数，@PlatformOnly */ select count(*) from wf_instance where status = 'RUNNING'"));

            // 近 7 天每日：活跃租户数 + 单据增量（bill_count 日快照做差）
            LocalDate winFrom = today.minusDays(7);
            List<Map<String, Object>> daily = jdbcTemplate.queryForList(
                    // 只保留 bill_sum。active_tenants 依赖 pf_tenant_stat（0 行、无采集），
                    // 画出来是一条恒 0 的直线，会让人误判"没人用"。
                    "select stat_date, coalesce(sum(bill_count), 0) as bill_sum "
                            + "from pf_tenant_stat where stat_date >= ? and stat_date <= ? "
                            + "group by stat_date order by stat_date", winFrom, today);
            Map<LocalDate, long[]> byDate = new HashMap<>();
            for (Map<String, Object> r : daily) {
                byDate.put(toDate(r.get("stat_date")),
                        new long[]{-1L, asLong(r.get("bill_sum"))});
            }
            List<Map<String, Object>> series = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                long[] cur = byDate.getOrDefault(d, new long[]{0, 0});
                long[] prev = byDate.getOrDefault(d.minusDays(1), new long[]{0, 0});
                long delta = cur[1] - prev[1];
                if (delta < 0) {
                    delta = 0; // 快照回退（数据修正）不计负增量
                }
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", d.toString());
                point.put("activeTenants", null); // 未采集：给 null 而不是 0，前端显示「—」
                point.put("billDelta", delta);
                series.add(point);
            }
            result.put("daily7d", series);
        });
        return result;
    }

    // 工具

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    private static long asLong(Object v) {
        return v instanceof Number num ? num.longValue() : 0L;
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof Date sd) {
            return sd.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v));
    }
}
