package com.fssc.report.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.ImageStatsFacade;
import com.fssc.framework.security.DataScopeHolder;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.security.annotation.ReportDomain;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理报表查询（需求文档 11 章）：借款余额汇总 / 合同汇总 / 费用明细表。
 * 只读聚合，租户拦截器自动注入 tenant_id。
 *
 * <p><b>数据权限</b>：本控制器 24 个查询端点补 {@code @DataScope}，由
 * {@code PermissionAspect.aroundDataScope} 把登录人 data_scope 写入 {@link DataScopeHolder}，
 * 再由 {@link #resolveScope} 翻译成 {@link ScopeFilter} 三元组传给 ReportMapper 的
 * {@code <if>} 动态条件。未挂注解的 4 个端点（ops-statistics／ops-efficiency／quality-pass-rate／
 * image-statistics）的驱动表没有公司列（op_audit_task／op_quality 实体无 company_id）或走跨库
 * Facade（img_batch 在影像库），本次不办结，理由写在对应 mapper 方法注释处。</p>
 */
@RestController
@RequestMapping("/api/tenant/report")
@RequiredArgsConstructor
@Slf4j
@FsscDataSource("replica")
public class ReportQueryController {

    private final ReportMapper reportMapper;
    /** 分库改造：img_batch 在 fssc_image 库，影像统计经 Facade 跨库访问（image 路由） */
    private final ImageStatsFacade imageFacade;

    // 数据范围解析

    /**
     * 数据范围解析结果。三个维度各为 null 表示"该维度不追加条件"，由
     * {@link #resolveScope(boolean, boolean)} 按 data_scope 档位与目标表可用列决定。
     *
     * <p>DENY 用 {@code company_id = -1} 作收紧哨兵（库内不存在该组织主键 ⇒ 结果为空），
     * 与 {@code WfManageOrgScope} 的 {@code id = -1} 既有规则一致，避免"拿不到范围就放开"。</p>
     */
    private record ScopeFilter(Long companyId, List<Long> deptIds, Long userId) {

        /** 不追加任何条件（本租户全部／通配管理员／平台端） */
        static final ScopeFilter ALL = new ScopeFilter(null, null, null);
        /** fail-closed：范围无法解析时收敛为空结果 */
        static final ScopeFilter DENY = new ScopeFilter(-1L, null, null);

        static ScopeFilter byCompany(Long companyId) {
            return new ScopeFilter(companyId, null, null);
        }
    }

    /**
     * 把 {@link DataScopeHolder} 里的 data_scope 翻译成报表 SQL 能表达的过滤三元组。
     * 语义逐条对齐 {@code DataScopeKit.apply}（1 不加条件／2 公司／3·4 部门，无部门列降级公司／
     * 5 本人，无本人列降级公司），并补两处比 DataScopeKit 更严的办结：
     * <ul>
     *   <li>载体为 null（{@code @DataScope} 未生效）→ DENY，而非 DataScopeKit 的"原样放行"；</li>
     *   <li>降级目标列也不存在（company_id 为 null）→ DENY，而非不加条件。</li>
     * </ul>
     * 通配管理员（{@code perms} 含 {@code *:*:*}，即 {@code is_admin=1} 的登录注入）与平台端
     * 取全量：与 {@code PermissionAspect.aroundPerm} 的通配规则一致，不把管理员锁死。
     *
     * @param hasDeptCol 目标表是否有部门列（false 时部门档位降级到公司）
     * @param hasSelfCol 目标表是否有本人列（false 时本人档位降级到公司）
     */
    private ScopeFilter resolveScope(boolean hasDeptCol, boolean hasSelfCol) {
        LoginUser u = UserContext.get();
        if (u == null) {
            return ScopeFilter.DENY;
        }
        if (u.isPlatform() || u.isAdmin()) {
            return ScopeFilter.ALL;
        }
        DataScopeHolder.Scope s = DataScopeHolder.get();
        if (s == null) {
            log.warn("数据范围载体缺失，按最窄口径收紧 user={}", u.getUsername());
            return ScopeFilter.DENY;
        }
        int code = s.getScope();
        if (code <= com.fssc.common.enums.DataScope.TENANT.getCode()) {
            return ScopeFilter.ALL;
        }
        if (code == com.fssc.common.enums.DataScope.COMPANY.getCode()) {
            return s.getCompanyId() == null ? ScopeFilter.DENY : ScopeFilter.byCompany(s.getCompanyId());
        }
        if (code == com.fssc.common.enums.DataScope.DEPT_AND_CHILD.getCode()
                || code == com.fssc.common.enums.DataScope.DEPT.getCode()) {
            if (hasDeptCol) {
                Set<Long> child = s.getDeptAndChild();
                if (code == com.fssc.common.enums.DataScope.DEPT_AND_CHILD.getCode()
                        && child != null && !child.isEmpty()) {
                    return new ScopeFilter(null, new ArrayList<>(child), null);
                }
                if (s.getDeptId() != null) {
                    return new ScopeFilter(null, List.of(s.getDeptId()), null);
                }
            }
            return s.getCompanyId() == null ? ScopeFilter.DENY : ScopeFilter.byCompany(s.getCompanyId());
        }
        if (code == com.fssc.common.enums.DataScope.SELF.getCode()) {
            if (hasSelfCol && s.getUserId() != null) {
                return new ScopeFilter(null, null, s.getUserId());
            }
            return s.getCompanyId() == null ? ScopeFilter.DENY : ScopeFilter.byCompany(s.getCompanyId());
        }
        return ScopeFilter.DENY;
    }

    // 端点

    /** 11.4 借款余额汇总：公司下员工借款金额、未冲销金额明细及汇总 */
    @RequiresPerm("report:view")
    @DataScope("借款余额汇总数据范围")
    @ReportDomain("EXP")
    @GetMapping("/loan-balance")
    public Result<List<Map<String, Object>>> loanBalance() {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.loanBalance(f.companyId(), f.deptIds(), f.userId()));
    }

    /** 11.6 合同汇总：合同总金额、已付款金额等执行情况 */
    @RequiresPerm("report:view")
    @DataScope("合同汇总数据范围")
    @ReportDomain("CONTRACT")
    @GetMapping("/contract-summary")
    public Result<List<Map<String, Object>>> contractSummary() {
        ScopeFilter f = resolveScope(true, false);
        return Result.success(reportMapper.contractSummary(f.companyId(), f.deptIds()));
    }

    /** 合同汇总分页（含关键字过滤；原 /contract-summary 保留给单行查找/多tab报表用） */
    @RequiresPerm("report:view")
    @DataScope("合同汇总分页数据范围")
    @ReportDomain("CONTRACT")
    @GetMapping("/contract-summary/page")
    public Result<PageResult<Map<String, Object>>> contractSummaryPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String kw) {
        ScopeFilter f = resolveScope(true, false);
        IPage<Map<String, Object>> p = reportMapper.contractSummaryPage(Page.of(pageNum, pageSize), kw,
                f.companyId(), f.deptIds());
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize));
    }

    /** 11.7 费用明细表：按 单号/经办人/费用项 关键字查询费用明细 */
    @RequiresPerm("report:view")
    @DataScope("费用明细数据范围")
    @ReportDomain("EXP")
    @GetMapping("/expense-detail")
    public Result<List<Map<String, Object>>> expenseDetail(@RequestParam(required = false) String kw) {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.expenseDetail(kw, f.companyId(), f.deptIds(), f.userId()));
    }

    /** 11.8 运营数据统计：共享作业情况（派工/凭证/付款/质检 + 影像）。
     *  分库改造：影像批次计数在 fssc_image 库，经 ImageFacade 应用层拼接，行结构 {kpi, val} 不变。
     *  本端点不办结——op_audit_task/op_quality 无公司列，且共享作业 KPI 为跨公司集中办理规则。 */
    @RequiresPerm("report:view")
    @ReportDomain("SHARING")
    @GetMapping("/ops-statistics")
    public Result<List<Map<String, Object>>> opsStatistics() {
        Map<String, Object> core = reportMapper.opsStatistics(TenantContext.getTenantId());
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(kpi("派工任务总数", core == null ? null : core.get("taskTotal")));
        rows.add(kpi("待处理任务", core == null ? null : core.get("taskWaiting")));
        rows.add(kpi("已办结任务", core == null ? null : core.get("taskDone")));
        rows.add(kpi("生成凭证数", core == null ? null : core.get("voucherCnt")));
        rows.add(kpi("付款批次", core == null ? null : core.get("payBatchCnt")));
        rows.add(kpi("质检记录", core == null ? null : core.get("qualityCnt")));
        rows.add(Map.of("kpi", "影像批次", "val", imageFacade.countBatches()));
        return Result.success(rows);
    }

    /** KPI 行：与原先 union 分支同形 {kpi, val}，聚合空值按 0。 */
    private static Map<String, Object> kpi(String name, Object val) {
        return Map.of("kpi", name, "val", val instanceof Number n ? n : 0L);
    }

    /** 11.1 费用发生汇总表（期间×费用类型） */
    @RequiresPerm("report:view")
    @DataScope("费用发生汇总数据范围")
    @ReportDomain("EXP")
    @GetMapping("/expense-summary")
    public Result<List<Map<String, Object>>> expenseSummary(@RequestParam(required = false) Integer year,
                                                            @RequestParam(required = false) Integer month,
                                                            @RequestParam(required = false) String itemName) {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.expenseSummary(year, month, itemName,
                f.companyId(), f.deptIds(), f.userId()));
    }

    /** 11.2 费用月度变动表 */
    @RequiresPerm("report:view")
    @DataScope("费用月度变动数据范围")
    @ReportDomain("EXP")
    @GetMapping("/monthly-trend")
    public Result<List<Map<String, Object>>> monthlyTrend() {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.monthlyTrend(f.companyId(), f.deptIds(), f.userId()));
    }

    /** 11.3 费用构成情况表（某类型费用 × 组织 × 月度） */
    @RequiresPerm("report:view")
    @DataScope("费用构成数据范围")
    @ReportDomain("EXP")
    @GetMapping("/expense-composition")
    public Result<List<Map<String, Object>>> expenseComposition(@RequestParam(required = false) String itemName) {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.expenseComposition(itemName, f.companyId(), f.deptIds(), f.userId()));
    }

    // 扩展报表

    /** 1. 应付账龄分析 */
    @RequiresPerm("report:view")
    @DataScope("应付账龄数据范围")
    @ReportDomain("P2P")
    @GetMapping("/ap-aging")
    public Result<List<Map<String, Object>>> apAging() {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.apAging(f.companyId(), f.deptIds(), f.userId()));
    }

    /** 2. 应收账龄分析 */
    @RequiresPerm("report:view")
    @DataScope("应收账龄数据范围")
    @ReportDomain("AR")
    @GetMapping("/ar-aging")
    public Result<List<Map<String, Object>>> arAging() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.arAging(f.companyId()));
    }

    /** 3. 资金收支汇总 */
    @RequiresPerm("report:view")
    @DataScope("资金收支汇总数据范围")
    @ReportDomain("CASH")
    @GetMapping("/cash-flow")
    public Result<List<Map<String, Object>>> cashFlowSummary() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.cashFlowSummary(f.companyId()));
    }

    /** 4. 资金余额表：各银行账户当前余额 */
    @RequiresPerm("report:view")
    @DataScope("资金余额数据范围")
    @ReportDomain("CASH")
    @GetMapping("/bank-balance")
    public Result<List<Map<String, Object>>> bankBalance() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.bankBalanceReport(f.companyId()));
    }

    /** 5. 供应商付款汇总 */
    @RequiresPerm("report:view")
    @DataScope("供应商付款汇总数据范围")
    @ReportDomain("P2P")
    @GetMapping("/supplier-payment")
    public Result<List<Map<String, Object>>> supplierPayment() {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.supplierPayment(f.companyId(), f.deptIds(), f.userId()));
    }

    /** 6. 进项发票统计 */
    @RequiresPerm("report:view")
    @DataScope("进项发票统计数据范围")
    // 发票族统计读侧仅凭报表基础码 report:view 放行；域校验（@ReportDomain）保留给业务域明细/写侧接口，不在统计读上重复卡域权限——原 P2P/AR/TAX 三把域校验致无岗可全取三页（税务岗缺销项页、部门经理缺税负页）。
    @GetMapping("/input-invoice")
    public Result<List<Map<String, Object>>> inputInvoiceStats() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.inputInvoiceStats(f.companyId()));
    }

    /** 7. 销项发票统计 */
    @RequiresPerm("report:view")
    @DataScope("销项发票统计数据范围")
    // 发票族统计读侧仅凭报表基础码 report:view 放行；域校验（@ReportDomain）保留给业务域明细/写侧接口，不在统计读上重复卡域权限——原 P2P/AR/TAX 三把域校验致无岗可全取三页（税务岗缺销项页、部门经理缺税负页）。
    @GetMapping("/output-invoice")
    public Result<List<Map<String, Object>>> outputInvoiceStats() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.outputInvoiceStats(f.companyId()));
    }

    /** 8. 税负分析 */
    @RequiresPerm("report:view")
    @DataScope("税负分析数据范围")
    // 发票族统计读侧仅凭报表基础码 report:view 放行；域校验（@ReportDomain）保留给业务域明细/写侧接口，不在统计读上重复卡域权限——原 P2P/AR/TAX 三把域校验致无岗可全取三页（税务岗缺销项页、部门经理缺税负页）。
    @GetMapping("/tax-burden")
    public Result<List<Map<String, Object>>> taxBurdenAnalysis() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.taxBurdenAnalysis(f.companyId()));
    }

    /** 9. 运营效率分析。本端点不办结——op_audit_task 无公司列（共享作业池跨公司范围）。 */
    @RequiresPerm("report:view")
    @ReportDomain("SHARING")
    @GetMapping("/ops-efficiency")
    public Result<List<Map<String, Object>>> opsEfficiency() {
        return Result.success(reportMapper.opsEfficiency());
    }

    /** 10. 质检合格率。本端点不办结——op_quality 无任何组织列（全中心规则）。 */
    @RequiresPerm("report:view")
    @ReportDomain("SHARING")
    @GetMapping("/quality-pass-rate")
    public Result<List<Map<String, Object>>> qualityPassRate() {
        return Result.success(reportMapper.qualityPassRate());
    }

    /** 11. 影像统计。
     *  分库改造：img_batch 在 fssc_image 库，改经 ImageFacade 查询（列结构不变）。
     *  本端点不办结——跨库 Facade 出数，办结需改 ImageFacade（非本文件面）。 */
    @RequiresPerm("report:view")
    @ReportDomain("IMAGE")
    @GetMapping("/image-statistics")
    public Result<List<Map<String, Object>>> imageStatistics() {
        return Result.success(imageFacade.statBatchByBizType());
    }

    /** 12. 合同执行明细 */
    @RequiresPerm("report:view")
    @DataScope("合同执行明细数据范围")
    @ReportDomain("CONTRACT")
    @GetMapping("/contract-execution")
    public Result<List<Map<String, Object>>> contractExecution() {
        ScopeFilter f = resolveScope(true, false);
        return Result.success(reportMapper.contractExecution(f.companyId(), f.deptIds()));
    }

    /** 13. 预算超支预警（口径与执行率同轴，阈值可传，默认 80%） */
    @RequiresPerm("report:view")
    @DataScope("预算超支预警数据范围")
    @ReportDomain("BUDGET")
    @GetMapping("/budget-overrun")
    public Result<List<Map<String, Object>>> budgetOverrunAlert(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double threshold) {
        // 只按公司维办结（hasDeptCol=false）：公司级预算 dept_id 常为 NULL，见 ReportMapper 同处说明
        ScopeFilter f = resolveScope(false, false);
        double pct = threshold == null || threshold <= 0 || threshold > 100 ? 80.0 : threshold;
        return Result.success(reportMapper.budgetOverrunAlert(f.companyId(), pct));
    }

    /** 14. 借款逾期分析 */
    @RequiresPerm("report:view")
    @DataScope("借款逾期分析数据范围")
    @ReportDomain("EXP")
    @GetMapping("/loan-overdue")
    public Result<List<Map<String, Object>>> loanOverdue() {
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.loanOverdue(f.companyId(), f.deptIds(), f.userId()));
    }

    /** 15. 凭证生成统计 */
    @RequiresPerm("report:view")
    @DataScope("凭证生成统计数据范围")
    @ReportDomain("GL")
    @GetMapping("/voucher-stats")
    public Result<List<Map<String, Object>>> voucherStats() {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.voucherStats(f.companyId()));
    }

    // 预置分析报表体系（新增5个预置报表）

    /** 16. 资产台账报表 */
    @RequiresPerm("report:view")
    @DataScope("资产台账数据范围")
    @ReportDomain("ASSET")
    @GetMapping("/asset-ledger")
    public Result<List<Map<String, Object>>> assetLedger() {
        ScopeFilter f = resolveScope(true, false);
        return Result.success(reportMapper.assetLedger(f.companyId(), f.deptIds()));
    }

    /** 17. 折旧明细报表 */
    @RequiresPerm("report:view")
    @DataScope("折旧明细数据范围")
    @ReportDomain("ASSET")
    @GetMapping("/asset-depr-detail")
    public Result<List<Map<String, Object>>> assetDeprDetail(@RequestParam(required = false) String period,
                                                              @RequestParam(required = false) String assetNo) {
        ScopeFilter f = resolveScope(true, false);
        return Result.success(reportMapper.assetDeprDetail(period, assetNo, f.companyId(), f.deptIds()));
    }

    /** 18. 预算执行明细表 */
    @RequiresPerm("report:view")
    @DataScope("预算执行明细数据范围")
    @ReportDomain("BUDGET")
    @GetMapping("/budget-execution-detail")
    public Result<List<Map<String, Object>>> budgetExecutionDetail(@RequestParam(required = false) Integer budgetYear) {
        // 只按公司维办结，理由同预算超支预警
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.budgetExecutionDetail(budgetYear, f.companyId()));
    }

    /** 19. 费用多维分析 */
    @RequiresPerm("report:view")
    @DataScope("费用多维分析数据范围")
    @ReportDomain("EXP")
    @GetMapping("/expense-analysis")
    public Result<List<Map<String, Object>>> expenseAnalysis(@RequestParam(required = false) Integer year,
                                                             @RequestParam(required = false) Long companyId) {
        // 前端可传 companyId 作查询条件，与本次的范围办结是 AND 关系：越界公司传进来也会被 scope 再拦一道
        ScopeFilter f = resolveScope(true, true);
        return Result.success(reportMapper.expenseAnalysis(year, companyId,
                f.companyId(), f.deptIds(), f.userId()));
    }

    /** 20. 资金流水明细表 */
    @RequiresPerm("report:view")
    @DataScope("资金流水明细数据范围")
    @ReportDomain("CASH")
    @GetMapping("/treasury-flow")
    public Result<List<Map<String, Object>>> treasuryFlow(@RequestParam(required = false) String bankAccount,
                                                           @RequestParam(required = false) String period) {
        ScopeFilter f = resolveScope(false, false);
        return Result.success(reportMapper.treasuryFlow(bankAccount, period, f.companyId()));
    }
}
