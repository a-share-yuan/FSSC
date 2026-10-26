// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据冷热分离归档服务：把最终状态超期的报销单从 {@code exp_bill*} 热表整体搬到 {@code exp_bill*_cold}
 * 冷表，并落 {@code exp_cold_archive_log} 归档流水。
 *
 * <h3>租户上下文</h3>
 * 冷表不在 MyBatis-Plus 租户拦截器管辖内（{@code exp_bill_cold} 等新表未挂实体），故所有 SQL
 * 都走 {@link JdbcTemplate}，手工带 {@code tenant_id}；扫描走 {@code runIgnoreTenant} 语义（跨租户 GROUP BY），
 * 逐租户处理走 {@link TenantContext#callAs} 读 sys_config。
 *
 * <h3>安全网</h3>
 * 只搬 {@code archive.terminal_status} 集合内、且 status 不在 DRAFT/SUBMITTED/APPROVING 的三个在途态、
 * 且 {@code update_time < NOW() - INTERVAL archive.cold_months MONTH} 的单据；
 * 全局 {@code archive.enabled=0}（默认关）时 {@link #archive} 直接返回。
 * 任何一张单失败只回滚该单，继续下一张（每张单独立事务）。
 *
 * <h3>不动</h3>
 * inv_invoice / 凭证 / 档案等关联数据一律不搬——发票池与凭证独立生命周期，冷单历史追溯靠
 * {@code exp_cold_archive_log} + 凭证 {@code bill_id} 反查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdArchiveService {

    /** 归档月数阈值配置键 */
    private static final String CFG_COLD_MONTHS = "archive.cold_months";
    /** 归档最终状态集合配置键（逗号分隔） */
    private static final String CFG_TERMINAL_STATUS = "archive.terminal_status";
    /** 归档开关配置键（0=关，1=开） */
    private static final String CFG_ENABLED = "archive.enabled";

    private static final int DEFAULT_COLD_MONTHS = 12;
    private static final String DEFAULT_TERMINAL_STATUS = "POSTED,PAID,DONE,REPAID,CLOSED";
    /** 每租户每批最多搬多少张，防一次调度锁太多行 */
    private static final int BATCH_LIMIT = 200;
    /** 在途态安全网：即使被写进 terminal_status 也强拦，绝不搬在途单 */
    private static final String INFLIGHT_EXCLUDE = "('DRAFT','SUBMITTED','APPROVING')";

    private final JdbcTemplate jdbcTemplate;
    private final SysConfigMapper sysConfigMapper;
    private final PlatformTransactionManager txManager;
    /** 每张单一个独立事务：@RequiredArgsConstructor 不注入 TransactionTemplate，改由 @PostConstruct 现搭 */
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void initTx() {
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    // ---------------- dryRun ----------------

    /**
     * 按当前配置返回各租户"可搬"最终状态超期单据数（只 SELECT COUNT，不搬）。
     * 返回结构：{@code {total, tenants:[{tenantId,enabled,coldMonths,count}, ...]}}。
     */
    public Map<String, Object> dryRunCount() {
        List<Long> tenantIds = listCandidateTenants();
        List<Map<String, Object>> perTenant = new ArrayList<>();
        long total = 0L;
        for (Long tenantId : tenantIds) {
            int months = readConfigInt(tenantId, CFG_COLD_MONTHS, DEFAULT_COLD_MONTHS);
            String statusCsv = readConfigStr(tenantId, CFG_TERMINAL_STATUS, DEFAULT_TERMINAL_STATUS);
            int enabled = readConfigInt(tenantId, CFG_ENABLED, 0);
            Long cnt = countCandidates(tenantId, months, statusCsv);
            long c = cnt == null ? 0L : cnt;
            total += c;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantId", tenantId);
            row.put("enabled", enabled);
            row.put("coldMonths", months);
            row.put("count", c);
            perTenant.add(row);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", total);
        resp.put("tenants", perTenant);
        return resp;
    }

    /** 是否存在任一租户 enabled=1，用于 ColdArchiveJob 快速早退。 */
    public boolean hasAnyEnabled() {
        for (Long tenantId : listCandidateTenants()) {
            if (readConfigInt(tenantId, CFG_ENABLED, 0) == 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------- archive ----------------

    /**
     * 归档主流程：enabled=1 才执行；逐租户，每批 LIMIT 200，每张单独立事务。
     *
     * @param batchNo 本批次号（落到 exp_cold_archive_log.batch_no，用于人工/回溯定位）
     * @return {@code {moved, failed, counts:{line,pay,attach,rel,offset}}}
     */
    public Map<String, Object> archive(String batchNo) {
        int moved = 0;
        int failed = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : new String[]{"line", "pay", "attach", "rel", "offset"}) {
            counts.put(k, 0);
        }
        for (Long tenantId : listCandidateTenants()) {
            if (readConfigInt(tenantId, CFG_ENABLED, 0) != 1) {
                continue;
            }
            int months = readConfigInt(tenantId, CFG_COLD_MONTHS, DEFAULT_COLD_MONTHS);
            String statusCsv = readConfigStr(tenantId, CFG_TERMINAL_STATUS, DEFAULT_TERMINAL_STATUS);
            List<Map<String, Object>> candidates = listCandidates(tenantId, months, statusCsv);
            if (candidates.isEmpty()) {
                continue;
            }
            log.info("[冷热归档] 租户 {} 本批候选 {} 张（batch={}）", tenantId, candidates.size(), batchNo);
            for (Map<String, Object> cand : candidates) {
                long billId = ((Number) cand.get("id")).longValue();
                String billNo = (String) cand.get("bill_no");
                String billType = (String) cand.get("bill_type");
                try {
                    Map<String, Integer> c = archiveOne(tenantId, billId, billNo, billType, batchNo);
                    moved++;
                    for (String k : c.keySet()) {
                        counts.merge(k, c.get(k), Integer::sum);
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("[冷热归档] 单据搬移失败 tenant={} billId={} billNo={}: {}",
                            tenantId, billId, billNo, e.getMessage(), e);
                    // 单张失败仅回滚本单事务（archiveOne 内部 TransactionTemplate 已回滚），
                    // 继续下一张；inv_invoice/凭证/档案不搬（独立生命周期）
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("moved", moved);
        summary.put("failed", failed);
        summary.put("counts", counts);
        log.info("[冷热归档] 完成 batch={} moved={} failed={} counts={}", batchNo, moved, failed, counts);
        return summary;
    }

    /** 单张单据搬移事务：子表→冷 → 表头→冷 → 删热 → 写 log。任一 SQL 异常整单回滚。 */
    private Map<String, Integer> archiveOne(long tenantId, long billId, String billNo, String billType, String batchNo) {
        return transactionTemplate.execute(status -> {
            // ① 计数（供 log 与后续对账）
            int lineCnt = countChild("exp_bill_line", tenantId, billId);
            int payCnt = countChild("exp_bill_payment", tenantId, billId);
            int attachCnt = countChild("exp_bill_attach", tenantId, billId);
            int relCnt = intOf(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM exp_bill_relation WHERE tenant_id=? AND (source_bill_id=? OR target_bill_id=?)",
                    Long.class, tenantId, billId, billId));
            int offsetCnt = countChild("exp_loan_offset", tenantId, billId);

            // ② 冷表插入（archived_at 走 NOW()；列顺序 = 原表全列 + 末尾 archived_at）
            jdbcTemplate.update(
                    "INSERT INTO exp_bill_cold SELECT *, NOW() FROM exp_bill WHERE tenant_id=? AND id=?",
                    tenantId, billId);
            insertChildCold("exp_bill_line", tenantId, billId);
            insertChildCold("exp_bill_payment", tenantId, billId);
            insertChildCold("exp_bill_attach", tenantId, billId);
            jdbcTemplate.update(
                    "INSERT INTO exp_bill_relation_cold SELECT *, NOW() FROM exp_bill_relation"
                            + " WHERE tenant_id=? AND (source_bill_id=? OR target_bill_id=?)",
                    tenantId, billId, billId);
            insertChildCold("exp_loan_offset", tenantId, billId);

            // ③ 删热表（子先、头后）
            jdbcTemplate.update("DELETE FROM exp_bill_line WHERE tenant_id=? AND bill_id=?", tenantId, billId);
            jdbcTemplate.update("DELETE FROM exp_bill_payment WHERE tenant_id=? AND bill_id=?", tenantId, billId);
            jdbcTemplate.update("DELETE FROM exp_bill_attach WHERE tenant_id=? AND bill_id=?", tenantId, billId);
            jdbcTemplate.update(
                    "DELETE FROM exp_bill_relation WHERE tenant_id=? AND (source_bill_id=? OR target_bill_id=?)",
                    tenantId, billId, billId);
            jdbcTemplate.update("DELETE FROM exp_loan_offset WHERE tenant_id=? AND bill_id=?", tenantId, billId);
            jdbcTemplate.update("DELETE FROM exp_bill WHERE tenant_id=? AND id=?", tenantId, billId);

            // ④ 归档流水（跨租户运维表，不挂实体，手工带 tenant_id）
            jdbcTemplate.update(
                    "INSERT INTO exp_cold_archive_log"
                            + " (tenant_id,bill_id,bill_no,bill_type,moved_at,line_cnt,pay_cnt,attach_cnt,rel_cnt,offset_cnt,batch_no)"
                            + " VALUES (?,?,?,?,NOW(),?,?,?,?,?,?)",
                    tenantId, billId, billNo, billType, lineCnt, payCnt, attachCnt, relCnt, offsetCnt, batchNo);

            Map<String, Integer> r = new LinkedHashMap<>();
            r.put("line", lineCnt);
            r.put("pay", payCnt);
            r.put("attach", attachCnt);
            r.put("rel", relCnt);
            r.put("offset", offsetCnt);
            return r;
        });
    }

    // ---------------- 冷表查询（Controller 用） ----------------

    /** 冷单据分页：手工带 tenant_id，走 JdbcTemplate（冷表不在租户拦截器管辖）。 */
    public PageResult<Map<String, Object>> coldPage(long pageNum, long pageSize, String billNo,
                                                    Long applyUserId, String beginDate, String endDate) {
        long tenantId = TenantContext.getTenantId();
        StringBuilder where = new StringBuilder(" WHERE tenant_id=? AND del_flag=0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (billNo != null && !billNo.isBlank()) {
            where.append(" AND bill_no LIKE ?");
            args.add("%" + billNo.trim() + "%");
        }
        if (applyUserId != null) {
            where.append(" AND apply_user_id=?");
            args.add(applyUserId);
        }
        if (beginDate != null && !beginDate.isBlank()) {
            where.append(" AND apply_date>=?");
            args.add(beginDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" AND apply_date<=?");
            args.add(endDate);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exp_bill_cold" + where, Long.class, args.toArray());
        long totalCnt = total == null ? 0L : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(Math.max(0L, (pageNum - 1) * pageSize));
        pageArgs.add(pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, bill_no, bill_type, bill_sub_type, title, total_amount, currency_code,"
                        + " status, apply_user_id, apply_user_name, apply_date, voucher_no, archived_at"
                        + " FROM exp_bill_cold" + where + " ORDER BY archived_at DESC, id DESC LIMIT ?, ?",
                pageArgs.toArray());
        return PageResult.of(rows, totalCnt, pageNum, pageSize);
    }

    /** 冷单据详情：冷头 + 5 张冷子表（Map 列表）。 */
    public Map<String, Object> coldDetail(Long billId) {
        long tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> head = jdbcTemplate.queryForList(
                "SELECT * FROM exp_bill_cold WHERE tenant_id=? AND id=?", tenantId, billId);
        if (head.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "历史单据不存在或尚未归档: " + billId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bill", head.get(0));
        result.put("lines", jdbcTemplate.queryForList(
                "SELECT * FROM exp_bill_line_cold WHERE tenant_id=? AND bill_id=? ORDER BY line_no", tenantId, billId));
        result.put("payments", jdbcTemplate.queryForList(
                "SELECT * FROM exp_bill_payment_cold WHERE tenant_id=? AND bill_id=? ORDER BY line_no", tenantId, billId));
        result.put("attaches", jdbcTemplate.queryForList(
                "SELECT * FROM exp_bill_attach_cold WHERE tenant_id=? AND bill_id=? ORDER BY sort", tenantId, billId));
        result.put("relations", jdbcTemplate.queryForList(
                "SELECT * FROM exp_bill_relation_cold WHERE tenant_id=? AND (source_bill_id=? OR target_bill_id=?)",
                tenantId, billId, billId));
        result.put("loanOffsets", jdbcTemplate.queryForList(
                "SELECT * FROM exp_loan_offset_cold WHERE tenant_id=? AND bill_id=?", tenantId, billId));
        return result;
    }

    // ---------------- 内部工具 ----------------

    /** 存在任意非删除单据的租户列表（用于跨租户扫描） */
    private List<Long> listCandidateTenants() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT tenant_id FROM exp_bill WHERE del_flag=0 ORDER BY tenant_id", Long.class);
    }

    private Long countCandidates(long tenantId, int months, String statusCsv) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String ph = inClause(statusCsv, args);
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exp_bill WHERE tenant_id=? AND del_flag=0"
                        + " AND status NOT IN " + INFLIGHT_EXCLUDE
                        + " AND status IN (" + ph + ")"
                        + " AND update_time < DATE_SUB(NOW(), INTERVAL " + months + " MONTH)",
                Long.class, args.toArray());
    }

    private List<Map<String, Object>> listCandidates(long tenantId, int months, String statusCsv) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String ph = inClause(statusCsv, args);
        return jdbcTemplate.queryForList(
                "SELECT id, bill_no, bill_type FROM exp_bill WHERE tenant_id=? AND del_flag=0"
                        + " AND status NOT IN " + INFLIGHT_EXCLUDE
                        + " AND status IN (" + ph + ")"
                        + " AND update_time < DATE_SUB(NOW(), INTERVAL " + months + " MONTH)"
                        + " ORDER BY id ASC LIMIT " + BATCH_LIMIT,
                args.toArray());
    }

    private int countChild(String table, long tenantId, long billId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND bill_id=?",
                Long.class, tenantId, billId);
        return intOf(c);
    }

    private static int intOf(Long v) {
        return v == null ? 0 : v.intValue();
    }

    private void insertChildCold(String hotTable, long tenantId, long billId) {
        jdbcTemplate.update(
                "INSERT INTO " + hotTable + "_cold SELECT *, NOW() FROM " + hotTable
                        + " WHERE tenant_id=? AND bill_id=?", tenantId, billId);
    }

    /** 生成 CSV 的 ? 占位符，同时把值按序 add 到 out（防 SQL 注入） */
    private String inClause(String csv, List<Object> out) {
        String[] arr = csv.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
            out.add(arr[i].trim());
        }
        return sb.toString();
    }

    /** 租户级 int 配置读取（走 SysConfigMapper + callAs 恢复上下文，参照 NotifyService.getConfig） */
    private int readConfigInt(long tenantId, String key, int def) {
        final int[] holder = {def};
        try {
            TenantContext.callAs(tenantId, () -> {
                SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key).last("limit 1"));
                if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                    try {
                        holder[0] = Integer.parseInt(c.getConfigValue().trim());
                    } catch (NumberFormatException ignored) {
                        // 保持默认
                    }
                }
            });
        } catch (Exception e) {
            log.warn("[冷热归档] 读取配置失败 tenant={} key={}", tenantId, key, e);
        }
        return holder[0];
    }

    /** 租户级 String 配置读取（同上） */
    private String readConfigStr(long tenantId, String key, String def) {
        final String[] holder = {def};
        try {
            TenantContext.callAs(tenantId, () -> {
                SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key).last("limit 1"));
                if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                    holder[0] = c.getConfigValue();
                }
            });
        } catch (Exception e) {
            log.warn("[冷热归档] 读取配置失败 tenant={} key={}", tenantId, key, e);
        }
        return holder[0];
    }
}
