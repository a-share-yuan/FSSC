package com.fssc.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.budget.entity.BgItem;
import com.fssc.common.enums.DataScope;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.entity.PrecheckResult;
import com.fssc.compliance.entity.ReviewLog;
import com.fssc.contract.entity.ContContract;
import com.fssc.document.entity.OpArchive;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.framework.security.DataScopeHolder;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.compliance.mapper.FraudDetectionLogMapper;
import com.fssc.compliance.mapper.PrecheckResultMapper;
import com.fssc.compliance.mapper.ReviewLogMapper;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.report.mapper.ReportMapper;
import com.fssc.sharing.entity.OpAuditTask;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.mapper.OpAuditTaskMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表看板聚合查询（租户内）。
 * 全部走 MP 聚合 SQL / @Select join，租户拦截器自动注入 tenant_id —— 天然租户隔离。
 * 说明：聚合查询（selectMaps）为只读，符合"跨租户仅允许聚合"之外的本租户只读聚合。
 */
@Service
@RequiredArgsConstructor
@FsscDataSource("replica")
public class DashboardService {

    private final ExpBillMapper expBillMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final BgItemMapper bgItemMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final ReportMapper reportMapper;
    private final FraudDetectionLogMapper fraudDetectionLogMapper;
    private final PrecheckResultMapper precheckResultMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final OpAuditTaskMapper opAuditTaskMapper;
    // WS-4 看板完整性：合同/发票/档案三卡查询源（租户拦截器自动注入 tenant_id）
    private final ContContractMapper contContractMapper;
    // 不要跨域直连的唯一允许点（read-only 报表聚合）：本类在只读报表服务里、类级路由 replica，
    //    发票进度／档案进度是跨域只读汇总，不走发票/档案域 Facade（那两个 Facade 的实现在本进程不装配）。
    //    分库后这两张表需并入副本或改由域服务出数——见 docker/README-document-invoice-split.md。
    private final InvInvoiceMapper invInvoiceMapper;
    private final OpArchiveMapper opArchiveMapper;
    /** 组织合法性守卫（安全审计①）：看板聚合按登录人公司隔离 */
    private final OrgGuard orgGuard;

    // 报销统计

    /** 按单据类型+状态分组统计（金额/单量，数据范围：管理岗=所辖组织，普通员工=本人/部门） */
    public List<Map<String, Object>> expenseByStatus(String billType) {
        QueryWrapper<ExpBill> qw = new QueryWrapper<>();
        qw.select("bill_type", "status", "count(*) as cnt", "sum(total_amount) as amount");
        if (billType != null && !billType.isBlank()) {
            qw.eq("bill_type", billType);
        }
        applyScope(qw, "company_id", "dept_id", "apply_user_id");
        qw.groupBy("bill_type", "status");
        return expBillMapper.selectMaps(qw);
    }

    /** 月度趋势（按单据期间月，近 N 月，数据范围同 expenseByStatus） */
    public List<Map<String, Object>> expenseTrend(String billType, int months) {
        QueryWrapper<ExpBill> qw = new QueryWrapper<>();
        qw.select("period_year", "period_month", "count(*) as cnt", "sum(total_amount) as amount");
        if (billType != null && !billType.isBlank()) {
            qw.eq("bill_type", billType);
        }
        applyScope(qw, "company_id", "dept_id", "apply_user_id");
        qw.gt("period_month", 0);
        // 按 period 升序取前 N 条会拿到"最早 N 个期间"（22 期里只剩最早半年）。
        // 这里倒序取最近 N 期，再由前端翻回升序按时间轴展示。
        qw.groupBy("period_year", "period_month").orderByDesc("period_year").orderByDesc("period_month");
        List<Map<String, Object>> rows = expBillMapper.selectMaps(qw);
        List<Map<String, Object>> recent = rows.stream()
                .limit(Math.max(months, 1))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(recent);
        return recent;
    }

    // 预算执行率

