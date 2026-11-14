package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.entity.TaxProvisionRule;
import com.fssc.glex.mapper.TaxDeclarationMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.service.NumberRuleService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 全税种申报服务（税务一体化）。
 * <p>
 * 覆盖增值税(VAT)/企业所得税(CIT)/个人所得税(IIT)/印花税(STAMP)/
 * 房产税(PROPERTY)/土地使用税(LAND)/车船税(VEHICLE)。
 * <p>
 * 申报生命周期：DRAFT → PENDING → APPROVED → FILED(已申报) → PAID(已缴纳)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxDeclarationService {

    private final TaxDeclarationMapper declarationMapper;
    private final NumberRuleService numberRuleService;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 申报确认后出「转出应交 → 未交税金」应缴凭证，并按税种取规则表科目 */
    private final VoucherService voucherService;
    /** 纳税周期与申报/缴款截止日按税种规则取（不再写死 15 号） */
    private final TaxProvisionRuleService taxRuleService;

    /**
     * 创建申报记录。
     */
    public Long create(TaxDeclaration record) {
        if (!StringUtils.hasText(record.getTaxType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税种必填");
        }
        if (!StringUtils.hasText(record.getPeriod())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报属期必填");
        }
        record.setDeclarationNo(Optional.ofNullable(record.getDeclarationNo())
                .orElseGet(() -> { try { return numberRuleService.next("TAX_DECLARATION"); }
                    catch (Exception e) { return "SB" + System.currentTimeMillis(); } }));
        record.setStatus("DRAFT");
        // 申报性质缺省＝正常申报；缴款截止日未填时按税种规则的 payDeadlineDay 推算
        if (!StringUtils.hasText(record.getDeclarationKind())) {
            record.setDeclarationKind("NORMAL");
        }
        if (record.getPayDeadline() == null) {
            record.setPayDeadline(defaultPayDeadline(record.getTaxType(), record.getPeriod()));
        }
        // 申报周期未填时按规则的纳税周期回填（月/季/半年/年/次）
        if (!StringUtils.hasText(record.getDeclareType())) {
            TaxProvisionRule rule = taxRuleService.effectiveRuleOf(record.getTaxType());
            if (rule != null && StringUtils.hasText(rule.getPeriodType())) {
                record.setDeclareType(rule.getPeriodType());
            }
        }
        // 自动计算应纳税额
        if (record.getBaseAmount() != null && record.getTaxRate() != null) {
            BigDecimal taxAmt = record.getBaseAmount()
                    .multiply(record.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            record.setTaxAmount(taxAmt);
            if (record.getPaidAmount() == null) {
                record.setPaidAmount(BigDecimal.ZERO);
            }
            record.setBalanceAmount(taxAmt.subtract(record.getPaidAmount()));
        }
        // WS-6 税域办结：非本租户全部档时，强制公司归属为登录人公司（未带则回填，已带则校验）
        Long scopedCompany = DataScopeKit.scopedCompanyId();
        if (scopedCompany != null) {
            if (record.getCompanyId() == null) {
                record.setCompanyId(scopedCompany);
            } else {
                DataScopeKit.requireCompanyInScope(record.getCompanyId());
            }
        }
        declarationMapper.insert(record);
        log.info("创建税务申报: no={}, type={}, period={}", record.getDeclarationNo(), record.getTaxType(), record.getPeriod());
        return record.getId();
    }

    // 下面四个状态跃迁都是"写 tax_declaration 一张业务表
    // + 写 audit_trail 一条记录"成对写入数据库，没有事务边界时记录写失败会留下"状态跃迁了却查不到谁跃的"的
    // 半截记录。外部资料没有覆盖"哪些方法必须有事务边界"（最近旁证只有〔丙·开发指南〕把状态
    // 回写分"聚合 VO／数据库表"两层），故本条按自定处理，不写成对比结论。
    // 单表写、无记录的方法一律不补。
    /**
     * 作废申报：DRAFT / PENDING → CANCELLED。
     *
     * <p>补的是界面上「没有作废出口」的缺口：此前 CANCELLED 是库里可达、界面不可达的
     * 死状态，草稿填错了只能一直挂着。已申报(FILED)/已缴纳(PAID) 不许作废——那要走「更正申报」留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"DRAFT".equals(row.getStatus()) && !"PENDING".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "申报 " + row.getDeclarationNo() + " 当前状态为 " + row.getStatus()
                            + "，只有草稿/待审批可以作废；已申报的请走「更正申报」");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("CANCELLED");
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 已作废（{}→CANCELLED）", row.getDeclarationNo(), row.getStatus());
        Map<String, Object> auditBefore = Collections.singletonMap("status", row.getStatus());
        Map<String, Object> auditAfter = Collections.singletonMap("status", "CANCELLED");
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "作废税务申报");
    }

    /**
     * 提交申报：DRAFT → PENDING。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"DRAFT".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报 " + row.getDeclarationNo() + " 当前状态不可提交");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("PENDING");
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 提交审批", row.getDeclarationNo());
        // 审计记录：状态跃迁 DRAFT → PENDING
        Map<String, Object> auditBefore = Collections.singletonMap("status", row.getStatus());
        Map<String, Object> auditAfter = Collections.singletonMap("status", "PENDING");
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "提交税务申报");
    }

    /**
     * 审批通过：PENDING → APPROVED。
     *
     * <p>禁自审：审批人不得是该申报单的制单人。此前只校验状态，而 {@code gl:taxdecl:approve}
     * 恰好也授给税务经理（制单人本人）⇒ 提交后自己能点【审批通过】，等于没有复核。
     * 与引擎侧 {@code approve.deny_self} 同口径：取当前登录人比对 create_by，取不到登录人时按放行处理
     * （定时任务/系统身份），避免误伤自动化。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"PENDING".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报 " + row.getDeclarationNo() + " 当前状态不可审批");
        }
        Long operator = com.fssc.framework.security.UserContext.get() == null
                ? null : com.fssc.framework.security.UserContext.get().getUserId();
        if (operator != null && row.getCreateBy() != null && operator.equals(row.getCreateBy())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "禁止审批自己提交的申报单（" + row.getDeclarationNo() + "），请由其他税务/复核岗审批");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("APPROVED");
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 审批通过", row.getDeclarationNo());
        // 审计记录：状态跃迁 PENDING → APPROVED
        Map<String, Object> auditBefore = Collections.singletonMap("status", row.getStatus());
        Map<String, Object> auditAfter = Collections.singletonMap("status", "APPROVED");
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "审批通过税务申报");
    }

    /**
     * 确认申报（提交税局）：APPROVED → FILED。
     */
    @Transactional(rollbackFor = Exception.class)
    public void file(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"APPROVED".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报 " + row.getDeclarationNo() + " 未审批，不可申报");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("FILED");
        upd.setDeclareDate(LocalDate.now());
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 已申报", row.getDeclarationNo());
        // 申报确认即出应缴凭证（转出应交 → 未交税金），凭证号回写 source_voucher_id。
        // 出证失败不阻断申报——申报是法定动作，凭证是可重跑的派生数据；失败留 error 日志，
        // 由「补出凭证」端点重试（POST /declaration/{id}/voucher）。
        try {
            Long voucherId = voucherService.generateDeclarationVoucher(row);
            if (voucherId != null) {
                TaxDeclaration link = new TaxDeclaration();
                link.setId(id);
                link.setSourceVoucherId(voucherId);
                declarationMapper.updateById(link);
            }
        } catch (Exception ex) {
            log.error("税务申报 {}（{}）应缴凭证生成失败，申报状态保留 FILED，可稍后补出: {}",
                    row.getDeclarationNo(), row.getTaxType(), ex.getMessage(), ex);
        }
        // 审计记录：状态跃迁 APPROVED → FILED，并记录申报日期变化
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", row.getStatus());
        auditBefore.put("declareDate", row.getDeclareDate());
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", "FILED");
        auditAfter.put("declareDate", upd.getDeclareDate());
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "确认申报");
    }

    /**
     * 更正（调整）申报：复制原申报表到新属期，置 {@code declarationKind=ADJUST} 并关联原单。
     *
     * <p>更正申报按「复制原单 + 关联原单」两步做，金额由用户在新单上改；
     * 不自建算税引擎——税额以外部申报结果为准。
     *
     * @param sourceId 原申报单 id（须已申报 FILED 或已缴纳 PAID，草稿没有"更正"的含义）
     * @param newPeriod 新属期 yyyyMM；为空＝沿用原属期（同期更正）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long copyForAdjust(Long sourceId, String newPeriod) {
        TaxDeclaration src = requireRow(sourceId);
        if (!"FILED".equals(src.getStatus()) && !"PAID".equals(src.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已申报/已缴纳的申报单可发起更正，当前: " + src.getStatus());
        }
        TaxDeclaration copy = new TaxDeclaration();
        copy.setCompanyId(src.getCompanyId());
        copy.setTaxType(src.getTaxType());
        copy.setTaxSubtype(src.getTaxSubtype());
        copy.setPeriod(StringUtils.hasText(newPeriod) ? newPeriod : src.getPeriod());
        copy.setDeclareType(src.getDeclareType());
        copy.setBaseAmount(src.getBaseAmount());
        copy.setTaxRate(src.getTaxRate());
        copy.setTaxAmount(src.getTaxAmount());
        copy.setPaidAmount(BigDecimal.ZERO);
        copy.setBalanceAmount(src.getTaxAmount());
        copy.setRemark("更正申报（原单 " + src.getDeclarationNo() + "）");
        copy.setDeclarationKind("ADJUST");
        copy.setRelatedId(sourceId);
        Long id = create(copy);
        log.info("更正申报生成：原单 {} → 新单 {}（属期 {}）", src.getDeclarationNo(), copy.getDeclarationNo(), copy.getPeriod());
        return id;
    }

    /** 按税种规则推算缴款截止日：属期次月第 payDeadlineDay 日（缺省 15 日） */
    private LocalDate defaultPayDeadline(String taxType, String period) {
        try {
            if (period == null || period.length() != 6) {
                return null;
            }
            TaxProvisionRule rule = taxRuleService.effectiveRuleOf(taxType);
            int day = rule == null || rule.getPayDeadlineDay() == null ? 15 : rule.getPayDeadlineDay();
            int y = Integer.parseInt(period.substring(0, 4));
            int m = Integer.parseInt(period.substring(4, 6));
            LocalDate next = LocalDate.of(y, m, 1).plusMonths(1);
            int maxDay = next.lengthOfMonth();
            return next.withDayOfMonth(Math.min(Math.max(day, 1), maxDay));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 补出/重出应缴凭证：申报确认时出证失败、或历史申报单从未出证的，由本方法重试。
     * 幂等——已有凭证直接返回原凭证 ID（{@code VoucherService.generateDeclarationVoucher} 内去重）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generateVoucher(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"FILED".equals(row.getStatus()) && !"PAID".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "申报 " + row.getDeclarationNo() + " 未确认申报（当前 " + row.getStatus() + "），不可出应缴凭证");
        }
        Long vid = voucherService.generateDeclarationVoucher(row);
        if (vid != null) {
            TaxDeclaration link = new TaxDeclaration();
            link.setId(id);
            link.setSourceVoucherId(vid);
            declarationMapper.updateById(link);
        }
        return vid;
    }

    /**
     * 确认缴纳：FILED → PAID。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id, BigDecimal paidAmount) {
        TaxDeclaration row = requireRow(id);
        if (!"FILED".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报 " + row.getDeclarationNo() + " 未申报，不可缴纳");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("PAID");
        upd.setPaidAmount(paidAmount != null ? paidAmount : row.getTaxAmount());
        if (upd.getPaidAmount() != null && row.getTaxAmount() != null) {
            upd.setBalanceAmount(row.getTaxAmount().subtract(upd.getPaidAmount()));
        }
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 已缴纳, 金额={}", row.getDeclarationNo(), paidAmount);
        // 审计记录：状态跃迁 FILED → PAID，并记录已缴/余额金额变化
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", row.getStatus());
        auditBefore.put("paidAmount", row.getPaidAmount());
        auditBefore.put("balanceAmount", row.getBalanceAmount());
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", "PAID");
        auditAfter.put("paidAmount", upd.getPaidAmount());
        auditAfter.put("balanceAmount", upd.getBalanceAmount());
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "确认缴纳");
    }

    /**
     * 按属期+税种汇总应纳税额。
     */
    public BigDecimal sumTaxAmount(String period, String taxType) {
        LambdaQueryWrapper<TaxDeclaration> qw = new LambdaQueryWrapper<TaxDeclaration>()
                .eq(TaxDeclaration::getPeriod, period)
                .eq(StringUtils.hasText(taxType), TaxDeclaration::getTaxType, taxType)
                .in(TaxDeclaration::getStatus, "FILED", "PAID");
        // WS-6 税域办结：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxDeclaration::getCompanyId, null, null);
        return declarationMapper.selectList(qw).stream()
                .map(d -> d.getTaxAmount() == null ? BigDecimal.ZERO : d.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 分页查询。
     */
    public PageResult<TaxDeclaration> page(long pn, long ps, String taxType, String period) {
        LambdaQueryWrapper<TaxDeclaration> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(taxType)) {
            qw.eq(TaxDeclaration::getTaxType, taxType);
        }
        if (StringUtils.hasText(period)) {
            qw.eq(TaxDeclaration::getPeriod, period);
        }
        // WS-6 税域办结：按数据范围收窄公司维度
        DataScopeKit.apply(qw, TaxDeclaration::getCompanyId, null, null);
        qw.orderByDesc(TaxDeclaration::getCreateTime);
        Page<TaxDeclaration> p = declarationMapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    private TaxDeclaration requireRow(Long id) {
        TaxDeclaration row = declarationMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税务申报记录不存在: id=" + id);
        }
        // WS-6 税域办结：单条读后写，按公司归属校验越权
        DataScopeKit.requireCompanyInScope(row.getCompanyId());
        return row;
    }
}
