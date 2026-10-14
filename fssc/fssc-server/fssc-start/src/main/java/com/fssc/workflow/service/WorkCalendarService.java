// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.fssc.framework.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G2 第 3 条「工作日历」：按共享工作日历把「自然小时」换算成「工作小时」的到期时间。
 *
 * <p><b>为什么要单独一个服务</b>：{@code due_time} 要成为真值（方案 §二 F6），而 SLA 是业务时限、
 * 不是墙钟时限——跨周末/节假日排期必须按工作日历扣减。此前 {@code TaskTimeLimitService} 按自然小时算、
 * 边界定时器按 {@code PT{N}H} 算，两处口径各自漂移。本类把"从某时刻起 +N 个工作小时"收敛为唯一实现，
 * 供 {@link TaskTimeLimitService} 与 BPMN 边界定时器共用。
 *
 * <p><b>日历来源与真实列（不臆造）</b>：{@code op_calendar}，列定义取自
 * {@code fssc/db/Dump20260911/fssc_op_calendar.sql}：
 * {@code tenant_id / cal_date / day_type(WORKDAY|REST|HOLIDAY) / work_start / work_end / remark}。
 * 组织列 {@code company_id} 由 {@code fssc/sql/V2.2.0_g2_calendar_org_20260924.sql} 新增
 * （组织维＝公司，依据见 {@code WfManageOrgScope} 类注释与 {@code NodeOrgResolver.companyIdOfBill}）。
 *
 * 租户默认行）"。组织列属<b>尚未执行的结构变更</b>，故本类<b>绝不无条件 SELECT 该列</b>，按下列顺序取数：
 * <ol>
 *   <li><b>先探测列是否存在</b>（查 {@code INFORMATION_SCHEMA.COLUMNS}，结果缓存在本进程）；
 *       列不存在 ⇒ 走<b>改造前逐字不变的旧 SQL</b>（见 {@link #SELECT_BASE_SQL}，仅按 {@code tenant_id} 取）；</li>
 *   <li>列存在且调用方给了 {@code companyId} ⇒ 取该公司专属行（{@code company_id = ?}）；
 *       <b>该公司本窗口内无专属行 ⇒ 回退 {@code company_id = 0} 的租户默认行</b>
 *       （存量行 company_id 全为 0，故这一回退与改造前结果逐字一致）；</li>
 *   <li>列存在但调用方拿不到组织（{@code companyId == null}）⇒ 仍取 {@code company_id = 0} 默认行；</li>
 *   <li>组织查询抛异常（如探测缓存与实际结构一度不一致）⇒ 捕获后回落旧 SQL 并纠正缓存，
 *       绝不把 Unknown column 抛给调用方。</li>
 * </ol>
 * 代价（如实说明）：① 探测为进程内缓存，脚本执行后需重启一次才切到组织口径（回滚后缓存仍为 true 的进程
 * 靠上述第 4 条兜底）；② 组织专属行与租户默认行需在"同一天"并存，故该表唯一键须由
 * {@code (tenant_id, cal_date)} 调整为 {@code (tenant_id, company_id, cal_date)}，详见该 SQL 脚本。
 * <b>哨兵取 0 而非 NULL</b>：MySQL 唯一索引视 NULL 互不相等，默认行若用 NULL 存，新键就拦不住"同一租户
 * 同一天插出多行默认日历"；该表写入口是纯 {@code insert()}（无 upsert 兜底），只能靠 0 哨兵保住数据库层
 * 唯一性。公司 id 自 1 起，0 不可能是真实公司，故不冲突。
 *
 * <p><b>缺省语义（零行为变化的前提）</b>：某天在日历里<b>没有行</b> ⇒ 视为正常工作日、用默认在岗时段
 * （08:30–17:30）。现网 {@code op_calendar} 只有 1 行（2026-10-01 节假日），若把"无行"当休息日，
 * 所有 SLA 都会无限后推——所以"无行＝工作日"是唯一安全缺省。
 */
@Slf4j
@Service
public class WorkCalendarService {

    /** 默认在岗时段（与 op_calendar 的列默认值一致） */
    static final String DEFAULT_WORK_START = "08:30";
    static final String DEFAULT_WORK_END = "17:30";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** 单次向前的最大推进天数：防止脏数据（如整年都是休息日）把调用方拖死 */
    private static final int MAX_SCAN_DAYS = 400;

    /**
     * 旧口径 SQL（改造前逐字原样保留）：仅按租户取。
     * 列不存在、拿不到组织、组织查询异常三种情形都走它，保证降级结果与改造前逐字一致。
     */
    private static final String SELECT_BASE_SQL =
            "SELECT `cal_date` AS d, `day_type` AS t, `work_start` AS s, `work_end` AS e "
                    + "FROM `op_calendar` WHERE `tenant_id` = ? AND `del_flag` = 0 "
                    + "AND `cal_date` BETWEEN ? AND ?";

    /** 组织专属行（company_id = ?）：需 company_id 列已建；参数顺序 tenantId、companyId、begin、end */
    private static final String SELECT_ORG_SQL =
            "SELECT `cal_date` AS d, `day_type` AS t, `work_start` AS s, `work_end` AS e "
                    + "FROM `op_calendar` WHERE `tenant_id` = ? AND `del_flag` = 0 "
                    + "AND `company_id` = ? AND `cal_date` BETWEEN ? AND ?";

    /**
     * 租户默认行（{@code company_id = 0} 哨兵，非 NULL；理由见类注释）：组织列已建、
     * 该公司无专属行时回退；参数顺序 tenantId、begin、end
     */
    private static final String SELECT_DEFAULT_SQL =
            "SELECT `cal_date` AS d, `day_type` AS t, `work_start` AS s, `work_end` AS e "
                    + "FROM `op_calendar` WHERE `tenant_id` = ? AND `del_flag` = 0 "
                    + "AND `company_id` = 0 AND `cal_date` BETWEEN ? AND ?";

    private final DataSource dataSource;

    /**
     * {@code op_calendar.company_id} 列是否已建（G2 第 3 条组织维）。三态：{@code null}=未探测、
     * {@code TRUE}=已建、{@code FALSE}=未建。进程内缓存：脚本执行后需重启一次才切到组织口径；
     * 回滚后缓存误置 TRUE 的情形由 {@link #loadCalendar} 的 try/catch 兜底（回落旧 SQL 并纠正缓存）。
     */
    private volatile Boolean companyColumnPresent;

    public WorkCalendarService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 从 {@code from} 起推进 {@code hours} 个<b>工作小时</b>后的时刻（租户口径，与改造前逐字一致）。
     *
     * @param hours 可为小数（如 4.5）；&lt;=0 或 from 为空时按"不推进"处理（返回 from 或 now）
     */
    public LocalDateTime plusWorkingHours(LocalDateTime from, double hours) {
        return plusWorkingHours(from, hours, null);
    }

    /**
     * 从 {@code from} 起推进 {@code hours} 个<b>工作小时</b>后的时刻（<b>按组织取日历</b>）。
     *
     * @param hours     可为小数（如 4.5）；&lt;=0 或 from 为空时按"不推进"处理（返回 from 或 now）
     * @param companyId 组织（公司）ID；{@code null} = 调用方拿不到组织，回落租户口径（与旧重载逐字一致）
     */
    public LocalDateTime plusWorkingHours(LocalDateTime from, double hours, Long companyId) {
        long minutes = Math.round(hours * 60.0);
        return plusWorkingMinutes(from, minutes, companyId);
    }

    /**
     * 从 {@code from} 起推进 {@code minutes} 个<b>工作分钟</b>后的时刻（租户口径，与改造前逐字一致）。
     *
     * <p>算法：逐日推进，落在工作日时取「在岗时段 ∩ [起点, 当日收工]」的有效分钟数扣减；
     * 扣完即返回该日的具体时刻。休息日/节假日整天跳过（贡献 0 分钟）。
     *
     * <p>兜底：超过 {@link #MAX_SCAN_DAYS} 天仍未扣完（脏日历），退化为自然分钟相加并在日志留痕
     * ——宁可给出一个偏晚的到期时间，也不返回空导致"永不超时"。
     */
    public LocalDateTime plusWorkingMinutes(LocalDateTime from, long minutes) {
        return plusWorkingMinutes(from, minutes, null);
    }

    /**
     * 从 {@code from} 起推进 {@code minutes} 个<b>工作分钟</b>后的时刻（<b>按组织取日历</b>）。
     *
     * @param companyId 组织（公司）ID；{@code null} = 调用方拿不到组织，回落租户口径（与旧重载逐字一致）
     * @see #plusWorkingMinutes(LocalDateTime, long)
     */
    public LocalDateTime plusWorkingMinutes(LocalDateTime from, long minutes, Long companyId) {
        LocalDateTime base = from == null ? LocalDateTime.now() : from;
        if (minutes <= 0) {
            return base;
        }
        Map<LocalDate, CalDay> cal;
        try {
            cal = loadCalendar(base.toLocalDate(), MAX_SCAN_DAYS, companyId);
        } catch (Exception ex) {
            log.warn("工作日历读取失败，按自然分钟兜底: {}", ex.getMessage());
            return base.plusMinutes(minutes);
        }
        long remaining = minutes;
        LocalDate day = base.toLocalDate();
        for (int i = 0; i <= MAX_SCAN_DAYS; i++) {
            CalDay d = cal.get(day);
            LocalTime start = d == null ? LocalTime.parse(DEFAULT_WORK_START, HM) : d.start();
            LocalTime end = d == null ? LocalTime.parse(DEFAULT_WORK_END, HM) : d.end();
            boolean working = d == null || d.working();
            if (working && end.isAfter(start)) {
                LocalDateTime windowStart = LocalDateTime.of(day, start);
                LocalDateTime windowEnd = LocalDateTime.of(day, end);
                if (base.isAfter(windowStart)) {
                    windowStart = base;
                }
                if (windowEnd.isAfter(windowStart)) {
                    long available = java.time.Duration.between(windowStart, windowEnd).toMinutes();
                    if (available >= remaining) {
                        return windowStart.plusMinutes(remaining);
                    }
                    remaining -= available;
                }
            }
            day = day.plusDays(1);
        }
        log.warn("工作日历推进超过 {} 天仍未扣完（{} 分钟），按自然分钟兜底", MAX_SCAN_DAYS, minutes);
        return base.plusMinutes(minutes);
    }

    /**
     * 读取 [from, from+days] 窗口内的日历行，键为日期（<b>按组织（公司）优先、可降级</b>）。
     *
     * <p>显式带 {@code tenant_id}：{@code op_calendar} 是既有 dump 表、<b>没有 MyBatis-Plus 实体</b>，
     * 租户拦截器无法替本查询注入条件，故这里显式过滤（非"手写租户过滤绕过拦截器"，而是"没有实体可拦"）。
     * 租户上下文缺失（如引擎线程未带上下文）时按"无日历"处理，退化为默认工作日。
     *
     * <p>取数顺序见类注释。关键点：<b>列不存在时用的 SQL 与改造前逐字一致</b>（{@link #SELECT_BASE_SQL}），
     * 故脚本未执行前本方法与改造前行为零差异。
     */
    private Map<LocalDate, CalDay> loadCalendar(LocalDate from, int days, Long companyId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new HashMap<>();
        }
        if (companyId == null || !companyColumnPresent()) {
            // 拿不到组织，或组织列尚未建（脚本未执行）⇒ 走改造前逐字不变的租户级 SQL
            return query(from, days, SELECT_BASE_SQL, tenantId, null);
        }
        try {
            Map<LocalDate, CalDay> org = query(from, days, SELECT_ORG_SQL, tenantId, companyId);
            if (!org.isEmpty()) {
                return org;
            }
            // 该组织本窗口内无专属日历 ⇒ 回退租户默认行（company_id = 0 哨兵）。
            // 存量行 company_id 全为 0，故这一回退与改造前"按租户取"结果逐字一致。
            return query(from, days, SELECT_DEFAULT_SQL, tenantId, null);
        } catch (Exception ex) {
            // 探测缓存为 true 但列实际不存在（如脚本已回滚）：捕获后回落旧 SQL 并纠正缓存，
            // 绝不把 Unknown column 抛给调用方（到期时间宁可退回租户口径，也不能整体失败）。
            log.warn("按组织取日历失败，回落租户口径: {}", ex.getMessage());
            companyColumnPresent = Boolean.FALSE;
            return query(from, days, SELECT_BASE_SQL, tenantId, null);
        }
    }

    /**
     * 执行一条日历查询并转为"日期→CalDay"映射。
     *
     * @param companyId 非空时 SQL 需含第二个占位符（{@code company_id = ?}）；为空时只有 tenantId/begin/end
     */
    private Map<LocalDate, CalDay> query(LocalDate from, int days, String sql, Long tenantId, Long companyId) {
        Map<LocalDate, CalDay> out = new HashMap<>();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        java.sql.Date begin = java.sql.Date.valueOf(from);
        java.sql.Date end = java.sql.Date.valueOf(from.plusDays(days));
        List<Map<String, Object>> rows = companyId == null
                ? jdbc.queryForList(sql, tenantId, begin, end)
                : jdbc.queryForList(sql, tenantId, companyId, begin, end);
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            LocalDate date = toDate(r.get("d"));
            if (date == null) {
                continue;
            }
            out.put(date, new CalDay(str(r.get("t")), str(r.get("s")), str(r.get("e"))));
        }
        return out;
    }

    /**
     * {@code op_calendar.company_id} 列是否已建（进程内缓存，双检锁只探测一次）。
     * 探测本身失败不缓存（返回 false 且下次重试），按"列不存在"回落旧租户口径。
     */
    private boolean companyColumnPresent() {
        Boolean cached = companyColumnPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (companyColumnPresent != null) {
                return companyColumnPresent;
            }
            Boolean probed = probeCompanyColumn();
            if (probed != null) {
                companyColumnPresent = probed;
            }
            return probed != null && probed;
        }
    }

    /** 查 INFORMATION_SCHEMA 判列是否存在；成功返回布尔，异常返回 {@code null}（不缓存，下次重试） */
    private Boolean probeCompanyColumn() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<Long> n = jdbc.queryForList(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'op_calendar' "
                            + "AND COLUMN_NAME = 'company_id'",
                    Long.class);
            return !n.isEmpty() && n.get(0) != null && n.get(0) > 0;
        } catch (Exception ex) {
            log.warn("探测 op_calendar.company_id 列失败，按列不存在（回落租户口径）处理: {}", ex.getMessage());
            return null;
        }
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof java.util.Date ud) {
            return ud.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        try {
            return o == null ? null : LocalDate.parse(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 一天的工作日历：是否工作日 + 在岗时段（解析失败一律回落默认时段，不让脏值把整天判成休息） */
    private record CalDay(String dayType, String workStart, String workEnd) {
        boolean working() {
            String t = dayType == null ? "" : dayType.trim().toUpperCase(java.util.Locale.ROOT);
            return !("REST".equals(t) || "HOLIDAY".equals(t));
        }

        LocalTime start() {
            return parse(workStart, DEFAULT_WORK_START);
        }

        LocalTime end() {
            return parse(workEnd, DEFAULT_WORK_END);
        }

        private static LocalTime parse(String v, String def) {
            try {
                return v == null || v.isBlank() ? LocalTime.parse(def, HM) : LocalTime.parse(v.trim(), HM);
            } catch (Exception e) {
                return LocalTime.parse(def, HM);
            }
        }
    }
}
