package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlSalaryRun;
import com.fssc.glex.entity.GlSalaryRunDetail;
import com.fssc.glex.mapper.GlSalaryRunDetailMapper;
import com.fssc.glex.mapper.GlSalaryRunMapper;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/** 薪酬发放服务（gl_salary_run，按部门/期间计算应发、扣除与个税并发放）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlSalaryRunService {

    private final GlSalaryRunMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    private final SysConfigMapper configMapper;
    /** 薪酬计提接预算占用（biz_type=GL_SALARY），与报销域同一规则，人工成本自此有校验 */
    private final BudgetService budgetService;
    /** 薪酬预算科目按费用项定位（费用项 id 走 sys_config，未配置则回退主数据里的「工资薪酬」） */
    private final MdExpenseItemMapper mdExpenseItemMapper;
    /**
     * 171：人员级薪酬明细载体。建表 DDL 是注释态（见实体类注释），
     * 现网大概率尚未落表，故本依赖<b>只读不写</b>，且每个调用点都包了 fail-soft——
     * 「表不存在」绝不能变成薪酬核算失败或界面 500。
     */
    private final GlSalaryRunDetailMapper salaryRunDetailMapper;

    public PageResult<GlSalaryRun> page(long pageNum, long pageSize, String period, String departmentId, String keyword) {
        LambdaQueryWrapper<GlSalaryRun> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlSalaryRun::getPeriod, period);
        }
        if (StringUtils.hasText(departmentId)) {
            qw.eq(GlSalaryRun::getDeptId, Long.valueOf(departmentId));
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlSalaryRun::getRunNo, keyword).or().like(GlSalaryRun::getPayStatus, keyword));
        }
        qw.orderByDesc(GlSalaryRun::getId);
        Page<GlSalaryRun> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 按期间/部门核算员工薪酬：以「发放人数 × 人均标准」计算应发工资、扣除、社保、个税与
     * 应付总额，生成 APPROVING（计提审批中）发放批次——核算即提单，不是最终状态。
     *
     * <p>整改：人均标准不再是写死常量，而是可配置业务参数（sys_config，租户级），
     * 在「系统设置 → 全局参数」维护，未配置时回退 yml/内置默认值：</p>
     * <ul>
     *   <li>{@code salary.std.basic} 人均应发工资（默认 8000.00）</li>
     *   <li>{@code salary.std.deduction} 人均其他扣除（默认 200.00）</li>
     *   <li>{@code salary.std.social} 人均社保（默认 1000.00）</li>
     *   <li>{@code salary.std.tax} 人均个税（默认 300.00）</li>
     * </ul>
     * <p>待接入员工薪酬台账后，可将本方法改为按人汇总；当前「人数 × 标准」规则适用于
     * 无明细台账的批量计提场景。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public GlSalaryRun calculateRun(String period, Long deptId, Integer empCount) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        if (empCount == null || empCount <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发放人数必须 > 0");
        }
        Long tenantId = TenantContext.getTenantId();
        GlSalaryRun run = new GlSalaryRun();
        run.setTenantId(tenantId);
        run.setPeriod(period);
        run.setDeptId(deptId);
        run.setEmpCount(empCount);

        // 人均标准（租户可配置，参数化替代原写死 Mock 常量）
        BigDecimal perSalary = stdAmount("salary.std.basic", "8000.00");
        BigDecimal perDeduction = stdAmount("salary.std.deduction", "200.00");
        BigDecimal perSocial = stdAmount("salary.std.social", "1000.00");
        BigDecimal perTax = stdAmount("salary.std.tax", "300.00");

        BigDecimal salary = perSalary.multiply(BigDecimal.valueOf(empCount));
        BigDecimal deduction = perDeduction.multiply(BigDecimal.valueOf(empCount));
        BigDecimal social = perSocial.multiply(BigDecimal.valueOf(empCount));
        BigDecimal tax = perTax.multiply(BigDecimal.valueOf(empCount));

        salary = salary.setScale(2, RoundingMode.HALF_UP);
        deduction = deduction.setScale(2, RoundingMode.HALF_UP);
        social = social.setScale(2, RoundingMode.HALF_UP);
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        // 薪酬金额公式强校验：total = salary − deduction − social − tax，且各项 ≥ 0
        BigDecimal total = assertSalaryFormula(salary, deduction, social, tax);
        run.setSalaryAmount(salary);
        run.setDeductionAmount(deduction);
        run.setSocialSecurityAmount(social);
        run.setTaxAmount(tax);
        run.setTotalAmount(total);
        run.setRunNo("SR" + period + String.format("%03d", countByPeriod(period) + 1));
        // 核算＝提单，计提审批链此刻才起，落库时不能是最终状态。
        // 写 PAID 会让 8 节审批对单据零回写、账上"已付款"而发放凭证全无。
        // 由 GlBizAction 回写：计提链末节点通过 → POSTED（已入账·未发放），发放链末节点通过 → PAID。
        run.setPayStatus("APPROVING");
        if (run.getCompanyId() == null) {
            run.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(run.getCompanyId());
        mapper.insert(run);
        // 173：人工成本全程不进预算 ⇒ 超支无校验。计提即占用，
        // 放在起流之前、与核算同一事务：起流失败会连占用流水一起回滚，不留幽灵占用。
        occupyBudgetForRun(run);
        // 薪酬计提即发起 GL_SALARY_PROV 薪酬计提审批流（拆分后：计提走 GL_SALARY_PROV，发放走 GL_SALARY_PAY）
        try {
            wfEngineFacade.start("GL_SALARY_PROV", run.getId(), userId(),
                    "薪酬计提-" + period, run.getTotalAmount());
        } catch (Exception ex) {
            log.error("薪酬计提 {} 发起发放审批失败: {}", run.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
        }
        // 171：emp_count 是"人数 × 人均标准"的入参，不是从到人数据数出来的。
        // 保存处即做一致性记录，让"审批人审的是一个总数"在服务端日志里可见（只 WARN，不拦核算）。
        checkEmpCountConsistency(run);
        return run;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 读取租户级人均标准（sys_config），未配置或非法时回退内置默认值 */
    private BigDecimal stdAmount(String key, String def) {
        String v = configValue(key, def);
        try {
            BigDecimal amt = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
            return amt.signum() < 0 ? new BigDecimal(def) : amt;
        } catch (NumberFormatException e) {
            log.warn("薪酬人均标准配置非法 key={} value={}，回退默认 {}", key, v, def);
            return new BigDecimal(def);
        }
    }

    /** 读租户级 sys_config 字符串参数（读取失败/未配置回退 def）——薪酬预算科目参数复用同一条读取路径 */
    private String configValue(String key, String def) {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            if (c != null && StringUtils.hasText(c.getConfigValue())) {
                return c.getConfigValue().trim();
            }
        } catch (Exception e) {
            log.warn("读取薪酬参数配置失败 key={}，回退默认 {}", key, def);
        }
        return def;
    }

    /**
     * 薪酬计提占用预算（占用量＝应发工资总额，与计提凭证借 6602 同额）。
     * <p>失败/落空一律只记录不回滚核算：预算域目前<b>没有</b>薪酬明细行（bg_item 9 行里没有一条薪酬费用项，
     * md_item_budget_map 把 352 工资薪酬 指向不存在的预算明细 98887），此时 {@code BudgetService} 会打
     * 「占用落空，不臆造科目」的 WARN。是否给薪酬单开预算线、超支是否硬拦＝待确认项（见交付报告）。</p>
     */
    private void occupyBudgetForRun(GlSalaryRun run) {
        try {
            Long expenseItemId = salaryBudgetExpenseItemId();
            if (expenseItemId == null) {
                log.warn("薪酬预算科目口径未配置（sys_config 无 salary.budget.expense_item，费用项主数据也没有「工资薪酬」），" +
                        "批次 {} 的占用将落空", run.getRunNo());
            }
            budgetService.occupySalaryBudget(run.getId(), run.getRunNo(), expenseItemId,
                    run.getCompanyId(), run.getDeptId(), periodYear(run.getPeriod()), run.getSalaryAmount());
        } catch (Exception ex) {
            log.error("薪酬批次 {} 预算占用异常（不阻断薪酬核算）: {}",
                    run.getRunNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 薪酬占用挂哪个费用项。优先取租户参数 {@code salary.budget.expense_item}（值＝md_expense_item.id），
     * 未配置则在费用项主数据里按名称「工资薪酬」取第一条（现网 id=352，item_code=23）——
     * 只解析已存在的主数据行，不新建、不臆造。
     */
    private Long salaryBudgetExpenseItemId() {
        String cfg = configValue("salary.budget.expense_item", null);
        if (StringUtils.hasText(cfg)) {
            try {
                return Long.valueOf(cfg);
            } catch (NumberFormatException e) {
                log.warn("薪酬预算科目参数 salary.budget.expense_item 非法（value={}），回退按费用项名称定位", cfg);
            }
        }
        MdExpenseItem item = mdExpenseItemMapper.selectOne(
                new LambdaQueryWrapper<MdExpenseItem>()
                        .eq(MdExpenseItem::getItemName, "工资薪酬")
                        .orderByAsc(MdExpenseItem::getId)
                        .last("limit 1"));
        return item == null ? null : item.getId();
    }

    /** 期间 yyyyMM → 预算年度（用于在多年度预算行中就近命中）；非法期间返回 null＝不做年度约束 */
    private static Integer periodYear(String period) {
        if (period == null || period.trim().length() < 4) {
            return null;
        }
        try {
            int y = Integer.parseInt(period.trim().substring(0, 4));
            return y < 1900 ? null : y;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 金额缺省归零 */
    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 发放：发起 GL_SALARY_PAY 薪酬发放审批流。
     *
     * <p>发起时置 APPROVING（审批中·未付款），不能置 PAID——出纳点"发放"不等于"已付款"。
     * PAID 由 {@code GlBizAction} 在末节点通过且发放凭证出证成功后回写；被驳回到计提办结态 POSTED。
     * <p>校验：只有计提链已办结（POSTED=已入账 / APPROVED=已审批未出证）的批次才允许发起发放；
     * 审批中（APPROVING）拦重复发起，已付款（PAID）/已驳回（REJECTED）拦重放。
     */
    @Transactional(rollbackFor = Exception.class)
    public void postPay(Long id) {
        GlSalaryRun run = mapper.selectById(id);
        if (run == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "薪酬发放记录不存在: " + id);
        }
        String st = run.getPayStatus();
        if ("PAID".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该批次已发放（发放审批办结回写），不可重复发起");
        }
        if ("APPROVING".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该批次审批流在途（计提或发放审批中），不可重复发起发放");
        }
        if (!"POSTED".equals(st) && !"APPROVED".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "薪酬计提链尚未审批通过（当前状态 " + st + "），不可发起发放——"
                            + "已驳回的计提批次请重新核算生成新批次");
        }
        // 发放发起＝"账要变成钱"的最后一道人工可见点，此刻再核一次
        // emp_count 与到人明细是否对得上。取 WARN 而非 fail-closed：明细表 DDL 尚是注释态、
        // 且现库无行级来源可回填，硬拦会把所有薪酬批次一起冻结；
        // "到人数据到底是不是发薪依据"＝待确认项，见交付报告。
        checkEmpCountConsistency(run);
        // 发起≠付款。PAID 由发放审批末节点回写（GlBizAction.handleSalary）。
        // 置位必须在起流之前：事件总线是同步派发（SimpleApplicationEventMulticaster 无 taskExecutor，
        // 见 WfEngineFacade 的 publishEvent 在 Flowable PROCESS_COMPLETED 监听器内），
        // 若流程在 start() 调用栈里即办结（首节点自动跳过/零节点直达结束），
        // 回写会先落 PAID/REJECTED，再被这里的 APPROVING 覆盖成永久在途——
        // 而上面的校验又拦掉 APPROVING 重发，等于批次卡死。与 calculateRun/GeneralJournal 同序。
        GlSalaryRun upd = new GlSalaryRun();
        upd.setId(id);
        upd.setPayStatus("APPROVING");
        mapper.updateById(upd);
        // 薪酬发放即发起 GL_SALARY_PAY 发放审批流（计提 GL_SALARY_PROV 已接线，发放这条腿同样要起流）
        try {
            wfEngineFacade.start("GL_SALARY_PAY", run.getId(), userId(),
                    "薪酬发放-" + run.getPeriod() + "-" + (run.getRunNo() == null ? run.getId() : run.getRunNo()),
                    nvl(run.getTotalAmount()));
        } catch (Exception ex) {
            log.error("薪酬发放 {} 发起审批失败: {}", run.getRunNo(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单（异常回滚，状态一并撤销）
        }
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlSalaryRun>().eq(GlSalaryRun::getPeriod, period));
    }

    /** 薪酬金额公式强校验：total = salary − deduction − social − tax，且各项 ≥ 0、应付 ≥ 0。 */
    private static BigDecimal assertSalaryFormula(BigDecimal salary, BigDecimal deduction,
                                                  BigDecimal social, BigDecimal tax) {
        if (salary.compareTo(BigDecimal.ZERO) < 0 || deduction.compareTo(BigDecimal.ZERO) < 0
                || social.compareTo(BigDecimal.ZERO) < 0 || tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "薪酬各项金额不能为负: 应发=" + salary + " 扣除=" + deduction + " 社保=" + social + " 个税=" + tax);
        }
        BigDecimal total = salary.subtract(deduction).subtract(social).subtract(tax);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "薪酬应付金额不能为负: 应发=" + salary + " 减 扣除=" + deduction + " 社保=" + social
                            + " 个税=" + tax + " 后=" + total);
        }
        return total;
    }

    /**
     * 171：读某薪酬批次的人员级工资明细。<b>只读、fail-soft</b>。
     *
     * <p>{@code gl_salary_run_detail} 的建表 DDL 以注释态交付（见
     * {@link GlSalaryRunDetail} 类注释）且未经产品确认执行，
     * 现网此表不存在——读它必然抛 SQL 语法错。故这里吞掉异常返回空集：
     * "明细载体还没建"不能演化成"薪酬页面 500"，也不能演化成"薪酬核算失败"。
     * 界面按空集显示"暂无人员级明细"即可，语义与真无明细一致（现库确实无到人数据）。</p>
     *
     * @return 到人明细行；表未建或无数据时为空集
     */
    public List<GlSalaryRunDetail> listDetailOfRun(Long runId) {
        if (runId == null) {
            return Collections.emptyList();
        }
        try {
            List<GlSalaryRunDetail> rows = salaryRunDetailMapper.selectList(
                    new LambdaQueryWrapper<GlSalaryRunDetail>()
                            .eq(GlSalaryRunDetail::getRunId, runId)
                            .orderByAsc(GlSalaryRunDetail::getId));
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            log.warn("批次 {} 的人员级薪酬明细读取失败"
                            + "（gl_salary_run_detail 建表 DDL 尚为注释态、未经产品确认执行，现网无此表）：{}",
                    runId, rootMsg(ex));
            return Collections.emptyList();
        }
    }

    /**
     * 171：{@code gl_salary_run.emp_count} 与人员级明细条数的一致性记录。
     *
     * <p><b>为什么是 WARN 而不是 fail-closed</b>（判断点，已按"语义拿不准走 WARN＋报告"处置）：
     * <ol>
     *   <li>明细表 DDL 未执行，任何拦截都等于把整个薪酬模块锁死；</li>
     *   <li>现库没有可回填的行级来源（全库无员工薪酬主数据，{@code md_employee_level} 只是职级字典），
     *       建表后历史批次仍是 0 行，fail-closed 会让历史 3 个批次永远发不出去；</li>
     *   <li>"emp_count 到底必须等于明细行数，还是允许批次级批计规则与到人台账并存"属规则待定，
     *       不由缺陷批次单方面定死。</li>
     * </ol>
     * 三种记录分档写日志，复核时可直接 grep {@code } 统计"无到人支撑的发薪次数"：
     * 表未建（无法核验）／表已建但 0 行（总数规则发薪）／行数与 emp_count 不符。</p>
     *
     * <p>本方法不抛异常、不回滚，挂在 {@link #calculateRun}（保存处）与 {@link #postPay}（发放发起处）。
     * 发放链末节点办结的回写在 {@code GlBizAction.handleSalary}，该段 145/138 在修，本批次不落脚，
     * 由发放发起处的这道记录等效覆盖。</p>
     */
    public void checkEmpCountConsistency(GlSalaryRun run) {
        if (run == null || run.getId() == null) {
            return;
        }
        Integer empCount = run.getEmpCount();
        Long counted;
        try {
            counted = salaryRunDetailMapper.selectCount(new LambdaQueryWrapper<GlSalaryRunDetail>()
                    .eq(GlSalaryRunDetail::getRunId, run.getId()));
        } catch (Exception ex) {
            log.warn("薪酬批次 {}（id={}）声明发放 {} 人，但 emp_count 无法核验——"
                            + "人员级明细表 gl_salary_run_detail 尚未建立（建表 DDL 以注释态交付，待产品确认后执行）；"
                            + "本批次的应发/扣除/社保/个税均由「人数 × sys_config 人均标准」算出，无到人支撑。原因：{}",
                    run.getRunNo(), run.getId(), empCount, rootMsg(ex));
            return;
        }
        long detailCount = counted == null ? 0L : counted;
        if (empCount == null) {
            log.warn("薪酬批次 {}（id={}）emp_count 为空，人员级明细 {} 行，数量无从核对",
                    run.getRunNo(), run.getId(), detailCount);
        } else if (detailCount == 0L) {
            log.warn("薪酬批次 {}（id={}）声明发放 {} 人，人员级明细 0 行——"
                            + "审批人审的是一个总数（应发={} 其他扣除={} 社保={} 个税={}），核不到人；"
                            + "明细表已建但该批次无到人来源，回填须真实薪酬台账，不以人均标准反推造数",
                    run.getRunNo(), run.getId(), empCount,
                    run.getSalaryAmount(), run.getDeductionAmount(),
                    run.getSocialSecurityAmount(), run.getTaxAmount());
        } else if (detailCount != empCount.longValue()) {
            log.warn("薪酬批次 {}（id={}）emp_count={} 与人员级明细 {} 行不一致，差 {} 人——"
                            + "发薪前须核到多出/缺少的是哪几个员工",
                    run.getRunNo(), run.getId(), empCount, detailCount, empCount.longValue() - detailCount);
        } else {
            log.info("薪酬批次 {}（id={}）emp_count={} 与人员级明细 {} 行一致，发放人数有到人支撑",
                    run.getRunNo(), run.getId(), empCount, detailCount);
        }
    }

    /** 取异常链根因消息（建表缺失时外层包了 MyBatis/Spring 两级，日志要看得见真话） */
    private static String rootMsg(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }
}