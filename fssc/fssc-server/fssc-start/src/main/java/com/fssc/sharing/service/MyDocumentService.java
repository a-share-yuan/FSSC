// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.fssc.common.core.PageQuery;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.service.BillRouteRegistry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 「我的单据」全类型读侧聚合（办结）。
 *
 * <p>只查 {@code exp_bill} 时接口注释却写"全类型"⇒ 合同/采购/预算/总账/档案的驳回单在工作台里根本看不见，
 * 用户只能新建，越缺越建、越建越缺。
 * 这里按 {@link BillRouteRegistry} 的"bizType→业务主表→归属人列"逐表查询后内存归并，
 * 覆盖范围由注册表决定，不另立一份表清单。
 *
 * <h3>三条刻意的设计</h3>
 * <ol>
 *   <li><b>不支持就说不支持</b>：资金调拨/票据/银行对账/派工单等表既无 {@code create_by}
 *       也无报账人列（见 {@code BillRouteRegistry.TABLE_OWNER} 注释），这类域进
 *       {@code unsupported} 列表由前端明示"请到 XX 台账查看"，而不是让"全类型"的承诺静默少数据。</li>
 *   <li>每表一条 SQL + LIMIT，内存归并：跨 26 张业务主表做 selectPage 会退化成深翻；
 *       每表取前 {@code offset+pageSize}（封顶 {@link #PER_TABLE_CAP}）再归并，
 *       与待办回填 {@code WfTaskController.fillBillInfoBatch()} 同一路数。
 *       total 用一条 UNION ALL 外层 COUNT 估算 ⇒ 响应里 {@code approximate=true}。</li>
 *   <li><b>列名先过 information_schema 再拼 SQL</b>：这些表的列并不齐（{@code gl_inter_offset}/
 *       {@code gl_salary_run} 无 status，{@code pay_plan}/{@code archive_*} 无 del_flag，
 *       {@code archive_access_log} 无 update_time），一项缺失就整条 SQL 报错会被吞成"该域无数据"
 *       ——正是 那个"缺列连累整条"的老坑，故此处逐列降级为 NULL 常量而不是连坐。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MyDocumentService {

    private final JdbcTemplate jdbcTemplate;

    /** 每表查询上限：翻页再深也不让单表把内存吃掉（超出部分归并后自然落选） */
    private static final int PER_TABLE_CAP = 300;

    /** 列元数据缓存：表名 → 小写列名集合（空集＝表不存在；null＝元数据查询失败，不校验） */
    private final Map<String, Set<String>> tableColumns = new ConcurrentHashMap<>();
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    /** exp_bill 一张表承载 10+ 种单据，真类型/报账人只能从行内列取，其余表 bizType 是常量 */
    private static final String EXP_BILL = "exp_bill";
    private static final Map<String, String> EXTRA_SELECT = Map.of(
            EXP_BILL, "bill_type AS billType, bill_sub_type AS billSubType, "
                    + "apply_date AS applyDate, apply_user_name AS applyUserName");

    /**
     * 费用族可按人筛的类型＝工作台场景卡片的 docType（规则与 {@code appendExpenseTypeCond} 一致，
     * 卡片入口和筛选下拉是同一套词）。不含 {@code APPLY} 裸值：库里 APPLY 只配
     * {@code bill_sub_type} 用，裸筛会把出差/培训一并混进来。
     */
    private static final List<String> EXPENSE_FILTER_KEYS = List.of(
            "CLAIM", "CLAIM_TRAVEL", "APPLY_MEETING", "APPLY_TRAVEL", "APPLY_TRAIN",
            "LOAN", "REPAY", "PAY", "PREPAY", "SETTLE");

    /** 一表多 bizType 时的筛选键前缀（{@code table:gl_journal_entry}）——只能按表筛，见 {@link #supportedTypes()} */
    private static final String TABLE_FILTER_PREFIX = "table:";

    /**
     * 业务主表 → 该表在注册表里的全部 bizType（规则与查询一致：只看有归属人列的可取行）。
     * 多值＝行内没有类型列、无法判定某一行属于哪一种。
     */
    private static final Map<String, Set<String>> TABLE_BIZ_TYPES = indexTableBizTypes();

    private static Map<String, Set<String>> indexTableBizTypes() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        BillRouteRegistry.exactRoutes().forEach((bt, r) -> {
            if (r.fillTable() == null || r.ownerCol() == null) return;
            m.computeIfAbsent(r.fillTable(), t -> new LinkedHashSet<>()).add(bt);
        });
        return Map.copyOf(m);
    }

    /** 本服务会查的全部主表（含费用族共表），供列元数据一次装载 */
    private static final List<String> ALL_TABLES = List.copyOf(TABLE_BIZ_TYPES.keySet());

    /** 一张业务主表的一条查询范围（同表多 bizType 时列名一致，取首行的列配置） */
    private record Source(String table, String filterKey, String bizType, String ownerCol,
                          String noCol, String amtCol, String titleCol) {
    }

    // 对外查询

    /**
     * 我登记的全类型单据。
     *
     * @param bizType 场景卡片名称表的类型（{@code CLAIM}/{@code APPLY_MEETING}/{@code CONTRACT}…），
     *                或 {@code table:<表名>}（一表多类型时按表筛，见 {@link #supportedTypes()}）；
     *                空＝全部可支持的类型
     * @param status 单据状态（DRAFT/APPROVING/REJECTED/…）；空＝全部
     */
    public Map<String, Object> myDocs(PageQuery query, String bizType, String status) {
        LoginUser me = UserContext.get();
        Long userId = me == null ? null : me.getUserId();
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.max(1, Math.min(100, query.getPageSize()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        if (userId == null) {
            result.put("records", List.of());
            result.put("total", 0);
            result.put("unsupported", List.of());
            return result;
        }
        Long tenantId = TenantContext.getTenantId();
        String type = bizType == null ? null : bizType.trim();
        List<Source> sources = selectSources(type, status);
        int perTable = (int) Math.min(PER_TABLE_CAP, pageNum * pageSize);

        List<Map<String, Object>> merged = new ArrayList<>();
        boolean capped = false;
        for (Source s : sources) {
            List<Map<String, Object>> rows = fetchByTable(s, userId, tenantId, type, status, perTable);
            if (rows.size() >= perTable && perTable < PER_TABLE_CAP) capped = true;
            merged.addAll(rows);
        }
        merged.sort(Comparator.comparing((Map<String, Object> r) -> str(r.get("docTime")),
                Comparator.reverseOrder()));
        int from = (int) Math.min(pageSize * (pageNum - 1), merged.size());
        int to = (int) Math.min(from + pageSize, merged.size());
        List<Map<String, Object>> page = new ArrayList<>(merged.subList(from, to));

        result.put("records", page);
        result.put("total", countTotal(sources, userId, tenantId, type, status, merged.size()));
        // 单表查询封顶 ⇒ 归并后的次序可能不含该表更靠后的行，总数只能算近似
        result.put("approximate", capped);
        result.put("types", supportedTypes());
        result.put("unsupported", unsupported());
        return result;
    }

    /**
     * 可按人检索的筛选项（供前端类型下拉，规则＝注册表，不再另立清单）。
     *
     * <p>费用族逐别名（行内有 {@code bill_type + bill_sub_type}，能精确到卡片那几类）；
     * 其余表若一表只对应一种 bizType 就用该 bizType，一表多 bizType（{@code gl_journal_entry}
     * ＝计提/待摊/其他收入/手工凭证、{@code po_order}＝PO/PURCHASE/评审）只能用 {@code table:} 键——
     * 按其中某个 bizType 筛会把同表其它类型的行一起返回，等于给了个错规则。
     */
    private List<Map<String, Object>> supportedTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        for (String k : EXPENSE_FILTER_KEYS) {
            types.add(Map.of("bizType", k, "table", EXP_BILL));
        }
        new TreeMap<>(TABLE_BIZ_TYPES).forEach((table, bts) -> {
            if (EXP_BILL.equals(table)) return;   // 费用族已按别名给出
            String key = bts.size() == 1 ? bts.iterator().next() : TABLE_FILTER_PREFIX + table;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bizType", key);
            m.put("table", table);
            m.put("label", BillRouteRegistry.domainLabelOfTable(table));
            types.add(m);
        });
        return types;
    }

    /**
     * 因"表里没有登记人列"而无法按人检索的类型 —— 前端必须明示，不能让列表静默少数据。
     * 另登记一类"主数据变更申请"（crm_customer/md_supplier）：有 create_by 但没有单号/标题列，
     * 注册表行本身就不参与待办回填，这里同样如实报出而不是硬凑一列。
     */
    public List<Map<String, Object>> unsupported() {
        Set<String> covered = new HashSet<>();
        BillRouteRegistry.exactRoutes().forEach((bt, r) -> {
            if (r.fillTable() != null && r.ownerCol() != null) covered.add(r.fillTable());
        });
        Map<String, Map<String, Object>> byTable = new LinkedHashMap<>();
        BillRouteRegistry.exactRoutes().forEach((bt, r) -> {
            if (r.fillTable() == null) {
                String home = r.companyTable();
                if (home != null && !covered.contains(home) && r.noCol() == null && r.titleCol() == null
                        && r.ownerCol() != null) {
                    byTable.computeIfAbsent(home, t -> bucket(t, "NO_DOC_NO_COL"))
                            .merge("bizTypes", bt, MyDocumentService::append);
                }
                return;
            }
            if (covered.contains(r.fillTable())) return;
            Set<String> cols = columnsOf(r.fillTable());
            if (cols != null && cols.isEmpty()) return;   // 表不存在（treasury_transfer/asset_card 是历史留下的死映射）
            if (r.ownerCol() != null) return;
            byTable.computeIfAbsent(r.fillTable(), t -> bucket(t, "NO_OWNER_COL"))
                    .merge("bizTypes", bt, MyDocumentService::append);
        });
        return new ArrayList<>(byTable.values());
    }

    private Map<String, Object> bucket(String table, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", table);
        m.put("label", BillRouteRegistry.domainLabelOfTable(table));
        m.put("reason", reason);
        m.put("bizTypes", "");
        return m;
    }

    private static Object append(Object a, Object b) {
        String s = (String) a;
        return s.isEmpty() ? b : s + "," + b;
    }

    // 查询

    /** 按 bizType/status 过滤出本次要查的表；同一张表只查一次 */
    private List<Source> selectSources(String bizType, String status) {
        String req = bizType == null ? "" : bizType.trim();
        boolean allTypes = req.isEmpty();
        boolean byTable = req.startsWith(TABLE_FILTER_PREFIX);
        String wantedTable = allTypes ? null
                : byTable ? req.substring(TABLE_FILTER_PREFIX.length()) : bizTypeTable(req);
        Map<String, Source> byTableKey = new LinkedHashMap<>();
        BillRouteRegistry.exactRoutes().forEach((bt, r) -> {
            if (r.fillTable() == null || r.ownerCol() == null) return;
            String filterKey = bt;
            if (byTable) {
                if (!wantedTable.equals(r.fillTable())) return;
                filterKey = req;
            } else if (!allTypes) {
                // 费用族共表：过滤条件取请求的别名（CLAIM_TRAVEL/APPLY_MEETING/…），不是路由键
                if (wantedTable != null) {
                    if (!wantedTable.equals(r.fillTable())) return;
                    filterKey = req;
                } else if (!bt.equals(req)) {
                    return;
                }
            }
            if (status != null && !status.isBlank() && !hasColumn(r.fillTable(), "status")) return;
            // 同表多个 bizType（费用族/总账族…）⇒ 列名一致，取首行为准；请求类型由 filterKey 表达
            if (byTableKey.containsKey(r.fillTable())) return;
            byTableKey.put(r.fillTable(), new Source(r.fillTable(), filterKey,
                    soleBizTypeOf(r.fillTable()), r.ownerCol(), r.noCol(), r.amtCol(), r.titleCol()));
        });
        return new ArrayList<>(byTableKey.values());
    }

    /** 一表只承载一种 bizType 时该类型的真值；多值（或费用族行内自带类型）时 null＝不猜 */
    private static String soleBizTypeOf(String table) {
        if (EXP_BILL.equals(table)) return null;
        Set<String> bts = TABLE_BIZ_TYPES.get(table);
        return bts != null && bts.size() == 1 ? bts.iterator().next() : null;
    }

    /** 费用族场景别名 → exp_bill（其余类型走各自本表，bizType 即路由键） */
    private static String bizTypeTable(String bizType) {
        if (bizType == null) return null;
        if (bizType.startsWith("EXP_") || BillRouteRegistry.EXP_BILL_FILLED.contains(bizType)
                || bizType.startsWith("APPLY_") || "CLAIM_TRAVEL".equals(bizType)) {
            return EXP_BILL;
        }
        return null;
    }

    private List<Map<String, Object>> fetchByTable(Source s, Long userId, Long tenantId,
                                                   String bizType, String status, int limit) {
        Set<String> cols = columnsOf(s.table());
        if (cols != null && cols.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id AS bizId, ")
                .append(expr(s.table(), s.noCol(), "docNo", s.filterKey())).append(", ")
                .append(expr(s.table(), s.titleCol(), "title", s.filterKey())).append(", ")
                .append(expr(s.table(), s.amtCol(), "amount", s.filterKey())).append(", ")
                .append(expr(s.table(), "status", "status", s.filterKey())).append(", ")
                .append(timeExpr(s.table())).append(", ")
                .append(s.ownerCol()).append(" AS ownerUserId, '").append(s.table()).append("' AS sourceTable");
        String extra = EXTRA_SELECT.get(s.table());
        if (extra != null) sql.append(", ").append(extra);
        sql.append(" FROM ").append(s.table()).append(" WHERE ").append(s.ownerCol()).append(" = ?");
        args.add(userId);
        if (hasColumn(s.table(), "tenant_id")) {
            sql.append(" AND tenant_id = ?");
            args.add(tenantId);
        }
        if (hasColumn(s.table(), "del_flag")) sql.append(" AND del_flag = 0");
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (EXP_BILL.equals(s.table())) appendExpenseTypeCond(sql, args, bizType);
        sql.append(" ORDER BY docTime DESC LIMIT ?");
        args.add(limit);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            boolean expense = EXP_BILL.equals(s.table());
            String ledger = BillRouteRegistry.ledgerRouteOfTable(s.table());
            String label = expense ? null : BillRouteRegistry.domainLabelOfTable(s.table());
            for (Map<String, Object> row : rows) {
                // 共表族的真类型在行内（bill_type），拿路由键当类型＝把差旅报销说成还款（错值比空值危险）
                row.put("bizType", expense ? row.get("billType") : s.bizType());
                row.put("typeLabel", label);
                // 只有费用族的填单页在工作台内联（与 /my-bills 同一规则），其余域一律跳台账编辑
                row.put("editable", expense);
                row.put("ledgerRoute", ledger);
            }
            return rows;
        } catch (Exception ex) {
            warnOnce("Q" + s.table(), "我的单据加载失败 表={}（该域暂不展示）: {}", s.table(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * 费用族场景别名 → exp_bill 的 bill_type/bill_sub_type 条件。
     * 别名规则＝场景卡片（{@code RequisitionWorkbenchService.getScenes()}）的 docType，
     * 保证"卡片入口"与"我的单据筛选"是同一套词。
     */
    private void appendExpenseTypeCond(StringBuilder sql, List<Object> args, String bizType) {
        if (!EXP_BILL.equals(bizTypeTable(bizType))) return;
        switch (bizType) {
            case "CLAIM" -> {
                // 库里 bill_sub_type 有三种历史写法（NULL/''/'-'＝日常，'TRAVEL'＝差旅）⇒ 只把差旅分出去
                sql.append(" AND bill_type = 'CLAIM' AND (bill_sub_type IS NULL OR bill_sub_type <> 'TRAVEL')");
            }
            case "CLAIM_TRAVEL" -> sql.append(" AND bill_type = 'CLAIM' AND bill_sub_type = 'TRAVEL'");
            case "APPLY_MEETING", "APPLY_TRAVEL", "APPLY_TRAIN" -> {
                sql.append(" AND bill_type = 'APPLY' AND bill_sub_type = ?");
                args.add(bizType.substring("APPLY_".length()));
            }
            default -> {
                // LOAN/REPAY/PAY/APPLY/CLAIM/TRAVEL/TRAIN…＝bill_type 直接对上；EXP_ 前缀同义
                String t = bizType.startsWith("EXP_") ? bizType.substring(4) : bizType;
                sql.append(" AND bill_type = ?");
                args.add(t);
            }
        }
    }

    private long countTotal(List<Source> sources, Long userId, Long tenantId, String bizType,
                            String status, int fallback) {
        if (sources.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM (");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (Source s : sources) {
            Set<String> cols = columnsOf(s.table());
            if (cols != null && cols.isEmpty()) continue;
            if (!first) sql.append(" UNION ALL ");
            first = false;
            sql.append("SELECT 1 AS c FROM ").append(s.table())
                    .append(" WHERE ").append(s.ownerCol()).append(" = ?");
            args.add(userId);
            if (hasColumn(s.table(), "tenant_id")) {
                sql.append(" AND tenant_id = ?");
                args.add(tenantId);
            }
            if (hasColumn(s.table(), "del_flag")) sql.append(" AND del_flag = 0");
            if (status != null && !status.isBlank()) {
                sql.append(" AND status = ?");
                args.add(status);
            }
            if (EXP_BILL.equals(s.table())) appendExpenseTypeCond(sql, args, bizType);
        }
        sql.append(") x");
        if (first) return fallback;
        try {
            Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
            return n == null ? fallback : n;
        } catch (Exception ex) {
            warnOnce("COUNT_TOTAL", "我的单据总数估算失败（按已取条数返回）: {}", ex.getMessage());
            return fallback;
        }
    }

    // 列元数据（同 WfTaskController 的逐列降级规则）

    /**
     * 表 → 列名（小写）。
     *
     * <p>逐表懒查 {@code information_schema} 时，每张表一条，重启后首个请求要连发几十条
     * 元数据查询，冷启动会明显卡住。这里改为首次需要时<b>一条 IN 查完</b>整批表，
     * 之后走进程内缓存。降级语义不变：批量装载失败返回 {@code null}＝本次不校验列名（单表失败同样如此）。
     */
    private Set<String> columnsOf(String table) {
        if (table == null || table.isBlank()) return Set.of();
        Set<String> cached = tableColumns.get(table);
        if (cached != null) return cached;
        synchronized (tableColumns) {
            if (tableColumns.containsKey(table)) return tableColumns.get(table);
            loadAllColumns();
            // 不在批量范围内（unsupported() 侧那些缺归属人列的表）才回落到单表查询
            if (!tableColumns.containsKey(table)) loadColumns(table);
            return tableColumns.get(table);
        }
    }

    /** 批量装载：本服务参与查询的全部主表（清单由 {@link #selectSources} 给出，两处须同步修改）。 */
    private void loadAllColumns() {
        List<String> tables = ALL_TABLES;
        if (tables.isEmpty()) return;
        String in = String.join(",", java.util.Collections.nCopies(tables.size(), "?"));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT LOWER(table_name) AS t, LOWER(column_name) AS c FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND LOWER(table_name) IN (" + in + ")",
                    tables.toArray());
            Map<String, Set<String>> m = new HashMap<>();
            for (Map<String, Object> r : rows) {
                Object t = r.get("t");
                Object c = r.get("c");
                if (t == null || c == null) continue;
                m.computeIfAbsent(t.toString(), k -> new HashSet<>()).add(c.toString());
            }
            for (String t : tables) {
                tableColumns.put(t.toLowerCase(), m.getOrDefault(t.toLowerCase(), Set.of()));
            }
        } catch (Exception ex) {
            log.warn("我的单据列元数据批量装载失败（列名暂不校验）: {}", ex.getMessage());
        }
    }

    private void loadColumns(String table) {
        try {
            Set<String> cols = new HashSet<>();
            for (String c : jdbcTemplate.queryForList(
                    "SELECT LOWER(column_name) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(?)",
                    String.class, table)) {
                if (c != null) cols.add(c);
            }
            tableColumns.put(table, cols);
        } catch (Exception ex) {
            log.warn("我的单据列元数据查询失败 表={}（列名暂不校验）", table, ex);
        }
    }

    private boolean hasColumn(String table, String col) {
        Set<String> cols = columnsOf(table);
        return cols == null || cols.contains(col.toLowerCase());
    }

    /** 列不存在 → 退化成 NULL 常量列：缺一项不连累同一条 SQL 的其余各项 */
    private String expr(String table, String col, String alias, String bizType) {
        if (col == null || col.isBlank()) return "NULL AS " + alias;
        Set<String> cols = columnsOf(table);
        if (cols == null || cols.contains(col.toLowerCase())) return col + " AS " + alias;
        warnOnce(table + "." + col, "我的单据缺列：表 {} 无列 {}（bizType={}，{} 置空）",
                table, col, bizType, alias);
        return "NULL AS " + alias;
    }

    /** 无 update_time 的表（archive_access_log 等）回落 create_time；统一格式化成字符串，排序/展示同一规则 */
    private String timeExpr(String table) {
        boolean hasUpdate = hasColumn(table, "update_time");
        boolean hasCreate = hasColumn(table, "create_time");
        String inner = hasUpdate && hasCreate ? "COALESCE(update_time, create_time)"
                : hasUpdate ? "update_time" : hasCreate ? "create_time" : null;
        if (inner == null) return "NULL AS docTime";
        return "IFNULL(DATE_FORMAT(" + inner + ", '%Y-%m-%d %H:%i:%s'), '0000-00-00 00:00:00') AS docTime";
    }

    private void warnOnce(String key, String template, Object... args) {
        if (warnedKeys.add(key)) log.warn(template, args);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
