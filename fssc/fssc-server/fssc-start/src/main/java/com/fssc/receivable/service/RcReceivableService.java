package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.ArReconRecord;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 应收单服务：按销售订单生成应收、按往来单位查询、逾期坏账计提批处理。
 * 状态机：NONE 部分（未结清）/ DONE 结清 / OVERDUE 逾期。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RcReceivableService {

    private final RcReceivableMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    /** 应收对账/分析报告记录（AR_RECON/AR_REPORT 收尾载体，P0-1） */
    private final com.fssc.receivable.mapper.ArReconRecordMapper arReconRecordMapper;
    /** 账期锁定判定（AR蓝图6.5：月结关账后当月不允许再录入业务） */
    private final com.fssc.system.service.PeriodLockService periodLockService;
    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    public PageResult<RcReceivable> page(long pn, long ps, String kw) {
        LambdaQueryWrapper<RcReceivable> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(RcReceivable::getBillNo, kw)
                    .or().eq(RcReceivable::getCustomerId, parseId(kw)));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(RcReceivable::getCompanyId, scopeC);
        }
        qw.orderByDesc(RcReceivable::getId);
        Page<RcReceivable> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /** 按销售订单生成应收单：remainAmount=amount，receivedAmount=0，status=NONE */
    @Transactional
    public Long createSingle(RcReceivable e) {
        orgGuard.assertCompanyAllowed(e.getCompanyId());
        // 必填兜底（治理批次 2B，口径 Q2）：单据号由 genBillNo 自动取号（Q1 人工只读，不校验），
        // 但下面三项留空会造成无法核销/永不逾期的哑数据。
        if (e.getCustomerId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择往来单位");
        }
        if (e.getAmount() == null || e.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入应收金额（须大于 0）");
        }
        if (e.getDueDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择到期日");
        }
        // 蓝图规则6.5：所属账期已被月结关闭时禁止再录入当月应收业务
        if (periodLockService.isLockedByDate(e.getCompanyId(), e.getBillDate() == null ? LocalDate.now() : e.getBillDate())) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "账期 " + java.time.YearMonth.from(e.getBillDate() == null ? LocalDate.now() : e.getBillDate())
                            + " 已被月结关闭，不允许再录入当月业务（蓝图规则6.5）");
        }
        e.setId(null);
        Long tenantId = TenantContext.getTenantId();
        e.setTenantId(tenantId);
        if (!StringUtils.hasText(e.getBillNo())) e.setBillNo(genBillNo(tenantId));
        if (e.getCurrencyCode() == null) e.setCurrencyCode("CNY");
        if (e.getBillDate() == null) e.setBillDate(LocalDate.now());
        // D-V3-142：期间未填 → AR 对账按期间圈单圈不到（BcpReconEngine 以期间过滤拉 FSSC 未清）。
        // 缺省按单据日期归期（yyyy-MM，与 selectReceivables 的格式容错口径一致）。
        if (!StringUtils.hasText(e.getPeriod())) {
            e.setPeriod(e.getBillDate().toString().substring(0, 7));
        }
        if (e.getAmount() == null) e.setAmount(BigDecimal.ZERO);
        e.setReceivedAmount(BigDecimal.ZERO);
        e.setRemainAmount(e.getAmount());
        if (e.getStatus() == null) e.setStatus("NONE");
        // 多分子公司审批路由 V1.3.17：前端已传不覆盖，否则取当前登录用户所属公司
        if (e.getCompanyId() == null) {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) e.setCompanyId(lu.getCompanyId());
        }
        // D-V5R-06：原两行无条件写死 sourceDocType="SALES_ORDER"、sourceDocId=orderId。
        // 手工提单（未选销售订单 ⇒ orderId 为 null）也会被标成"销售订单来源"且 source_doc_id 为 null，
        // 于是：① BCP 侧无法区分「手工单本就无订单」与「系统漏填」；
        //      ② AR 对账明细订单腿四列全 NULL 时无从判读是否缺陷。
        // 改为按 orderId 有无如实标注（实体注释既有 SALES_ORDER/MANUAL 两个枚举，此前从未写过 MANUAL）。
        if (e.getOrderId() != null) {
            e.setSourceDocType("SALES_ORDER");
            e.setSourceDocId(e.getOrderId());
        } else {
            e.setSourceDocType("MANUAL");
            e.setSourceDocId(null);
        }
        mapper.insert(e);
        // P4：应收单生成后发起 OTC 收款审批流（OTC03 应收账款收款，bizType=RECEIVABLE），
        // procInstId 由 wf_instance 留存，审批通过后共享往来结算岗核销并推送 IFS 生成记账凭证。
        try {
            wfEngineFacade.start("RECEIVABLE", e.getId(), userId(),
                    "应收单-" + (e.getBillNo() == null ? e.getId() : e.getBillNo()), e.getAmount());
        } catch (Exception ex) {
            log.error("应收单 {} 发起收款审批失败: {}", e.getId(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return e.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 应收管理分析报告（OTC05 AR_REPORT）：落记录 + 发起审批流（应收会计→财务经理→财务总监），记录 id 作为流程 bizId。 */
    @Transactional
    public Long generateReport(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报告期间不能为空");
        }
        ArReconRecord rec = new ArReconRecord();
        rec.setRecordType("REPORT");
        rec.setPeriod(period);
        rec.setStatus("RUNNING");
        rec.setCompanyId(currentCompanyId());
        arReconRecordMapper.insert(rec);
        Long userId = userId();
        // D-DEC-040：发起金额透真实值，不再硬编码 BigDecimal.ZERO——
        // 金额为 0 时 AR_REPORT 的金额分级/DOA 阈值闸（如 >5,000 自动升级）永不触发＝代码级死配置，
        // 且审批工作台「申请金额」列直显 "-"（12-14 实测两单皆如此）。
        BigDecimal amount = reportPeriodAmount(period, rec.getCompanyId());
        String basis = "本期应收发生额合计";
        // 补完（D-DEC-040 二次收尾）：上一段只算「本期新开单」的发生额，实测本环境应收单集中在
        // 2026-09（5 行）且另有 2 行 period=NULL 的历史行——按手册期间 2026-11/2026-12 发起时
        // 发生额仍为 0，金额闸（wf.timeout.autopass.max_amount 敞口）与工作台「申请金额」列
        // 退回缺陷原状（恒 0／显 "-"）。故发生额为 0 时回退「截至本期末未清应收余额合计」：
        // 同属"本报告所审金额"，且按 bill_date 圈定天然覆盖 period 为空的存量行；两口径均写进 remark 留痕。
        if (amount.signum() == 0) {
            amount = reportOpenBalanceAsOf(period, rec.getCompanyId());
            basis = "本期无新增发生额，取截至本期末未清应收余额合计";
        }
        rec.setAmount(amount);
        rec.setRemark("发起金额=" + basis + " " + amount.toPlainString() + " 元（期间 " + period + "）");
        try {
            String pi = wfEngineFacade.start("AR_REPORT", rec.getId(), userId,
                    "应收分析报告-" + period, amount);
            rec.setProcInstId(pi);
            arReconRecordMapper.updateById(rec);
        } catch (Exception ex) {
            log.error("应收分析报告 {}（记录 {}）发起审批失败: {}", period, rec.getId(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return rec.getId();
    }

    /**
     * AR_REPORT 发起金额口径（D-DEC-040）：本期本公司应收发生额合计（rc_receivable.amount）。
     * period 两式并存（应收单缺省按单据日期写 yyyy-MM，历史/导入数据亦有 yyyyMM），故两种写法一并匹配；
     * 无本公司应收单时如实返回 0（本期确无发生额，与"硬编码 0"的区别是有出处、可复核）。
     */
    private BigDecimal reportPeriodAmount(String period, Long companyId) {
        String compact = period == null ? "" : period.replace("-", "").trim();
        if (compact.length() < 6) {
            log.warn("应收分析报告期间 {} 无法归一为 yyyyMM，发起金额按 0 上报", period);
            return BigDecimal.ZERO;
        }
        String dashed = compact.substring(0, 4) + "-" + compact.substring(4, 6);
        List<RcReceivable> rows = mapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .and(w -> w.eq(RcReceivable::getPeriod, compact).or().eq(RcReceivable::getPeriod, dashed))
                .eq(companyId != null, RcReceivable::getCompanyId, companyId));
        BigDecimal sum = rows.stream()
                .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("应收分析报告 期间 {}（公司 {}）命中应收单 {} 张，发起金额 {}", period, companyId, rows.size(), sum);
        return sum;
    }

    /**
     * AR_REPORT 发起金额回退口径（D-DEC-040 二次收尾）：截至报告期末仍未收回的应收余额合计
     * （rc_receivable.remain_amount &gt; 0 且 bill_date ≤ 期末，本公司）。
     * <p>刻意按 bill_date 而非 period 圈定——历史/导入行的 period 为空（实测 2 行 127,200 元未清），
     * 等值匹配会整组漏掉，回退也就白回退了。期间无法归一或无未清行时如实返回 0。
     */
    private BigDecimal reportOpenBalanceAsOf(String period, Long companyId) {
        java.time.YearMonth ym;
        try {
            String compact = period == null ? "" : period.replace("-", "").trim();
            ym = java.time.YearMonth.of(Integer.parseInt(compact.substring(0, 4)),
                    Integer.parseInt(compact.substring(4, 6)));
        } catch (Exception ex) {
            log.warn("应收分析报告期间 {} 无法归一为 yyyyMM，未清余额回退口径按 0 上报", period);
            return BigDecimal.ZERO;
        }
        List<RcReceivable> rows = mapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .isNotNull(RcReceivable::getBillDate)
                .le(RcReceivable::getBillDate, ym.atEndOfMonth())
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .eq(companyId != null, RcReceivable::getCompanyId, companyId));
        BigDecimal sum = rows.stream()
                .map(r -> r.getRemainAmount() == null ? BigDecimal.ZERO : r.getRemainAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("应收分析报告 期间 {}（公司 {}）期末未清应收 {} 张，回退发起金额 {}", period, companyId, rows.size(), sum);
        return sum;
    }

    /** 应收账款对账（OTC04 AR_RECON）：落记录 + 发起对账审批流，记录 id 作为流程 bizId（P0-1 收尾载体）。 */
    @Transactional
    public Long reconStart(String period, Long customerId) {
        // 对账记录无期间即无从复核（ar_recon_record.period 可空，DB 不拦）
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写对账期间（yyyyMM）");
        }
        ArReconRecord rec = new ArReconRecord();
        rec.setRecordType("RECON");
        rec.setPeriod(period);
        rec.setCustomerId(customerId);
        rec.setStatus("RUNNING");
        rec.setCompanyId(currentCompanyId());
        // D-DEC-415：对账金额取本期真实合计（口径同本文件 AR_REPORT 的发起金额），
        // 既落 ar_recon_record.amount，也顶替原先硬传的 BigDecimal.ZERO。
        BigDecimal amount = reportPeriodAmount(period, rec.getCompanyId());
        if (amount.signum() == 0) {
            amount = reportOpenBalanceAsOf(period, rec.getCompanyId());
        }
        rec.setAmount(amount);
        arReconRecordMapper.insert(rec);
        Long userId = userId();
        try {
            String pi = wfEngineFacade.start("AR_RECON", rec.getId(), userId,
                    "应收对账-" + (period == null ? "" : period)
                            + (customerId == null ? "" : "-客户" + customerId),
                    amount);
            rec.setProcInstId(pi);
            arReconRecordMapper.updateById(rec);
        } catch (Exception ex) {
            log.error("应收对账（记录 {}）发起审批失败: {}", rec.getId(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return rec.getId();
    }

    /** 当前登录用户所属公司（未登录返回 null） */
    private Long currentCompanyId() {
        return UserContext.get() == null ? null : UserContext.get().getCompanyId();
    }

    /** 按往来单位查询应收单 */
    public List<RcReceivable> listByCustomer(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getCustomerId, customerId)
                .orderByDesc(RcReceivable::getDueDate));
    }

    /**
     * 逾期打卡可准入状态集（D-DEC-408 甲案）＝"已确认前后但未结清"的四态：
     * NONE 未确认（createSingle 缺省态）／NORMAL 开票派生／CONFIRMED 应收审批通过／PARTIAL 部分收款。
     * <p>业务口径来源：催收域的业务前提是「到期未收的应收账款」，判据主项是
     * {@code remain_amount > 0}（与本域 {@code ArClaimService:392}／{@code ArStatementService:80}／
     * {@code ArWriteoffService:116} 的 {@code status <> 'DONE'} 同一族口径），而不是「单据尚未走完审批」。
     * 原实现只放 {@code status='NONE'} 进逾期（全项目唯一 OVERDUE 写入前置），导致
     * 一支应收越往主链下游走（NORMAL→CONFIRMED）越不可能被判逾期＝状态机与业务预期反向（D-DEC-408）。
     * <p>OVERDUE 本身不在准入集内＝保持原"只打卡一次"语义，避免日终批处理每日重复翻档刷审计流水。
     */
    public static final java.util.Set<String> OVERDUE_MARKABLE_STATUSES =
            java.util.Set.of("NONE", "NORMAL", "CONFIRMED", "PARTIAL");

    /**
     * 逾期坏账计提批处理：把已到期未结清的应收打卡状态置为 OVERDUE 一次。
     * <p>基准日＝系统当前日（保留原调用方语义）；补跑历史／未来账期请用
     * {@link #accrueOverdueBadDebt(java.time.LocalDate)}（D-DEC-407）。
     */
    @Transactional
    public int accrueOverdueBadDebt() {
        return accrueOverdueBadDebt(null);
    }

    /**
     * 逾期坏账计提批处理（带账期基准日，D-DEC-407 甲案）。
     * <p>原实现把 {@code LocalDate.now()} 写死在判据里，且全链路无基准日入参 ⇒
     * 凡"业务期间晚于系统日"（补录历史／跑到未来账期）时到期日永不早于基准日，逾期集恒空，
     * 下游催收待办、账龄分期、坏账计提（{@code RcBadDebtService:90} 依赖 OVERDUE）与日终站内信
     * 同因空转。本重载只把"与哪一天比"参数化，判定式（到期日 &lt; 基准日 ∧ 余额 &gt; 0）一字未改。
     *
     * @param asOfDate 账期／跑批基准日；null 或空白回退系统当前日（与旧行为逐字一致）
     */
    @Transactional
    public int accrueOverdueBadDebt(LocalDate asOfDate) {
        LocalDate baseline = asOfDate == null ? LocalDate.now() : asOfDate;
        LambdaQueryWrapper<RcReceivable> qw = new LambdaQueryWrapper<RcReceivable>()
                .in(RcReceivable::getStatus, OVERDUE_MARKABLE_STATUSES)
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .isNotNull(RcReceivable::getDueDate)
                .lt(RcReceivable::getDueDate, baseline);
        List<RcReceivable> list = mapper.selectList(qw);
        for (RcReceivable r : list) {
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", r.getStatus());
            RcReceivable u = new RcReceivable();
            u.setId(r.getId());
            u.setStatus("OVERDUE");
            mapper.updateById(u);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", "OVERDUE");
            auditTrailService.recordUpdate("RC_RECEIVABLE", r.getId(), r.getBillNo(), "rc_receivable",
                    String.valueOf(r.getId()), auditBefore, auditAfter,
                    "逾期自动置为 OVERDUE（基准日 " + baseline + "）");
        }
        log.info("逾期计提批处理 基准日 {}：命中到期未结清应收 {} 张", baseline, list.size());
        return list.size();
    }

    /**
     * 账期入参归一（D-DEC-407）：把「基准日」或「期间」两种写法折成一个跑批基准日。
     * <ul>
     *   <li>asOfDate（yyyy-MM-dd，亦容 yyyyMMdd）优先，逐字取该日；</li>
     *   <li>period（yyyy-MM／yyyyMM）取<b>期末日</b>——账期口径下"到期日早于期末的次日"＝本期届满，
     *       与 {@code reportOpenBalanceAsOf} 的「bill_date ≤ 期末」同一读法；</li>
     *   <li>两者皆空回退 null＝由调用方按系统当前日处理（旧行为）。</li>
     * </ul>
     * 无法归一时如实抛参数校验错，不做静默兜底（避免重演"扫描 0 条被归因成参数不当"）。
     */
    public static LocalDate resolveAsOfDate(String asOfDate, String period) {
        if (StringUtils.hasText(asOfDate)) {
            String v = asOfDate.trim().replace("/", "-");
            try {
                if (v.length() == 8 && v.chars().allMatch(Character::isDigit)) {
                    return LocalDate.of(Integer.parseInt(v.substring(0, 4)),
                            Integer.parseInt(v.substring(4, 6)), Integer.parseInt(v.substring(6, 8)));
                }
                return LocalDate.parse(v);
            } catch (Exception ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "基准日格式无法识别：" + asOfDate + "（应为 yyyy-MM-dd）");
            }
        }
        if (StringUtils.hasText(period)) {
            String compact = period.trim().replace("-", "");
            if (compact.length() < 6) {
                throw new BizException(ErrorCode.PARAM_INVALID, "期间格式无法识别：" + period + "（应为 yyyyMM 或 yyyy-MM）");
            }
            try {
                return java.time.YearMonth.of(Integer.parseInt(compact.substring(0, 4)),
                        Integer.parseInt(compact.substring(4, 6))).atEndOfMonth();
            } catch (Exception ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "期间格式无法识别：" + period);
            }
        }
        return null;
    }

    public RcReceivable require(Long id) {
        RcReceivable r = mapper.selectById(id);
        if (r == null) throw new BizException(ErrorCode.PARAM_INVALID, "应收单不存在: " + id);
        return r;
    }

    private String genBillNo(Long tenantId) {
        return "RC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + (tenantId == null ? "" : String.valueOf(tenantId % 1000));
    }

    private static Long parseId(String kw) {
        try {
            return Long.parseLong(kw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}