    /** 按费用项汇总预算执行（总额/调整/已用 + 执行率 used/(total+adjust)，数据范围：公司级/部门级） */
    public List<Map<String, Object>> budgetExecution() {
        QueryWrapper<BgItem> qw = new QueryWrapper<>();
        qw.select("item_id", "sum(total_amount) as total", "sum(adjust_amount) as adjust",
                "sum(occupy_amount) as occupy", "sum(used_amount) as used");
        applyScope(qw, "company_id", "dept_id", null, true);
        qw.groupBy("item_id");
        List<Map<String, Object>> rows = bgItemMapper.selectMaps(qw);
        return rows.stream().map(row -> {
            // 用 LinkedHashMap 保证序列化键序稳定，下钻抽屉才能按固定字段序取值
            Map<String, Object> out = new LinkedHashMap<>();
            Long itemId = toLong(row.get("item_id"));
            out.put("itemId", itemId);
            String itemName = itemNameOf(itemId);
            out.put("itemName", itemName);
            out.put("dimKey", itemName);
            BigDecimal total = toDecimal(row.get("total"));
            BigDecimal adjust = toDecimal(row.get("adjust"));
            BigDecimal used = toDecimal(row.get("used"));
            BigDecimal available = total.add(adjust);
            out.put("totalAmount", total);
            out.put("adjustAmount", adjust);
            out.put("usedAmount", used);
            BigDecimal occupy = toDecimal(row.get("occupy"));
            out.put("occupyAmount", occupy);
            out.put("availableAmount", available);
            out.put("executionRate", available.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : used.divide(available, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            return out;
        }).collect(Collectors.toList());
    }

    // 付款进度

    /** 批次状态统计 */
    public List<Map<String, Object>> payBatchByStatus() {
        QueryWrapper<OpPayBatch> qw = new QueryWrapper<>();
        qw.select("status", "count(*) as cnt", "sum(total_amount) as amount").groupBy("status");
        return opPayBatchMapper.selectMaps(qw);
    }

    /** 付款记录待付/已付汇总 */
    public Map<String, Object> payProgress() {
        QueryWrapper<ExpBillPayment> qw = new QueryWrapper<>();
        qw.select("pay_status", "count(*) as cnt", "sum(amount) as amount").groupBy("pay_status");
        List<Map<String, Object>> rows = expBillPaymentMapper.selectMaps(qw);
        Map<String, Object> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("pay_status"));
            if ("PAID".equals(status)) {
                out.put("paidCnt", row.get("cnt"));
                out.put("paidAmount", row.get("amount"));
            } else if ("WAITING".equals(status)) {
                out.put("waitingCnt", row.get("cnt"));
                out.put("waitingAmount", row.get("amount"));
            }
        }
        out.putIfAbsent("paidCnt", 0);
        out.putIfAbsent("paidAmount", BigDecimal.ZERO);
        out.putIfAbsent("waitingCnt", 0);
        out.putIfAbsent("waitingAmount", BigDecimal.ZERO);
        return out;
    }

    // 部门排行 / 审批时效（join 聚合）

    /**
     * 部门报销排行（金额降序）。
     * 原规则只按 {@code orgGuard.enforceCompany()} 做公司隔离，而接口只挂
     * {@code dashboard:view}（没有额外数据权限）⇒ 任意持该权限的用户都看到全公司各部门排名，
     * 报销员工与共享会计看到的却是<b>逐行完全相同</b>的结果。
     * 公司 + 部门/本人三级办结，范围由 {@code @DataScope} 入口写入
     * {@link DataScopeHolder}（控制器 {@code DashboardController#deptRanking} 已补注解）。
     * 公司范围保持 {@link OrgGuard#enforceCompany()} 不变，避免回归 的公司隔离。
     */
    public List<Map<String, Object>> expenseByDept() {
        Long companyId = orgGuard.enforceCompany();
        ScopeArgs sc = scopeArgs();
        return reportMapper.expenseByDept(companyId, sc.deptIds(), sc.userId());
    }

