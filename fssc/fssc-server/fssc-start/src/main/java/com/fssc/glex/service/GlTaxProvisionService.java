// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.gl.entity.GlEntry;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.gl.mapper.GlEntryMapper;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.entity.TaxProvisionRule;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
import com.fssc.glex.mapper.TaxDeclarationMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 税金计提服务（gl_tax_provision，按会计期间批量计提税金）。
 *
 * <p><b> 改造</b>：税种／计税基数／税率／科目原先写死在本类的三条 {@code ProvSpec} 常量里，
 * 换客户即改码；现改为从 {@code tax_provision_rule} 读取（{@link TaxProvisionRuleService#effectiveRules()}），
 * 表空时按内置三条兜底，改造前后行为一致。
 *
 * <p>基数来源三档（{@code base_source}）：
 * <ul>
 *   <li>MANUAL：取规则缺省基数，可被调用方按税种覆盖（前端「手工录入」）</li>
 *   <li>DECLARATION：按同期已申报数（FILED/PAID）汇总</li>
 *   <li>INVOICE：按同期已认证进项税汇总（增值税专用）</li>
 * </ul>
 * 汇总结果为 0 的税种<b>跳过不落行</b>——落一条 0 元计提会起一条 0 元审批流，审批人无法判定其意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlTaxProvisionService {

    private final GlTaxProvisionMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    /** 漏挂断言与挂接核查只读流程实例表（wf_instance），不改引擎、不写引擎 */
    private final WfInstanceMapper wfInstanceMapper;
    /** 挂接核查按 source_doc_type + source_doc_id 查真实凭证（op_voucher） */
    private final OpVoucherMapper opVoucherMapper;
    // 挂接核查再往下探一层——凭证出了但 op_gl_entry 零分录即「应落未落」，只读探测，不写任何表
    private final GlEntryMapper glEntryMapper;
    /** 计提规则：税种/税率/基数来源/科目唯一取数口 */
    private final TaxProvisionRuleService ruleService;
    /** DECLARATION 基数来源取数：同期已申报税额 */
    private final TaxDeclarationMapper taxDeclarationMapper;
    /** INVOICE 基数来源取数：同期已认证进项税额（数据源＝发票池） */
    private final InvInvoiceMapper invInvoiceMapper;

    public PageResult<GlTaxProvision> page(long pageNum, long pageSize, String period, String taxType, String keyword) {
        LambdaQueryWrapper<GlTaxProvision> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlTaxProvision::getPeriod, period);
        }
        if (StringUtils.hasText(taxType)) {
            qw.eq(GlTaxProvision::getTaxType, taxType);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlTaxProvision::getProvisionNo, keyword).or().like(GlTaxProvision::getVoucherNo, keyword));
        }
        qw.orderByDesc(GlTaxProvision::getId);
        Page<GlTaxProvision> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 无覆盖的计提：按规则表逐税种落行 */
    @Transactional(rollbackFor = Exception.class)
    public List<GlTaxProvision> provision(String period) {
        return provision(period, null);
    }

    /**
     * 按期间批量计提税金：对规则表里每个生效税种各计提一条，税额=计税基数×税率。
     *
     * @param baseOverrides 手工基数覆盖（税种码 → 基数）。只对 {@code base_source=MANUAL} 的规则生效；
     *                      传了覆盖值时以覆盖值为准，未传则用规则缺省基数
     */
    @Transactional(rollbackFor = Exception.class)
    public List<GlTaxProvision> provision(String period, Map<String, BigDecimal> baseOverrides) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        List<TaxProvisionRule> rules = ruleService.effectiveRules();
        if (rules == null || rules.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未配置任何税金计提规则，请先在「税务计提规则」中维护税种与税率");
        }
        Long tenantId = TenantContext.getTenantId();
        // 组织合法性守卫：记账主体绑定登录公司（集团级=空，保持旧行为）
        Long cid = orgGuard.currentCompany();
        orgGuard.assertCompanyAllowed(cid);
        // 期间重复计提防护：同一会计期间、同一税种只允许一张有效计提单。
        // 会计上同期同税种重复计提等于重复入账（会各出一张凭证），只能先冲销原单再重算。
        // 作废态（REJECTED 驳回 / REVERSED 冲销）不计入占用，允许重新计提。
        java.util.Set<String> existingTypes = mapper.selectList(new LambdaQueryWrapper<GlTaxProvision>()
                        .eq(GlTaxProvision::getPeriod, period)
                        .notIn(GlTaxProvision::getStatus, "REJECTED", "REVERSED"))
                .stream().map(GlTaxProvision::getTaxType).collect(java.util.stream.Collectors.toSet());
        long seq = countByPeriod(period) + 1;
        List<GlTaxProvision> created = new ArrayList<>();
        int skipped = 0;
        int dupSkipped = 0;
        for (TaxProvisionRule rule : rules) {
            if (existingTypes.contains(rule.getTaxType())) {
                log.info("税金计提 {}：税种 {} 本期已有有效计提单，跳过（如需重算请先冲销原单）",
                        period, rule.getTaxType());
                dupSkipped++;
                continue;
            }
            BigDecimal base = resolveBase(rule, period, baseOverrides);
            if (base == null || base.signum() <= 0) {
                log.info("税金计提 {}：税种 {} 基数来源 {} 汇总为 0，跳过（不落 0 元计提行）",
                        period, rule.getTaxType(), rule.getBaseSource());
                skipped++;
                continue;
            }
            GlTaxProvision e = new GlTaxProvision();
            e.setTenantId(tenantId);
            e.setCompanyId(cid);
            e.setPeriod(period);
            e.setTaxType(rule.getTaxType());
            e.setSubjectId(ruleService.subjectIdOf(rule.getCreditSubjectCode()));
            e.setBaseAmount(base);
            e.setRate(rule.getTaxRate());
            // 税金金额公式强校验：taxAmount = baseAmount × rate / 100（四舍五入取 2 位）
            e.setTaxAmount(calcTaxAmount(base, rule.getTaxRate()));
            e.setProvisionNo("TX" + period + String.format("%03d", seq++));
            // 提交只建在途态——最终状态（POSTED/APPROVED/REJECTED）与凭证真号（JZ…）由
            // GlBizAction.handleTax 在流程办结时回写。
            e.setStatus("APPROVING");
            e.setVoucherNo(null);
            mapper.insert(e);
            created.add(e);
        }
        if (created.isEmpty()) {
            if (dupSkipped > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "本期(" + period + ")已有 " + dupSkipped + " 个税种的计提单，未重复生成；"
                                + "如需按新基数重算，请先冲销原计提单");
            }
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本期(" + period + ")所有税种计税基数均为 0（跳过 " + skipped + " 条），无可计提税金");
        }
        // 一条记录一个流程实例（逐条起流），审批对象=金额=凭证三者同源
        for (GlTaxProvision e : created) {
            try {
                wfEngineFacade.start("GL_TAX_PROV", e.getId(), userId(),
                        "税金计提-" + period + "-" + taxTypeName(e.getTaxType()),
                        e.getTaxAmount() == null ? BigDecimal.ZERO : e.getTaxAmount());
            } catch (Exception ex) {
                log.error("税金计提 {}（{}，单号 {}）发起审批失败，整批回滚: {}",
                        period, e.getTaxType(), e.getProvisionNo(), ex.getMessage(), ex);
                throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
        assertAllLinked(created);
        return created;
    }

    /**
     * 计提试算：不落库、不起流，只回答「按当前规则本期应计提哪些税种、各多少钱」。
     * 前端「手工录入」用它预填，避免用户盲填一个基数就要跑一次真实计提。
     */
    public List<Map<String, Object>> preview(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (TaxProvisionRule rule : ruleService.effectiveRules()) {
            BigDecimal base = resolveBase(rule, period, null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taxType", rule.getTaxType());
            m.put("ruleName", rule.getRuleName());
            m.put("baseSource", rule.getBaseSource());
            m.put("baseAmount", base);
            m.put("taxRate", rule.getTaxRate());
            m.put("taxAmount", base == null || base.signum() <= 0
                    ? BigDecimal.ZERO : calcTaxAmount(base, rule.getTaxRate()));
            m.put("debitSubjectCode", rule.getDebitSubjectCode());
            m.put("creditSubjectCode", rule.getCreditSubjectCode());
            out.add(m);
        }
        return out;
    }

    /** 按规则的基数来源取本期计税基数；取不到返回 null（调用方跳过） */
    private BigDecimal resolveBase(TaxProvisionRule rule, String period, Map<String, BigDecimal> overrides) {
        String src = rule.getBaseSource() == null ? "MANUAL" : rule.getBaseSource().toUpperCase();
        BigDecimal override = overrides == null ? null : overrides.get(rule.getTaxType());
        if (override != null) {
            if (override.signum() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "税种 " + rule.getTaxType() + " 的计税基数不能为负: " + override);
            }
            return override;
        }
        switch (src) {
            case "DECLARATION": {
                List<TaxDeclaration> rows = taxDeclarationMapper.selectList(
                        new LambdaQueryWrapper<TaxDeclaration>()
                                .eq(TaxDeclaration::getPeriod, period)
                                .eq(TaxDeclaration::getTaxType, rule.getTaxType())
                                .in(TaxDeclaration::getStatus, "FILED", "PAID"));
                return rows.stream()
                        .map(d -> d.getTaxAmount() == null ? BigDecimal.ZERO : d.getTaxAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            case "INVOICE": {
                // 进项认证已并入发票池，取数改读 inv_invoice.deduct_amount，
                // 独立台账已退役：它与发票池是两张皮，不再作为数据源。
                List<InvInvoice> rows = invInvoiceMapper.selectList(
                        new LambdaQueryWrapper<InvInvoice>()
                                .eq(InvInvoice::getCertifyPeriod, period)
                                .eq(InvInvoice::getCertifyStatus, "CERTIFIED"));
                return rows.stream()
                        .map(d -> d.getDeductAmount() == null ? BigDecimal.ZERO : d.getDeductAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            case "MANUAL":
            default:
                return rule.getBaseAmount();
        }
    }

    /**
     * 漏挂断言：本批每条计提记录都必须有 GL_TAX_PROV 流程实例，
     * 条数与实例数不等即显式抛错并回滚整批——"只挂第 1 条"却无人报错正是要堵的口子。
     */
    private void assertAllLinked(List<GlTaxProvision> created) {
        if (created.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (GlTaxProvision e : created) {
            ids.add(e.getId());
        }
        Long linked = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizType, "GL_TAX_PROV")
                .in(WfInstance::getBizId, ids));
        int n = linked == null ? 0 : linked.intValue();
        if (n != ids.size()) {
            List<String> nos = new ArrayList<>();
            for (GlTaxProvision e : created) {
                nos.add(e.getProvisionNo() + "(" + e.getTaxType() + ")");
            }
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税金计提漏挂校验未过：" + ids.size() + " 条计提记录只挂到 " + n + " 条审批流程，整批回滚。明细："
                            + String.join("、", nos));
        }
    }

    /**
     * 计提挂接核查（的"可见、可查、不静默"面）：按期间列出每条计提记录的
     * ①审批流程（wf_instance GL_TAX_PROV，在途/已通过）②凭证（op_voucher source_doc_id）③缴款流程（GL_TAX_PAY，同上规则）
     * 挂接情况，并给出未挂接计数。历史上"只挂第 1 条"留下的无流程/无凭证行在此一览可查。
     *
     * @param period 会计期间 yyyyMM（必输，防全表扫）
     */
    public Map<String, Object> linkCheck(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        List<GlTaxProvision> rows = mapper.selectList(new LambdaQueryWrapper<GlTaxProvision>()
                .eq(GlTaxProvision::getPeriod, period)
                .orderByAsc(GlTaxProvision::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (GlTaxProvision r : rows) {
            ids.add(r.getId());
        }
        Set<Long> withProvFlow = new HashSet<>();
        Set<Long> withPayFlow = new HashSet<>();
        Set<Long> withVoucher = new HashSet<>();
        // 计提记录ID → 凭证ID，凭证ID → op_gl_entry 分录行数（只读探测「应落未落」）
        Map<Long, Long> voucherIdByDoc = new HashMap<>();
        Map<Long, Integer> entryCountByVoucher = new HashMap<>();
        if (!ids.isEmpty()) {
            // 规则与发起侧一致：只有「在途/已通过」的实例才算挂上（LIVE_FLOW_STATUS）。
            // 已驳回/已撤回的流程按未挂处理——该行自身的 status（REJECTED）仍在明细里，不丢信息。
            for (WfInstance wi : wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .in(WfInstance::getBizId, ids)
                    .in(WfInstance::getBizType, "GL_TAX_PROV", "GL_TAX_PAY")
                    .in(WfInstance::getStatus, LIVE_FLOW_STATUS))) {
                if ("GL_TAX_PROV".equals(wi.getBizType())) {
                    withProvFlow.add(wi.getBizId());
                } else {
                    withPayFlow.add(wi.getBizId());
                }
            }
            for (OpVoucher v : opVoucherMapper.selectList(
                    new LambdaQueryWrapper<OpVoucher>()
                            .eq(OpVoucher::getSourceDocType, "GL_TAX_PROV")
                            .in(OpVoucher::getSourceDocId, ids)
                            .ne(OpVoucher::getStatus, "VOID"))) {
                withVoucher.add(v.getSourceDocId());
                voucherIdByDoc.putIfAbsent(v.getSourceDocId(), v.getId());
            }
            // 凭证出了 ≠ 落了总账——按 voucher_id 数 op_gl_entry 分录行数，零行即「应落未落」
            if (!voucherIdByDoc.isEmpty()) {
                List<GlEntry> entryRows = glEntryMapper.selectList(
                        new LambdaQueryWrapper<GlEntry>()
                                .in(GlEntry::getVoucherId, voucherIdByDoc.values()));
                for (GlEntry e : entryRows) {
                    entryCountByVoucher.merge(e.getVoucherId(), 1, Integer::sum);
                }
            }
        }
        int missFlow = 0;
        int missVoucher = 0;
        int missEntry = 0;
        BigDecimal missFlowAmount = BigDecimal.ZERO;
        BigDecimal missVoucherAmount = BigDecimal.ZERO;
        BigDecimal missEntryAmount = BigDecimal.ZERO;
        for (GlTaxProvision r : rows) {
            boolean hasFlow = withProvFlow.contains(r.getId());
            boolean hasVoucher = withVoucher.contains(r.getId());
            Long vid = voucherIdByDoc.get(r.getId());
            int entryCount = vid == null ? 0 : entryCountByVoucher.getOrDefault(vid, 0);
            boolean posted = "POSTED".equalsIgnoreCase(String.valueOf(r.getStatus()));
            boolean missEntryRow = posted && entryCount == 0;
            if (!hasFlow) {
                missFlow++;
                missFlowAmount = missFlowAmount.add(nvl0(r.getTaxAmount()));
            }
            if (!hasVoucher) {
                missVoucher++;
                missVoucherAmount = missVoucherAmount.add(nvl0(r.getTaxAmount()));
            }
            if (missEntryRow) {
                missEntry++;
                missEntryAmount = missEntryAmount.add(nvl0(r.getTaxAmount()));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("provisionNo", r.getProvisionNo());
            item.put("taxType", r.getTaxType());
            item.put("taxAmount", r.getTaxAmount());
            item.put("status", r.getStatus());
            item.put("voucherNo", r.getVoucherNo());
            item.put("hasApproveFlow", hasFlow);
            item.put("hasVoucher", hasVoucher);
            item.put("hasPayFlow", withPayFlow.contains(r.getId()));
            item.put("entryCount", entryCount);
            item.put("postedWithoutEntry", missEntryRow);
            items.add(item);
        }
        if (missFlow > 0 || missVoucher > 0) {
            log.warn("税金计提挂接核查 期间 {}：{} 条无审批流程（¥{}）、{} 条无凭证（¥{}）——" +
                            "存在未挂接条目，请到「税金计提」按单号处理或重新计提",
                    period, missFlow, missFlowAmount, missVoucher, missVoucherAmount);
        }
        if (missEntry > 0) {
            log.warn("税金计提落账核查 期间 {}：{} 条状态已入账但总账分录为 0（¥{}）——应落未落，需查凭证生成腿",
                    period, missEntry, missEntryAmount);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("period", period);
        res.put("total", rows.size());
        res.put("missingFlowCount", missFlow);
        res.put("missingFlowAmount", missFlowAmount);
        res.put("missingVoucherCount", missVoucher);
        res.put("missingVoucherAmount", missVoucherAmount);
        res.put("missingEntryCount", missEntry);
        res.put("missingEntryAmount", missEntryAmount);
        res.put("rows", items);
        return res;
    }

    private static BigDecimal nvl0(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 税种中文名（流程标题用；后可按标题区分同批三条） */
    private static String taxTypeName(String taxType) {
        if (taxType == null) {
            return "税金";
        }
        switch (taxType) {
            case "VAT":
                return "增值税";
            case "SURCHARGE":
                return "附加税";
            case "INCOME":
            case "CIT":
                return "所得税";
            case "IIT":
                return "个人所得税";
            case "STAMP":
                return "印花税";
            default:
                return taxType;
        }
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 缴款流"已挂"判定规则：只算在途/已通过的实例。已驳回（REJECTED）/已撤回（CANCELLED）/已终止
     *  TERMINATED 的缴款流不算挂过——挂接核查按本规则报「已挂缴款流程」，历史在途缴款流才看得见。 */
    private static final List<String> LIVE_FLOW_STATUS = Arrays.asList("RUNNING", "APPROVED");

    // pay(period) 的缴款发起环节已删除——GL_TAX_PAY 那条独立缴款流既不产生付款动作
    // 也不出缴纳凭证（VoucherService.generateTaxVoucher 同批办结），留下的只会是一条「审批通过、什么也没发生」
    // 的僵尸流。税金缴款的真相由对公付款通道承载：应付税额的付款单／付款批次执行后才出「借 2221／贷 1002」。
    // 页面入口（GlSpecial.vue「缴纳」按钮）与前端调用环节（api.js glexApi）同步删除；库里 wf_model 的 GL_TAX_PAY
    // 模型行与 bpmn-repo 流程定义属数据/文件面，本次不动（历史实例仍须可查，摘模型行需另裁）。
    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlTaxProvision>().eq(GlTaxProvision::getPeriod, period));
    }

    /** 税金金额公式强校验：taxAmount = baseAmount × rate / 100，且基数 ≥ 0、税率 > 0。 */
    private static BigDecimal calcTaxAmount(BigDecimal baseAmount, BigDecimal rate) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "计税基数不能为负: " + baseAmount);
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税率必须 > 0: " + rate);
        }
        return baseAmount.multiply(rate)
                .divide(new BigDecimal("100"), MathContext.DECIMAL128)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
