// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlFxRevaluation;
import com.fssc.glex.mapper.GlFxRevaluationMapper;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.masterdata.service.ExchangeRateService;
import com.fssc.masterdata.service.PeriodService;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.service.NumberRuleService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * ④ 月末外币汇兑重估服务（CAS 19 外币货币性项目期末按即期汇率重估）。
 *
 * <p>货币性项目三类：AP 外币应付（exp_bill PAY/CLAIM/CLAIM_TRAVEL 未付部分）、
 * LOAN 员工外币借款（exp_bill LOAN 未还余额）、AR 外币应收（rc_receivable 未收余额）。
 * 按 公司+币种 聚合期末余额，取该月末汇率重估本位币，差异生成 DRAFT 凭证（不自动记账），
 * 落 {@code gl_fx_revaluation} 台账，uk 防重跑。功能开关 {@code fx.reval_enabled}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRevaluationService {

    /** 本位币种 */
    private static final String HOME = "CNY";
    /** 外币应付来源单据类型 */
    private static final List<String> AP_BILL_TYPES = Arrays.asList("PAY", "CLAIM", "CLAIM_TRAVEL");
    /** 金额精度（本位币） */
    private static final int MONEY_SCALE = 2;
    /** 凭证号同进程单调序号，配合毫秒时间戳保证 uk_tenant_voucher_no 不冲突 */
    private static final AtomicInteger VOUCHER_SEQ = new AtomicInteger();

    /** 财务费用-汇兑损益 */
    private static final String SUBJECT_PNL = "660304";
    /** 应付账款 */
    private static final String SUBJECT_AP = "2202";
    /** 应收账款 */
    private static final String SUBJECT_AR = "1122";
    /** 其他应收款（员工外币借款） */
    private static final String SUBJECT_LOAN = "1221";

    private static final Map<String, String> CCY_CN = Map.of(
            "USD", "美元", "EUR", "欧元", "JPY", "日元",
            "HKD", "港币", "GBP", "英镑", "SGD", "新加坡元");
    private static final Map<String, String> TYPE_CN = Map.of("AP", "应付", "AR", "应收", "LOAN", "借款");

    private final GlFxRevaluationMapper revalMapper;
    private final ExchangeRateService exchangeRateService;
    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final RcReceivableMapper rcReceivableMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final MdSubjectMapper subjectMapper;
    private final OpVoucherMapper voucherMapper;
    private final OpVoucherLineMapper voucherLineMapper;
    private final SysConfigMapper configMapper;
    private final NumberRuleService numberRuleService;
    private final PeriodService periodService;

    // 纯函数（可脱离 Spring 单测）

    /** 重估方向：损益方向。GAIN 收益（汇兑收益，冲减财务费用）/ LOSS 损失（计入财务费用）/ SKIP 无差异。 */
    public enum FxDirection { GAIN, LOSS, SKIP }

    /** 重估结果载体：重估本位币 + 汇兑差异。 */
    public static final class RevalResult {
        private final BigDecimal revalLocal;
        private final BigDecimal diff;

        RevalResult(BigDecimal revalLocal, BigDecimal diff) {
            this.revalLocal = revalLocal;
            this.diff = diff;
        }

        public BigDecimal getRevalLocal() {
            return revalLocal;
        }

        public BigDecimal getDiff() {
            return diff;
        }
    }

    /**
     * 重估本位币 = 原币合计 × 期末汇率（2 位 HALF_UP）；差异 = 重估本位币 − 账面本位币。
     * 任一入参为 null 视作 0。
     */
    public static RevalResult computeDiff(BigDecimal bookLocal, BigDecimal origTotal, BigDecimal newRate) {
        BigDecimal book = nvl(bookLocal).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal orig = nvl(origTotal);
        BigDecimal rate = nvl(newRate);
        BigDecimal reval = orig.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal diff = reval.subtract(book).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new RevalResult(reval, diff);
    }

    /**
     * 损益方向（按项目性质判方向，diff 为正/负含义不同）：
     * <ul>
     *   <li>负债类 AP：期末汇率涨（diff&gt;0）→ 负债增加 → 损失 LOSS；跌（diff&lt;0）→ 收益 GAIN。</li>
     *   <li>资产类 AR/LOAN：期末汇率涨（diff&gt;0）→ 资产增值 → 收益 GAIN；跌 → 损失 LOSS。</li>
     *   <li>diff=0 → SKIP（无需记账）。</li>
     * </ul>
     */
    public static FxDirection direction(String itemType, BigDecimal diff) {
        if (diff == null || diff.signum() == 0) {
            return FxDirection.SKIP;
        }
        boolean asset = "AR".equals(itemType) || "LOAN".equals(itemType);
        if (asset) {
            return diff.signum() > 0 ? FxDirection.GAIN : FxDirection.LOSS;
        }
        return diff.signum() > 0 ? FxDirection.LOSS : FxDirection.GAIN;
    }

    /** 损益方向 → 会计分录（借/贷）。收益：借 BS 科目 / 贷 6603；损失：借 6603 / 贷 BS 科目。 */
    static List<Object[]> buildEntry(String itemType, FxDirection dir, BigDecimal amount) {
        String bs = bsSubject(itemType);
        List<Object[]> lines = new ArrayList<>();
        if (dir == FxDirection.GAIN) {
            lines.add(new Object[]{bs, "DEBIT", amount});
            lines.add(new Object[]{SUBJECT_PNL, "CREDIT", amount});
        } else {
            lines.add(new Object[]{SUBJECT_PNL, "DEBIT", amount});
            lines.add(new Object[]{bs, "CREDIT", amount});
        }
        return lines;
    }

    static String bsSubject(String itemType) {
        switch (itemType) {
            case "AP": return SUBJECT_AP;
            case "LOAN": return SUBJECT_LOAN;
            default: return SUBJECT_AR;
        }
    }

    // 主流程

    /**
     * 逐外币货币性项目期末重估。{@code fx.reval_enabled=0} 直接返回。
     *
     * @param period 会计期间 yyyyMM 或 yyyy-MM
     * @return {processed, generated, skipped}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revalMonth(String period) {
        Map<String, Object> ret = new LinkedHashMap<>();
        int processed = 0, generated = 0, skipped = 0;
        ret.put("processed", 0);
        ret.put("generated", 0);
        ret.put("skipped", 0);

        if ("0".equals(getConfig("fx.reval_enabled", "1").trim())) {
            log.info("[汇兑重估] 功能开关 fx.reval_enabled=0，跳过 period={}", period);
            return ret;
        }

        String p = StringUtils.hasText(period) ? normPeriod(period) : null;
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        LocalDate monthEnd = monthEnd(p);
        Long tenantId = TenantContext.getTenantId();
        Long defaultCompany = firstCompanyId();

        // 逐币种缓存期末汇率（缺失 → null，落 SKIPPED）
        Map<String, BigDecimal> rateCache = new HashMap<>();

        LinkedHashMap<String, Bucket> buckets = collectBuckets(p, monthEnd, defaultCompany);
        Map<String, MdSubject> subjectCache = new HashMap<>();

        for (Bucket b : buckets.values()) {
            processed++;
            // uk 防重复：该 公司+币种+方向 本期已有记录则跳过
            Long exist = revalMapper.selectCount(new LambdaQueryWrapper<GlFxRevaluation>()
                    .eq(GlFxRevaluation::getPeriod, p)
                    .eq(GlFxRevaluation::getCompanyId, b.companyId)
                    .eq(GlFxRevaluation::getFromCurrency, b.currency)
                    .eq(GlFxRevaluation::getItemType, b.itemType));
            if (exist != null && exist > 0) {
                continue;
            }

            BigDecimal rate = rateCache.computeIfAbsent(b.currency,
                    c -> exchangeRateService.lookup(c, HOME, monthEnd));

            GlFxRevaluation row = new GlFxRevaluation();
            row.setTenantId(tenantId);
            row.setPeriod(p);
            row.setCompanyId(b.companyId);
            row.setFromCurrency(b.currency);
            row.setItemType(b.itemType);
            row.setBookCount(b.count);
            BigDecimal bookLocal = b.bookLocal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            row.setBookLocal(bookLocal);

            if (rate == null || rate.signum() <= 0) {
                row.setStatus("SKIPPED");
                revalMapper.insert(row);
                skipped++;
                log.warn("[汇兑重估] {} {}→CNY 期末({})汇率缺失，币种 {} 组合置 SKIPPED",
                        b.itemType, b.currency, monthEnd, b.currency);
                continue;
            }

            RevalResult rr = computeDiff(bookLocal, b.origTotal, rate);
            row.setRate(rate);
            row.setRevalLocal(rr.getRevalLocal());
            row.setDiff(rr.getDiff());

            FxDirection dir = direction(b.itemType, rr.getDiff());
            if (dir == FxDirection.SKIP) {
                row.setStatus("SKIPPED");
                revalMapper.insert(row);
                skipped++;
                continue;
            }

            Long voucherId = createRevalVoucher(p, monthEnd, b, rr.getDiff(), dir, subjectCache);
            row.setVoucherId(voucherId);
            row.setStatus("GENERATED");
            revalMapper.insert(row);
            generated++;
            log.info("[汇兑重估] 租户 {} 生成重估凭证 voucherId={} {} {} diff={}",
                    tenantId, voucherId, b.itemType, b.currency, rr.getDiff());
        }

        ret.put("processed", processed);
        ret.put("generated", generated);
        ret.put("skipped", skipped);
        return ret;
    }

    /** 分页查询重估流水（供「汇兑重估」页签展示，按最近写入倒序）。 */
    public PageResult<GlFxRevaluation> page(long pageNum, long pageSize, String period, String itemType) {
        LambdaQueryWrapper<GlFxRevaluation> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlFxRevaluation::getPeriod, normPeriod(period));
        }
        if (StringUtils.hasText(itemType)) {
            qw.eq(GlFxRevaluation::getItemType, itemType.trim().toUpperCase(Locale.ROOT));
        }
        qw.orderByDesc(GlFxRevaluation::getId);
        Page<GlFxRevaluation> pg = revalMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(pg.getRecords(), pg.getTotal(), pageNum, pageSize);
    }

    // 试算 / 反冲（四步化：试算→确认→凭证→反冲）

    /**
     * 重估试算（第一步，只算不写入数据库）：逐 公司+币种+方向 桶取期末汇率并算预计差异，
     * 供人工确认后再执行正式重估；本期已有重估记录的桶标 EXIST（执行时将跳过）。
     */
    public Map<String, Object> trial(String period) {
        String p = normPeriod(period);
        LocalDate monthEnd = monthEnd(p);
        Long defaultCompany = firstCompanyId();
        Map<String, BigDecimal> rateCache = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Bucket b : collectBuckets(p, monthEnd, defaultCompany).values()) {
            Long exist = revalMapper.selectCount(new LambdaQueryWrapper<GlFxRevaluation>()
                    .eq(GlFxRevaluation::getPeriod, p)
                    .eq(GlFxRevaluation::getCompanyId, b.companyId)
                    .eq(GlFxRevaluation::getFromCurrency, b.currency)
                    .eq(GlFxRevaluation::getItemType, b.itemType));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("companyId", b.companyId);
            r.put("itemType", b.itemType);
            r.put("itemTypeCn", TYPE_CN.getOrDefault(b.itemType, b.itemType));
            r.put("currency", b.currency);
            r.put("bookCount", b.count);
            r.put("bookLocal", b.bookLocal.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            r.put("origTotal", b.origTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            if (exist != null && exist > 0) {
                r.put("trialStatus", "EXIST");
                r.put("rate", null);
                r.put("diff", null);
            } else {
                BigDecimal rate = rateCache.computeIfAbsent(b.currency,
                        c -> exchangeRateService.lookup(c, HOME, monthEnd));
                r.put("rate", rate);
                if (rate == null || rate.signum() <= 0) {
                    r.put("trialStatus", "NO_RATE");
                    r.put("diff", null);
                } else {
                    RevalResult rr = computeDiff(b.bookLocal.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                            b.origTotal, rate);
                    FxDirection dir = direction(b.itemType, rr.getDiff());
                    r.put("revalLocal", rr.getRevalLocal());
                    r.put("diff", rr.getDiff());
                    r.put("direction", dir.name());
                    r.put("trialStatus", dir == FxDirection.SKIP ? "SKIP" : "READY");
                }
            }
            rows.add(r);
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("period", p);
        ret.put("displayPeriod", displayPeriod(p));
        ret.put("rows", rows);
        return ret;
    }

    /**
     * 反冲（第四步）：对已生成凭证（GENERATED）的重估记录生成借贷对调的反向凭证，
     * 记录置 REVERSED；期间已结账或已反冲过的拦截。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverse(Long revalId) {
        GlFxRevaluation row = revalMapper.selectById(revalId);
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重估记录不存在 id=" + revalId);
        }
        if (!"GENERATED".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅状态为已生成凭证（GENERATED）的重估记录可反冲");
        }
        if (row.getVoucherId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重估记录缺少关联凭证，无法反冲");
        }
        int year = Integer.parseInt(row.getPeriod().substring(0, 4));
        int month = Integer.parseInt(row.getPeriod().substring(4, 6));
        periodService.assertOpen(row.getCompanyId(), year, month);
        Long reversed = voucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, "FX_REVAL_REVERSE")
                .eq(OpVoucher::getSourceDocId, row.getId()));
        if (reversed != null && reversed > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该重估记录已反冲过，不可重复反冲");
        }
        OpVoucher origin = voucherMapper.selectById(row.getVoucherId());
        if (origin == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关联重估凭证已不存在 voucherId=" + row.getVoucherId());
        }

        String summary = "反冲-" + origin.getSummary();
        OpVoucher v = new OpVoucher();
        v.setTenantId(TenantContext.getTenantId());
        v.setCompanyId(origin.getCompanyId());
        v.setVoucherNo(nextFxVoucherNo(row.getPeriod()));
        v.setVoucherWord("转");
        v.setVoucherDate(origin.getVoucherDate());
        v.setPeriodYear(origin.getPeriodYear());
        v.setPeriodMonth(origin.getPeriodMonth());
        v.setSourceType("FX_REVAL_REVERSE");
        v.setSourceDocType("GL_FX_REVALUATION");
        v.setSourceDocId(row.getId());
        v.setSummary(summary);
        v.setDebitTotal(origin.getDebitTotal());
        v.setCreditTotal(origin.getCreditTotal());
        v.setIsBalanced(1);
        v.setStatus("DRAFT");
        v.setRemark("汇兑重估反冲：原凭证 " + origin.getVoucherNo() + "，借贷方向对调");
        voucherMapper.insert(v);

        List<OpVoucherLine> lines = voucherLineMapper.selectByVoucherId(origin.getId());
        Map<String, MdSubject> subjectCache = new HashMap<>();
        int lineNo = 1;
        for (OpVoucherLine ol : lines) {
            String dir = "DEBIT".equals(ol.getDirection()) ? "CREDIT" : "DEBIT";
            addLine(v.getId(), lineNo++, ol.getSubjectCode(), dir, ol.getAmount(),
                    "反冲-" + ol.getSummary(), subjectCache);
        }
        row.setStatus("REVERSED");
        revalMapper.updateById(row);
        log.info("[汇兑重估] 记录 {} 反冲完成，反向凭证 {}", revalId, v.getVoucherNo());

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("revalId", revalId);
        ret.put("reverseVoucherNo", v.getVoucherNo());
        ret.put("amount", origin.getDebitTotal());
        return ret;
    }

    // 查询聚合

    /** 三类货币性项目聚合为 公司+币种+方向 桶。 */
    private LinkedHashMap<String, Bucket> collectBuckets(String p, LocalDate monthEnd, Long defaultCompany) {
        LinkedHashMap<String, Bucket> map = new LinkedHashMap<>();

        // AP 外币应付
        List<ExpBill> apBills = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .in(ExpBill::getBillType, AP_BILL_TYPES)
                .eq(ExpBill::getStatus, "APPROVED")
                .gt(ExpBill::getPayableAmount, BigDecimal.ZERO));
        if (!apBills.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (ExpBill b : apBills) {
                ids.add(b.getId());
            }
            Map<Long, List<ExpBillLine>> linesByBill = new HashMap<>();
            for (ExpBillLine l : expBillLineMapper.selectList(
                    new LambdaQueryWrapper<ExpBillLine>().in(ExpBillLine::getBillId, ids))) {
                linesByBill.computeIfAbsent(l.getBillId(), k -> new ArrayList<>()).add(l);
            }
            for (ExpBill bill : apBills) {
                BigDecimal total = nvl(bill.getTotalAmount());
                BigDecimal payable = nvl(bill.getPayableAmount());
                BigDecimal ratio = total.signum() > 0
                        ? clampRatio(payable.divide(total, MathContext.DECIMAL128))
                        : BigDecimal.ONE;
                for (ExpBillLine l : linesByBill.getOrDefault(bill.getId(), List.of())) {
                    String cur = effCurrency(l.getCurrencyCode(), bill.getCurrencyCode());
                    if (cur == null) {
                        continue;
                    }
                    BigDecimal orig = l.getOrigAmount();
                    if (orig == null || orig.signum() <= 0) {
                        continue;
                    }
                    BigDecimal er = effRate(l.getExchangeRate(), bill.getExchangeRate());
                    BigDecimal lineBook = orig.multiply(er).multiply(ratio);
                    BigDecimal lineOrig = orig.multiply(ratio);
                    addTo(map, bill.getCompanyId() != null ? bill.getCompanyId() : defaultCompany,
                            cur, "AP", lineBook, lineOrig);
                }
            }
        }

        // LOAN 员工外币借款（bill_type=LOAN，loan_balance>0）
        List<ExpBill> loans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getBillType, "LOAN")
                .gt(ExpBill::getLoanBalance, BigDecimal.ZERO));
        for (ExpBill loan : loans) {
            String cur = effCurrency(loan.getCurrencyCode(), loan.getCurrencyCode());
            if (cur == null) {
                continue;
            }
            BigDecimal book = nvl(loan.getLoanBalance());
            BigDecimal er = effRate(loan.getExchangeRate(), loan.getExchangeRate());
            BigDecimal orig = er.signum() > 0
                    ? book.divide(er, MONEY_SCALE, RoundingMode.HALF_UP) : book;
            addTo(map, loan.getCompanyId() != null ? loan.getCompanyId() : defaultCompany,
                    cur, "LOAN", book, orig);
        }

        // AR 外币应收（rc_receivable 无公司维度，取租户首个公司兜底）
        List<RcReceivable> receivables = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .ne(RcReceivable::getCurrencyCode, HOME));
        for (RcReceivable r : receivables) {
            if (defaultCompany == null) {
                log.warn("[汇兑重估] 本租户未维护公司档案，外币应收 {} 无法归属公司，跳过", r.getBillNo());
                break;
            }
            String cur = effCurrency(r.getCurrencyCode(), r.getCurrencyCode());
            if (cur == null) {
                continue;
            }
            BigDecimal orig = nvl(r.getRemainAmount());
            LocalDate asOf = r.getBillDate() != null ? r.getBillDate() : monthEnd;
            BigDecimal oldRate = exchangeRateService.lookup(cur, HOME, asOf);
            if (oldRate == null || oldRate.signum() <= 0) {
                log.warn("[汇兑重估] 外币应收 {} 入账日({})缺 {}→CNY 汇率，无法确定账面，跳过",
                        r.getBillNo(), asOf, cur);
                continue;
            }
            addTo(map, defaultCompany, cur, "AR", orig.multiply(oldRate), orig);
        }
        return map;
    }

    private void addTo(LinkedHashMap<String, Bucket> map, Long companyId, String currency,
                       String itemType, BigDecimal bookLocalDelta, BigDecimal origDelta) {
        if (companyId == null) {
            log.warn("[汇兑重估] {} {} 缺公司归属，跳过", itemType, currency);
            return;
        }
        String key = companyId + "|" + currency + "|" + itemType;
        Bucket b = map.computeIfAbsent(key, k -> new Bucket(companyId, currency, itemType));
        b.bookLocal = b.bookLocal.add(nvl(bookLocalDelta));
        b.origTotal = b.origTotal.add(nvl(origDelta));
        b.count++;
    }

    // 凭证生成

    /** 一公司一币种一方向一张重估凭证（DRAFT，不自动记账）。 */
    private Long createRevalVoucher(String p, LocalDate monthEnd, Bucket b, BigDecimal diff,
                                    FxDirection dir, Map<String, MdSubject> subjectCache) {
        BigDecimal amt = diff.abs().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重估差异为零，不生成凭证");
        }
        String summary = displayPeriod(p) + " 外币汇兑重估-" + TYPE_CN.getOrDefault(b.itemType, b.itemType)
                + ccyCn(b.currency);
        int year = Integer.parseInt(p.substring(0, 4));
        int month = Integer.parseInt(p.substring(4, 6));

        OpVoucher v = new OpVoucher();
        v.setTenantId(TenantContext.getTenantId());
        v.setCompanyId(b.companyId);
        v.setVoucherNo(nextFxVoucherNo(p));
        v.setVoucherWord("转");
        v.setVoucherDate(monthEnd);
        v.setPeriodYear(year);
        v.setPeriodMonth(month);
        v.setSourceType("FX_REVALUATION");
        v.setSummary(summary);
        v.setDebitTotal(amt);
        v.setCreditTotal(amt);
        v.setIsBalanced(1);
        v.setStatus("DRAFT");
        v.setRemark("月末外币汇兑重估自动生成：" + b.itemType + " " + b.currency + " 差异 " + diff);
        voucherMapper.insert(v);

        int lineNo = 1;
        for (Object[] e : buildEntry(b.itemType, dir, amt)) {
            addLine(v.getId(), lineNo++, (String) e[0], (String) e[1], (BigDecimal) e[2], summary, subjectCache);
        }
        return v.getId();
    }

    private void addLine(Long voucherId, int lineNo, String subjectCode, String dir, BigDecimal amount,
                         String summary, Map<String, MdSubject> subjectCache) {
        MdSubject s = subjectCache.computeIfAbsent(subjectCode, this::requireSubject);
        OpVoucherLine l = new OpVoucherLine();
        l.setTenantId(TenantContext.getTenantId());
        l.setVoucherId(voucherId);
        l.setLineNo(lineNo);
        l.setSummary(summary);
        l.setSubjectId(s.getId());
        l.setSubjectCode(s.getSubjectCode());
        l.setSubjectName(s.getSubjectName());
        l.setDirection(dir);
        l.setAmount(amount);
        l.setCurrencyCode(HOME);
        l.setExchangeRate(BigDecimal.ONE);
        l.setOrigAmount(amount);
        voucherLineMapper.insert(l);
    }

    /** 按科目代码查 md_subject，缺失抛业务异常（避免生成无科目的脏凭证）。 */
    private MdSubject requireSubject(String code) {
        MdSubject s = subjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, code).last("limit 1"));
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "汇兑重估所需科目 " + code + " 未配置，无法生成重估凭证，请先维护科目表");
        }
        return s;
    }

    // 工具

    private Long firstCompanyId() {
        SysCompany c = sysCompanyMapper.selectOne(new LambdaQueryWrapper<SysCompany>()
                .orderByAsc(SysCompany::getId).last("limit 1"));
        return c == null ? null : c.getId();
    }

    /** 行有效币种：行币种优先，否则继承表头；CNY/空 → null（不参与外币重估）。 */
    private static String effCurrency(String lineCur, String billCur) {
        String c = StringUtils.hasText(lineCur) ? lineCur : billCur;
        if (!StringUtils.hasText(c)) {
            return null;
        }
        c = c.trim().toUpperCase(Locale.ROOT);
        return HOME.equals(c) ? null : c;
    }

    /** 行有效汇率：行汇率优先（>0），否则表头汇率（>0），否则 1。 */
    private static BigDecimal effRate(BigDecimal lineRate, BigDecimal billRate) {
        if (lineRate != null && lineRate.signum() > 0) {
            return lineRate;
        }
        if (billRate != null && billRate.signum() > 0) {
            return billRate;
        }
        return BigDecimal.ONE;
    }

    private static BigDecimal clampRatio(BigDecimal r) {
        if (r == null || r.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return r.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : r;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 期间归一化：yyyy-MM / yyyyMM → yyyyMM（6 位）。 */
    public static String normPeriod(String period) {
        String p = period.trim().replace("-", "");
        if (p.length() != 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间格式错误（应为 yyyyMM 或 yyyy-MM）: " + period);
        }
        return p;
    }

    /** 该月最后一天。 */
    private static LocalDate monthEnd(String p) {
        return LocalDate.parse(p + "01", DateTimeFormatter.BASIC_ISO_DATE)
                .with(TemporalAdjusters.lastDayOfMonth());
    }

    /** yyyyMM → yyyy-MM。 */
    private static String displayPeriod(String p) {
        return p.substring(0, 4) + "-" + p.substring(4);
    }

    private static String ccyCn(String ccy) {
        return CCY_CN.getOrDefault(ccy, ccy);
    }

    /** 读租户级开关（仿 NotifyService.getConfig：显式按当前租户过滤）。 */
    private String getConfig(String key, String def) {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            if (c != null && c.getConfigValue() != null) {
                return c.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("[汇兑重估] 读取配置失败 key={}", key, e);
        }
        return def;
    }

    /** 聚合桶：某 公司+币种+方向 的账面本位币 / 原币合计 / 条数。 */
    private static final class Bucket {
        final Long companyId;
        final String currency;
        final String itemType;
        BigDecimal bookLocal = BigDecimal.ZERO;
        BigDecimal origTotal = BigDecimal.ZERO;
        int count = 0;

        Bucket(Long companyId, String currency, String itemType) {
            this.companyId = companyId;
            this.currency = currency;
            this.itemType = itemType;
        }
    }

    private String nextFxVoucherNo(String period) {
        try {
            return "FX" + period + "-" + numberRuleService.next("FX_REVALUATION");
        } catch (Exception e) {
            return "FX" + period + "-" + System.currentTimeMillis() + "-" + VOUCHER_SEQ.incrementAndGet();
        }
    }
}