    /** 审批时效：提交→审批通过平均分钟（按单据类型）。数据范围办结同 {@link #expenseByDept()} */
    public List<Map<String, Object>> approvalEfficiency() {
        Long companyId = orgGuard.enforceCompany();
        ScopeArgs sc = scopeArgs();
        return reportMapper.approvalEfficiency(companyId, sc.deptIds(), sc.userId());
    }

    // 费用概览 KPI

    /**
     * 费用概览四枚指标：本月报销总额 / 待审单据数 / 已付金额 / 预算执行率(%)。
     * 返回<b>单枚</b> Map、camelCase 键，供报表工作台的概览四格直接查询——
     * 原 expenseByStatus 是按「单据类型×状态」分组的明细数组，键也是 snake_case，
     * 概览卡按 camelCase 单值读必然读空，故另立一枚真汇总，不改既有分组接口契约。
     * 规则对齐：总额取本会计期间的非草稿非作废单；待审取已提交＋审批中；
     * 已付复用付款进度卡的 PAID 规则；执行率同 budgetExecution 的 used/(total+adjust)。
     * 数据范围同 expenseByStatus（由持有 @DataScope 的入口写入 DataScopeHolder 后 applyScope 办结）。
     */
    public Map<String, Object> expenseOverview() {
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new HashMap<>();

        // 1) 本月报销总额
        QueryWrapper<ExpBill> monthQw = new QueryWrapper<>();
        monthQw.select("ifnull(sum(total_amount), 0) as totalAmount");
        monthQw.eq("period_year", today.getYear());
        monthQw.eq("period_month", today.getMonthValue());
        monthQw.notIn("status", "DRAFT", "VOID");
        applyScope(monthQw, "company_id", "dept_id", "apply_user_id");
        out.put("totalAmount", scalar(expBillMapper.selectMaps(monthQw), "totalAmount", BigDecimal.ZERO));

        // 2) 待审单据数
        QueryWrapper<ExpBill> pendingQw = new QueryWrapper<>();
        pendingQw.select("count(*) as pendingCount");
        pendingQw.in("status", "SUBMITTED", "APPROVING");
        applyScope(pendingQw, "company_id", "dept_id", "apply_user_id");
        out.put("pendingCount", scalar(expBillMapper.selectMaps(pendingQw), "pendingCount", 0L));

        // 3) 已付金额（与付款进度卡同一把尺子）
        Object paid = payProgress().get("paidAmount");
        out.put("paidAmount", paid == null ? BigDecimal.ZERO : paid);

        // 4) 预算执行率
        QueryWrapper<BgItem> bgQw = new QueryWrapper<>();
        bgQw.select("sum(total_amount) as total", "sum(adjust_amount) as adjust", "sum(used_amount) as used");
        // 与同文件 budgetExecution() 和导出走同一规则：bg_item 公司级下达行 dept_id 为 NULL，
        // 部门级登录人若只按 in(子部门) 过滤会一行不中 ⇒ 执行率恒 0。
        applyScope(bgQw, "company_id", "dept_id", null, true);
        List<Map<String, Object>> bgRows = bgItemMapper.selectMaps(bgQw);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal adjust = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        if (bgRows != null && !bgRows.isEmpty()) {
            // 【】sum() 在"本人规则下一行都没命中"时，MySQL 仍回一行且三列全 NULL，
            // MyBatis 把整行 NULL 映射成 list 里的一个 null 元素 ⇒ isEmpty()=false 而 get(0)=null，
            // 原代码直接 row.get(...) 必 NPE，费用概览整卡 500（共享会计/部门规则下没有 bg_item 行时）。
            Map<String, Object> row = bgRows.get(0);
            if (row != null) {
                total = toDecimal(row.get("total"));
                adjust = toDecimal(row.get("adjust"));
                used = toDecimal(row.get("used"));
            }
        }
        BigDecimal available = total.add(adjust);
        out.put("budgetRate", available.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : used.divide(available, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

        return out;
    }

    /** 聚合查询恒一行：取该行的某个键，空集/空值回落默认值 */
    private Object scalar(List<Map<String, Object>> rows, String key, Object fallback) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return fallback;
        }
        Object v = rows.get(0).get(key);
        return v == null ? fallback : v;
    }

