// SPDX-License-Identifier: Apache-2.0
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
import com.fssc.gl.mapper.GlEntryMapper;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
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

/** 税金计提服务（gl_tax_provision，按会计期间批量计提税金）。 */
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

    /** 税率常量：增值税13%、附加12%、所得税25%（百分比） */
    private static final BigDecimal RATE_VAT = new BigDecimal("13.00");
    private static final BigDecimal RATE_SURCHARGE = new BigDecimal("12.00");
    private static final BigDecimal RATE_INCOME = new BigDecimal("25.00");

    /** 各税种关联科目（简化为预置科目id，实际应查 md_subject） */
    private static final Long SUBJECT_VAT = 222105L;
    private static final Long SUBJECT_SURCHARGE = 222101L;
    private static final Long SUBJECT_INCOME = 222106L;

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

    /**
     * 按期间批量计提税金：对增值税/附加/所得税各计提一条，税额=计税基数×税率，
     * 关联科目并生成凭证号。
     * <p>逐条起流，不能"3 条记录只挂第 1 条"。
     * 若循环落 3 行后只以 {@code created.get(0).getId()} 起一条 GL_TAX_PROV，
     * 另外两条就无流程、无审批、无凭证；
     * 且流程金额取三条合计（26,940）而审批屏展示第 1 条（13,000），审批人审的与被背书的不是同一件事。
     * <p>现在：每行一个审批对象（bizId=本行 id、金额=本行税额、标题带税种），
     * 任一行起流失败即整体回滚（不留半挂接批次），并在收尾做<b>漏挂断言</b>
     * （见 {@link #assertAllLinked}）——条数与流程实例数不等就抛错回滚，绝不静默。
     * <p>未选"一张汇总单挂 3 条明细"方案：那要新建头行表（DDL），而逐条起流零 DDL、
     * 且与本表既有"一行一税种一凭证"的行级语义一致（回写路径 GlBizAction.handleTax 本就按 bizId 逐条出凭证）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<GlTaxProvision> provision(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        Long tenantId = TenantContext.getTenantId();
        // 组织合法性守卫：记账主体绑定登录公司（集团级=空，保持旧行为）
        Long cid = orgGuard.currentCompany();
        orgGuard.assertCompanyAllowed(cid);
        long seq = countByPeriod(period) + 1;
        List<GlTaxProvision> created = new ArrayList<>();
        for (ProvSpec spec : specs) {
            GlTaxProvision e = new GlTaxProvision();
            e.setTenantId(tenantId);
            e.setCompanyId(cid);
            e.setPeriod(period);
            e.setTaxType(spec.taxType);
            e.setSubjectId(spec.subjectId);
            e.setBaseAmount(spec.baseAmount);
            e.setRate(spec.rate);
            // 税金金额公式强校验：taxAmount = baseAmount × rate / 100（四舍五入取 2 位）
            e.setTaxAmount(calcTaxAmount(spec.baseAmount, spec.rate));
            e.setProvisionNo("TX" + period + String.format("%03d", seq++));
            // 提交只建在途态——旧写法 insert 前硬置 POSTED＋预造 GLTX 号＝提交瞬间即最终状态、
            // 审批链退化为事后追认。最终状态（POSTED/APPROVED/REJECTED）与凭证真号（JZ…）由
            // GlBizAction.handleTax 在流程办结时回写。
            e.setStatus("APPROVING");
            e.setVoucherNo(null);
            mapper.insert(e);
            created.add(e);
        }
        // 税金计提即发起 GL_TAX_PROV 税金计提审批流（拆分后：计提走 GL_TAX_PROV，支付走 GL_TAX_PAY）
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
                return "所得税";
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

    private static final List<ProvSpec> specs = Arrays.asList(
            new ProvSpec("VAT", SUBJECT_VAT, new BigDecimal("100000.00"), RATE_VAT),
            new ProvSpec("SURCHARGE", SUBJECT_SURCHARGE, new BigDecimal("12000.00"), RATE_SURCHARGE),
            new ProvSpec("INCOME", SUBJECT_INCOME, new BigDecimal("50000.00"), RATE_INCOME)
    );

    private static final class ProvSpec {
        final String taxType;
        final Long subjectId;
        final BigDecimal baseAmount;
        final BigDecimal rate;

        ProvSpec(String taxType, Long subjectId, BigDecimal baseAmount, BigDecimal rate) {
            this.taxType = taxType;
            this.subjectId = subjectId;
            this.baseAmount = baseAmount;
            this.rate = rate;
        }
    }
}