    // 合规风险看板

    /** 合规风险概览：预检统计、欺诈告警、审单统计 */
    public Map<String, Object> complianceRisk() {
        Map<String, Object> result = new HashMap<>();

        // 预检统计：批次数与结果分布分两趟，都走 (tenant_id, …) 前缀索引的窄索引扫描。
        // 合成一趟（COUNT DISTINCT + 条件求和）时优化器只能整表扫，24 万行实测 399ms；
        // 拆开后 75ms + 105ms，且批次数改用派生表分组（COUNT DISTINCT 自身要建临时表去重，453ms）。
        long precheckTotal = precheckResultMapper.countDistinctBatch(TenantContext.getTenantId());
        long precheckPass = 0L;
        long precheckFail = 0L;
        for (Map<String, Object> row : precheckResultMapper.selectMaps(new QueryWrapper<PrecheckResult>()
                .select("check_result", "count(*) as cnt")
                .groupBy("check_result"))) {
            String key = row.get("check_result") == null ? "" : String.valueOf(row.get("check_result"));
            long n = numOf(row, "cnt");
            if ("PASS".equals(key)) {
                precheckPass = n;
            } else if ("FAIL".equals(key)) {
                precheckFail = n;
            }
        }
        result.put("precheckTotal", precheckTotal);
        result.put("precheckPass", precheckPass);
        result.put("precheckFail", precheckFail);
        result.put("precheckPassRate", precheckTotal > 0
                ? BigDecimal.valueOf(precheckPass * 100.0 / precheckTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // 欺诈告警：按结果分组计数（覆盖索引扫描 62ms；条件求和要走整行，198k 行 131ms）
        long fraudHigh = 0L;
        long fraudSuspicious = 0L;
        for (Map<String, Object> row : fraudDetectionLogMapper.selectMaps(new QueryWrapper<FraudDetectionLog>()
                .select("detection_result", "count(*) as cnt")
                .groupBy("detection_result"))) {
            String key = row.get("detection_result") == null ? "" : String.valueOf(row.get("detection_result"));
            long n = numOf(row, "cnt");
            if ("HIGH_RISK".equals(key)) {
                fraudHigh = n;
            } else if ("SUSPICIOUS".equals(key)) {
                fraudSuspicious = n;
            }
        }
        result.put("fraudHigh", fraudHigh);
        result.put("fraudSuspicious", fraudSuspicious);

        // 审单统计：按动作分组计数（覆盖索引扫描 35ms；条件求和要走整行，15 万行 227ms）
        long reviewAutoPass = 0L;
        long reviewAutoReject = 0L;
        long reviewManual = 0L;
        for (Map<String, Object> row : reviewLogMapper.selectMaps(new QueryWrapper<ReviewLog>()
                .select("action", "count(*) as cnt")
                .groupBy("action"))) {
            String key = row.get("action") == null ? "" : String.valueOf(row.get("action"));
            long n = numOf(row, "cnt");
            switch (key) {
                case "AUTO_PASS" -> reviewAutoPass = n;
                case "AUTO_REJECT" -> reviewAutoReject = n;
                case "MANUAL_REVIEW" -> reviewManual = n;
                default -> { }
            }
        }
        long reviewTotal = reviewAutoPass + reviewAutoReject + reviewManual;
        result.put("reviewAutoPass", reviewAutoPass);
        result.put("reviewAutoReject", reviewAutoReject);
        result.put("reviewManual", reviewManual);
        result.put("reviewAutoRate", reviewTotal > 0
                ? BigDecimal.valueOf((reviewAutoPass + reviewAutoReject) * 100.0 / reviewTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return result;
    }

    // 共享运营看板

    /** 运营概览：任务池统计、工单分布、SLA 达标率 */
    public Map<String, Object> opsOverview() {
        Map<String, Object> result = new HashMap<>();

        // 任务池统计：三态一趟扫描（原来是三次全表计数）
        Map<String, Object> pool = firstRow(opAuditTaskMapper.selectMaps(new QueryWrapper<OpAuditTask>()
                .select("SUM(CASE WHEN pool_status = 'WAITING' THEN 1 ELSE 0 END) AS pendingNum",
                        "SUM(CASE WHEN pool_status = 'RECEIVED' THEN 1 ELSE 0 END) AS inProgressNum",
                        "SUM(CASE WHEN pool_status = 'FINISHED' THEN 1 ELSE 0 END) AS completedNum")));
        long pendingTasks = numOf(pool, "pendingNum");
        long inProgressTasks = numOf(pool, "inProgressNum");
        long completedTasks = numOf(pool, "completedNum");
        result.put("pendingTasks", pendingTasks);
        result.put("inProgressTasks", inProgressTasks);
        result.put("completedTasks", completedTasks);
        result.put("totalTasks", pendingTasks + inProgressTasks + completedTasks);

        // 任务类型分布与平均处理时长同按 task_type 分组，合并成一趟分组；
        // 时长只对已完成的算（AVG 跳过 CASE 的 ELSE NULL），与原 isNotNull(finish_time) 同义。
        QueryWrapper<OpAuditTask> typeQw = new QueryWrapper<>();
        typeQw.select("task_type",
                "count(*) as cnt",
                "avg(case when finish_time is not null then timestampdiff(minute, create_time, finish_time) end) as avg_minutes")
                .groupBy("task_type")
                .orderByDesc("cnt");
        List<Map<String, Object>> byType = opAuditTaskMapper.selectMaps(typeQw);
        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        List<Map<String, Object>> avgHandleTime = new ArrayList<>();
        for (Map<String, Object> r : byType) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("task_type", r.get("task_type"));
            d.put("cnt", r.get("cnt"));
            typeDistribution.add(d);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("task_type", r.get("task_type"));
            h.put("avg_minutes", r.get("avg_minutes"));
            avgHandleTime.add(h);
        }
        result.put("taskTypeDistribution", typeDistribution);
        result.put("avgHandleTime", avgHandleTime);

        // 积压趋势（近7天每天新增待办）：必须带时间窗，否则 ORDER BY day LIMIT 7 取的是
        // 全表最早 7 天（实测返回 2026-01-05 起的历史日），与「近7天」口径相反。
        List<Map<String, Object>> backlogTrend = opAuditTaskMapper.selectMaps(
                new QueryWrapper<OpAuditTask>()
                        .select("date(create_time) as day", "count(*) as cnt")
                        .eq("pool_status", "WAITING")
                        .ge("create_time", LocalDate.now().minusDays(6).atStartOfDay())
                        .groupBy("day")
                        .orderByAsc("day"));
        result.put("backlogTrend", backlogTrend);

        return result;
    }

    // 业务实时进度看板：合同/发票/档案（WS-4 看板完整性）

    /**
     * 合同进度：在途＝审批中(APPROVING)；今日完成＝今日落 EFFECTIVE；超时＝已过合同止期(end_date)且仍 EFFECTIVE/CHANGING（履约逾期未关闭）。
     * 数据范围走 applyScope（公司/部门维），与费用概览同尺。
     */
    public Map<String, Object> contractProgress() {
        QueryWrapper<ContContract> qw = new QueryWrapper<>();
        qw.select("sum(case when status='APPROVING' then 1 else 0 end) as inTransit",
                "sum(case when status='EFFECTIVE' and date(update_time)=curdate() then 1 else 0 end) as done",
                "sum(case when status in ('EFFECTIVE','CHANGING') and end_date is not null and end_date<curdate() then 1 else 0 end) as timeout");
        applyScope(qw, "company_id", "dept_id", null);
        return progressRow(contContractMapper.selectMaps(qw), true);
    }

    /** 发票进度：在途＝待核验(verify_status=NONE)；今日完成＝今日核验通过(PASS)；超时＝核验失败(REJECT)待处理。 */
    public Map<String, Object> invoiceProgress() {
        QueryWrapper<InvInvoice> qw = new QueryWrapper<>();
        qw.select("sum(case when verify_status='NONE' then 1 else 0 end) as inTransit",
                "sum(case when verify_status='PASS' and date(verify_time)=curdate() then 1 else 0 end) as done",
                "sum(case when verify_status='REJECT' then 1 else 0 end) as timeout");
        applyScope(qw, "company_id", null, null);
        return progressRow(invInvoiceMapper.selectMaps(qw), true);
    }

    /**
     * 档案进度：在途＝待归档/归档中(PENDING/ARCHIVING)；今日完成＝今日归档(ARCHIVED)。
     * 超时无独立汇总方式（到期预警走 ArchiveExpireJob、不改列），返回 null ⇒ 前端显「－」。
     */
    public Map<String, Object> archiveProgress() {
        QueryWrapper<OpArchive> qw = new QueryWrapper<>();
        qw.select("sum(case when archive_status in ('PENDING','ARCHIVING') then 1 else 0 end) as inTransit",
                "sum(case when archive_status='ARCHIVED' and date(archived_at)=curdate() then 1 else 0 end) as done");
        applyScope(qw, "company_id", null, null);
        return progressRow(opArchiveMapper.selectMaps(qw), false);
    }

    /** 进度三槽位归一：聚合恒一行，空集/空值回落 0；hasTimeout=false ⇒ timeout=null（该域无规则，前端显「－」）。 */
    private Map<String, Object> progressRow(List<Map<String, Object>> rows, boolean hasTimeout) {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> row = (rows == null || rows.isEmpty()) ? null : rows.get(0);
        out.put("inTransit", (row == null || row.get("inTransit") == null) ? 0L : toLong(row.get("inTransit")));
        out.put("done", (row == null || row.get("done") == null) ? 0L : toLong(row.get("done")));
        out.put("timeout", (hasTimeout && row != null && row.get("timeout") != null) ? toLong(row.get("timeout")) : null);
        return out;
    }

    // 工具

    /**
     * 向聚合 QueryWrapper 追加数据范围条件（由持有 @DataScope 的入口方法写入 DataScopeHolder）。
     * 参考 DataScopeKit，但适用于原生字符串列聚合查询（selectMaps）：
     * 1 本租户全量不加条件；2 公司=companyCol；3 部门及以下/4 本部门=deptCol（无部门降级公司）；
     * 5 仅本人=ownerCol（无本人列降级公司）。
     */
    /**
     * 把 {@link DataScopeHolder} 里的范围翻译成 Mapper 需要的两个入参。
     * <p>只产出「部门集合 / 本人」，公司维由调用方按 {@link OrgGuard#enforceCompany()} 单独传 ——
     * 公司范围是 的既有行为，不与数据范围混算，避免改一处动两处。
     * <p>scope=1（本租户全部）/ 2（本公司）时两个入参都为 null：Mapper 的 {@code <if test='... != null'>}
     * 不会追加条件，行为与原先完全一致，只对 3/4/5 级（部门及以下/本部门/仅本人）办结。
     */
    private ScopeArgs scopeArgs() {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return new ScopeArgs(null, null);
        }
        if (s.getScope() <= DataScope.COMPANY.getCode()) {
            return new ScopeArgs(null, null); // 1 租户全部 / 2 本公司：不收窄
        }
        if (s.getScope() == DataScope.SELF.getCode()) {
            return new ScopeArgs(null, s.getUserId());
        }
        if (s.getScope() == DataScope.DEPT.getCode()) {
            // 本部门档只取本部门，与 DataScopeKit.apply／ReportQueryController.resolveScope
            // 同一规则；若与"本部门及下级"档共用 deptAndChild，scope=4 的用户会在部门排行/审批时效里看见子部门数据。
            return new ScopeArgs(s.getDeptId() == null ? null : Set.of(s.getDeptId()), null);
        }
        // 3 部门及以下：取 deptAndChild（无部门信息时按 null 处理＝不收窄）
        return new ScopeArgs(s.getDeptAndChild(), null);
    }

    /** 数据范围入参载体（部门集合 + 本人）。 */
    private record ScopeArgs(Set<Long> deptIds, Long userId) {
    }

    private void applyScope(QueryWrapper<?> qw, String companyCol, String deptCol, String ownerCol) {
        applyScope(qw, companyCol, deptCol, ownerCol, false);
    }

    /**
     * 5 参重载：allowNullDept=true 时，部门维条件放宽为「部门 in 子部门 OR 部门为空」。
     * bg_item 公司级预算 dept_id 常为 NULL，仅 in(child) 会把「有预算却查不到」的既有缺陷在新端点复刻。
     */
    private void applyScope(QueryWrapper<?> qw, String companyCol, String deptCol, String ownerCol, boolean allowNullDept) {
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            return; // 非 @DataScope 入口：不干预
        }
        int scope = s.getScope();
        if (scope <= DataScope.TENANT.getCode()) {
            return;
        }
        if (scope == DataScope.COMPANY.getCode()) {
            if (companyCol != null && s.getCompanyId() != null) {
                qw.eq(companyCol, s.getCompanyId());
            }
            return;
        }
        if (scope == DataScope.DEPT_AND_CHILD.getCode() || scope == DataScope.DEPT.getCode()) {
            Set<Long> child = s.getDeptAndChild();
            if (deptCol != null && scope == DataScope.DEPT_AND_CHILD.getCode()
                    && child != null && !child.isEmpty()) {
                if (allowNullDept) {
                    qw.and(w -> w.in(deptCol, child).or(x -> {
                        x.isNull(deptCol);
                        if (companyCol != null && s.getCompanyId() != null) {
                            x.eq(companyCol, s.getCompanyId());
                        }
                    }));
                } else {
                    qw.in(deptCol, child);
                }
            } else if (deptCol != null && s.getDeptId() != null) {
                if (allowNullDept) {
                    qw.and(w -> w.eq(deptCol, s.getDeptId()).or(x -> {
                        x.isNull(deptCol);
                        if (companyCol != null && s.getCompanyId() != null) {
                            x.eq(companyCol, s.getCompanyId());
                        }
                    }));
                } else {
                    qw.eq(deptCol, s.getDeptId());
                }
            } else if (companyCol != null && s.getCompanyId() != null) {
                qw.eq(companyCol, s.getCompanyId());
            }
            return;
        }
        if (scope == DataScope.SELF.getCode()) {
            if (ownerCol != null && s.getUserId() != null) {
                qw.eq(ownerCol, s.getUserId());
            } else if (companyCol != null && s.getCompanyId() != null) {
                qw.eq(companyCol, s.getCompanyId());
            }
        }
    }

    private String itemNameOf(Long itemId) {
        if (itemId == null) {
            return "未绑定";
        }
        MdExpenseItem item = mdExpenseItemMapper.selectById(itemId);
        return item == null ? "未知(" + itemId + ")" : item.getItemName();
    }

    /** 单行聚合取首行：selectMaps 的集合聚合恒返回一行，空表时该行各列为 NULL。 */
    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }

    /** 聚合列取长整型，NULL（空表/无条件命中）按 0 计。 */
    private long numOf(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(v));
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(v));
    }
}
