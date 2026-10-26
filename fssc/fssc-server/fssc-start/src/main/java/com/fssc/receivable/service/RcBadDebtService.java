// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcBadDebtMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 坏账计提服务：按逾期账龄计提坏账并落 RcBadDebt，同一应收单同一期间仅计提一次。
 * 状态：PROVISIONED 已计提 / REVERSED 已转回。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RcBadDebtService {

    private final RcBadDebtMapper mapper;
    private final RcReceivableMapper receivableMapper;
    private final WfEngineFacade wfEngineFacade;
    private final OpVoucherMapper voucherMapper;
    private final OpVoucherLineMapper voucherLineMapper;
    private final MdSubjectMapper subjectMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    private final com.fssc.system.service.ConfigService configService;
    /** P4-3 审计扩面：单据状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** R5-D-097：公司维度读取守卫（坏账计提列表接 OrgGuard，与应收单同一规则） */
    private final com.fssc.framework.org.OrgGuard orgGuard;
    /** R5-D-094：客户主数据解析（列表补客户名称，前端不裸出 FK id） */
    private final CrmCustomerMapper crmCustomerMapper;

    /**
     * 逾期账龄 → 计提比例。sys_config 可配（baddebt.rate.{365|180|90|30|0}，百分数如 100/50/30/10/5），
     * 未配置回退行业经验值；同一应收单同一期仅计提一次。
     */
    private BigDecimal rateByAging(long overdueDays) {
        String band = overdueDays >= 365 ? "365" : overdueDays >= 180 ? "180"
                : overdueDays >= 90 ? "90" : overdueDays >= 30 ? "30" : "0";
        String key = "baddebt.rate." + band;
        String v = configService.getValue(key);
        if (v != null && !v.isBlank()) {
            try {
                return new BigDecimal(v.trim()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                log.warn("坏账计提比例配置 {} 非法值 {}，回退默认", key, v);
            }
        }
        if (overdueDays >= 365) return new BigDecimal("1.00");
        if (overdueDays >= 180) return new BigDecimal("0.50");
        if (overdueDays >= 90) return new BigDecimal("0.30");
        if (overdueDays >= 30) return new BigDecimal("0.10");
        return new BigDecimal("0.05");
    }

    /**
     * 按逾期账龄计提坏账并落 RcBadDebt。
     *
     * @param period 期间（yyyy-MM 或 yyyyMM）
     * @return 本次计提记录数
     */
    @Transactional
    public int provisionScan(String period) {
        // 期间空值会让"同一应收单同一期仅一次"的去重查询（period = NULL 永不成立）失效，重复计提
        if (period == null || period.isBlank()) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "计提期间必填（yyyy-MM）");
        }
        LocalDate today = LocalDate.now();
        List<RcReceivable> overdue = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getStatus, "OVERDUE")
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .isNotNull(RcReceivable::getDueDate));
        int created = 0;
        Long firstId = null;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (RcReceivable r : overdue) {
            long overdueDays = ChronoUnit.DAYS.between(r.getDueDate(), today);
            Long exist = mapper.selectCount(new LambdaQueryWrapper<RcBadDebt>()
                    .eq(RcBadDebt::getReceivableId, r.getId())
                    .eq(RcBadDebt::getPeriod, period));
            if (exist != null && exist > 0) continue;
            BigDecimal rate = rateByAging(overdueDays);
            BigDecimal amt = r.getRemainAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            RcBadDebt d = new RcBadDebt();
            d.setTenantId(TenantContext.getTenantId());
            d.setCustomerId(r.getCustomerId());
            d.setReceivableId(r.getId());
            d.setAmount(amt);
            d.setProvisionDate(today);
            d.setProvisionRate(rate);
            d.setPeriod(period);
            d.setStatus("PROVISIONED");
            // 多分子公司审批路由 V1.3.17：优先取来源应收单的公司，取不到回退登录用户
            if (d.getCompanyId() == null) {
                Long cid = r.getCompanyId();
                if (cid == null) {
                    com.fssc.framework.security.LoginUser lu = UserContext.get();
                    if (lu != null) cid = lu.getCompanyId();
                }
                d.setCompanyId(cid);
            }
            mapper.insert(d);
            if (firstId == null) firstId = d.getId();
            totalAmt = totalAmt.add(amt);
            created++;
        }
        // P4：坏账计提后发起 OTC06 坏账计提审批流（拆分后：计提走 BAD_DEBT_PROV，确认走 BAD_DEBT_CONFIRM；第一笔计提单为业务主键）
        if (created > 0 && firstId != null) {
            try {
                wfEngineFacade.start("BAD_DEBT_PROV", firstId, userId(),
                        "坏账计提-" + period, totalAmt);
            } catch (Exception ex) {
                log.error("坏账计提 {} 发起审批失败: {}", firstId, ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
        return created;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * #19 坏账转回：状态 PROVISIONED → REVERSED，并生成反向（红字）凭证与计提对冲。
     *
     * <p>计提分录为「借：信用减值损失 6701 / 贷：坏账准备 1231」，
     * 转回即反向「借：坏账准备 1231 / 贷：信用减值损失 6701」，借贷方向与计提相反，
     * 金额为原计提额。凭证落 DRAFT 待审核，不自动记账（避免期间关闭/科目缺失时误记账）。
     *
     * <p>此前 rc_bad_debt 的 REVERSED 状态从未被任何代码置位，转回与总账完全脱节。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverse(Long id) {
        RcBadDebt d = mapper.selectById(id);
        if (d == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "坏账计提记录不存在: " + id);
        }
        if (!"PROVISIONED".equals(d.getStatus())) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "仅已计提(PROVISIONED)的坏账可转回，当前状态: " + d.getStatus());
        }
        Long voucherId = createReversalVoucher(d);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", d.getStatus());
        RcBadDebt u = new RcBadDebt();
        u.setId(id);
        u.setStatus("REVERSED");
        mapper.updateById(u);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "REVERSED");
        auditTrailService.recordUpdate("RC_BAD_DEBT", id, null, "rc_bad_debt",
                String.valueOf(id), auditBefore, auditAfter, "坏账转回");

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("badDebtId", id);
        res.put("amount", d.getAmount());
        res.put("voucherId", voucherId);
        res.put("status", "REVERSED");
        log.info("坏账转回 id={} 金额={} 生成反向凭证 voucherId={}", id, d.getAmount(), voucherId);
        return res;
    }

    /** 坏账确认（BAD_DEBT_CONFIRM，OTC06 第2阶段）：坏账申请报告经讨论/批准后确认核销，发起坏账确认审批流。 */
    public void confirm(Long id, String reason) {
        RcBadDebt d = mapper.selectById(id);
        if (d == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "坏账计提记录不存在: " + id);
        }
        if ("CONFIRMED".equals(d.getStatus())) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "该坏账已确认，请勿重复操作");
        }
        try {
            wfEngineFacade.start("BAD_DEBT_CONFIRM", id, userId(),
                    "坏账确认-" + d.getCustomerId() + (reason == null ? "" : "-" + reason), d.getAmount());
        } catch (Exception ex) {
            log.error("坏账 {} 发起确认审批失败: {}", id, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /** 生成坏账转回的反向凭证（借：坏账准备 1231 / 贷：信用减值损失 6701） */
    private Long createReversalVoucher(RcBadDebt d) {
        LocalDate today = LocalDate.now();
        BigDecimal amt = d.getAmount() == null ? BigDecimal.ZERO : d.getAmount();

        OpVoucher v = new OpVoucher();
        // op_voucher.company_id NOT NULL：应收/坏账域无公司维度字段，取当前租户首个公司归属
        SysCompany comp = sysCompanyMapper.selectOne(new LambdaQueryWrapper<SysCompany>()
                .orderByAsc(SysCompany::getId).last("limit 1"));
        if (comp == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "本租户未维护公司档案，无法生成坏账转回凭证");
        }
        v.setCompanyId(comp.getId());
        v.setTenantId(TenantContext.getTenantId());
        v.setVoucherNo(nextBadDebtVoucherNo());
        v.setVoucherWord("转");
        v.setVoucherDate(today);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setSourceType("BAD_DEBT_REVERSE");
        v.setBillId(d.getId());
        v.setSummary("坏账转回-应收单" + d.getReceivableId());
        v.setDebitTotal(amt);
        v.setCreditTotal(amt);
        v.setIsBalanced(amt.compareTo(amt) == 0 ? 1 : 0);
        v.setStatus("DRAFT");
        v.setRemark("由坏账计提记录 " + d.getId() + " 转回自动生成（红字对冲）");
        voucherMapper.insert(v);

        // 借：坏账准备 1231（与计提贷方对冲）
        addLine(v.getId(), 1, "1231", "坏账准备", "DEBIT", amt, d.getCustomerId());
        // 贷：信用减值损失 6701（与计提借方对冲）
        addLine(v.getId(), 2, "6701", "信用减值损失", "CREDIT", amt, d.getCustomerId());
        return v.getId();
    }

    /** 凭证分录：按科目代码解析科目ID；科目未配置则报错，避免生成无科目的脏凭证 */
    private void addLine(Long voucherId, int lineNo, String subjectCode, String subjectName,
                         String direction, BigDecimal amount, Long customerId) {
        MdSubject s = subjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, subjectCode).last("limit 1"));
        if (s == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "科目 " + subjectCode + "(" + subjectName + ") 未配置，无法生成坏账转回凭证，请先维护科目表");
        }
        OpVoucherLine l = new OpVoucherLine();
        l.setTenantId(TenantContext.getTenantId());
        l.setVoucherId(voucherId);
        l.setLineNo(lineNo);
        l.setSummary("坏账转回");
        l.setSubjectId(s.getId());
        l.setSubjectCode(s.getSubjectCode());
        l.setSubjectName(s.getSubjectName());
        l.setDirection(direction);
        l.setAmount(amount);
        l.setCurrencyCode("CNY");
        l.setExchangeRate(BigDecimal.ONE);
        l.setOrigAmount(amount);
        voucherLineMapper.insert(l);
    }

    /**
     * 坏账核销（蓝图流程22，AR-16-2）：前提=该应收已完成坏账计提；
     * 核销金额不能超过该客户已计提坏账准备余额（累计计提-累计核销）。
     * 账务处理：借 1231 坏账准备 / 贷 1122 应收账款，并按核销额冲减应收单余额。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> writeOff(Long id, BigDecimal amount, String reason, String basisNo) {
        // 核销原因与认定依据是坏账税前列销/审计的凭据，缺任一条都不能核销（页面红星同源）
        if (reason == null || reason.isBlank()) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "核销原因必填");
        }
        if (basisNo == null || basisNo.isBlank()) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "认定依据编号必填（破产说明等《坏账认定依据》编号）");
        }
        RcBadDebt d = mapper.selectById(id);
        if (d == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "坏账计提记录不存在: " + id);
        }
        if ("WRITTEN_OFF".equals(d.getStatus())) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "该坏账已核销，请勿重复操作");
        }
        if (!"PROVISIONED".equals(d.getStatus())) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "仅已计提(PROVISIONED)的坏账可核销，当前状态: " + d.getStatus());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "核销金额必须大于0");
        }
        // 客户维度坏账准备余额 = 累计计提 - 累计核销（蓝图规则：核销金额不能超过已计提的坏账准备余额）
        List<RcBadDebt> customerRows = mapper.selectList(new LambdaQueryWrapper<RcBadDebt>()
                .eq(RcBadDebt::getCustomerId, d.getCustomerId()));
        BigDecimal provisioned = customerRows.stream()
                .filter(r -> "PROVISIONED".equals(r.getStatus()) || "WRITTEN_OFF".equals(r.getStatus()))
                .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal writtenOff = customerRows.stream()
                .filter(r -> "WRITTEN_OFF".equals(r.getStatus()))
                .map(r -> r.getWriteoffAmount() == null ? BigDecimal.ZERO : r.getWriteoffAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = provisioned.subtract(writtenOff);
        if (amount.compareTo(balance) > 0) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "核销金额(" + amount + ")超过该客户坏账准备余额(" + balance + ")");
        }
        RcReceivable receivable = d.getReceivableId() == null ? null : receivableMapper.selectById(d.getReceivableId());
        String voucherNo = createWriteoffVoucher(d, amount, basisNo);
        if (receivable != null) {
            BigDecimal remain = receivable.getRemainAmount() == null ? BigDecimal.ZERO : receivable.getRemainAmount();
            BigDecimal cut = remain.min(amount);
            java.util.Map<String, Object> recAuditBefore = new java.util.LinkedHashMap<>();
            recAuditBefore.put("remainAmount", receivable.getRemainAmount());
            java.util.Map<String, Object> recAuditAfter = new java.util.LinkedHashMap<>();
            recAuditAfter.put("remainAmount", remain.subtract(cut));
            RcReceivable u = new RcReceivable();
            u.setId(receivable.getId());
            u.setRemainAmount(remain.subtract(cut));
            if (remain.subtract(cut).compareTo(BigDecimal.ZERO) == 0) {
                recAuditBefore.put("status", receivable.getStatus());
                recAuditAfter.put("status", "DONE");
                u.setStatus("DONE");
            }
            receivableMapper.updateById(u);
            auditTrailService.recordUpdate("RC_RECEIVABLE", receivable.getId(), receivable.getBillNo(), "rc_receivable",
                    String.valueOf(receivable.getId()), recAuditBefore, recAuditAfter, "坏账核销冲减应收余额");
        }
        LocalDate today = LocalDate.now();
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", d.getStatus());
        auditBefore.put("writeoffAmount", d.getWriteoffAmount());
        auditBefore.put("writeoffDate", d.getWriteoffDate());
        auditBefore.put("writeoffReason", d.getWriteoffReason());
        auditBefore.put("basisNo", d.getBasisNo());
        auditBefore.put("writeoffVoucherNo", d.getWriteoffVoucherNo());
        RcBadDebt u = new RcBadDebt();
        u.setId(id);
        u.setStatus("WRITTEN_OFF");
        u.setWriteoffAmount(amount);
        u.setWriteoffDate(today);
        u.setWriteoffReason(reason);
        u.setBasisNo(basisNo);
        u.setWriteoffVoucherNo(voucherNo);
        mapper.updateById(u);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "WRITTEN_OFF");
        auditAfter.put("writeoffAmount", amount);
        auditAfter.put("writeoffDate", today);
        auditAfter.put("writeoffReason", reason);
        auditAfter.put("basisNo", basisNo);
        auditAfter.put("writeoffVoucherNo", voucherNo);
        auditTrailService.recordUpdate("RC_BAD_DEBT", id, null, "rc_bad_debt",
                String.valueOf(id), auditBefore, auditAfter, "坏账核销");

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("badDebtId", id);
        res.put("writeoffAmount", amount);
        res.put("voucherNo", voucherNo);
        res.put("status", "WRITTEN_OFF");
        log.info("坏账核销 id={} 金额={} 凭证 {}，认定依据 {}", id, amount, voucherNo, basisNo);
        return res;
    }

    /** 坏账核销凭证：借 1231 坏账准备 / 贷 1122 应收账款（ERP坏账核销账务处理） */
    private String createWriteoffVoucher(RcBadDebt d, BigDecimal amount, String basisNo) {
        OpVoucher dup = voucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, "BAD_DEBT_WRITEOFF")
                .eq(OpVoucher::getSourceDocId, d.getId())
                .ne(OpVoucher::getStatus, "VOID")
                .last("LIMIT 1"));
        if (dup != null) {
            return dup.getVoucherNo();
        }
        SysCompany comp = sysCompanyMapper.selectOne(new LambdaQueryWrapper<SysCompany>()
                .orderByAsc(SysCompany::getId).last("limit 1"));
        if (comp == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "本租户未维护公司档案，无法生成坏账核销凭证");
        }
        OpVoucher v = new OpVoucher();
        v.setCompanyId(comp.getId());
        v.setTenantId(d.getTenantId());
        v.setVoucherNo(nextBadDebtVoucherNo());
        v.setVoucherWord("转");
        v.setVoucherDate(LocalDate.now());
        v.setPeriodYear(LocalDate.now().getYear());
        v.setPeriodMonth(LocalDate.now().getMonthValue());
        v.setSourceType("BAD_DEBT_WRITEOFF");
        v.setBillId(d.getId());
        v.setSummary("坏账核销-应收单" + d.getReceivableId()
                + (basisNo == null || basisNo.isBlank() ? "" : "（依据 " + basisNo + "）"));
        v.setDebitTotal(amount);
        v.setCreditTotal(amount);
        v.setIsBalanced(1);
        v.setStatus("DRAFT");
        voucherMapper.insert(v);
        addLine(v.getId(), 1, "1231", "坏账准备", "DEBIT", amount, d.getCustomerId());
        addLine(v.getId(), 2, "1122", "应收账款", "CREDIT", amount, d.getCustomerId());
        return v.getVoucherNo();
    }

    public List<RcBadDebt> listByPeriod(String period) {
        LambdaQueryWrapper<RcBadDebt> qw = new LambdaQueryWrapper<>();
        if (period != null && !period.isEmpty()) {
            // R5-D-098：账期入参归一——yyyyMM/yyyy-MM-dd/yyyyMMdd 与库内 yyyy-MM/yyyyMM 两种历史形态对齐；
            // 原实现按原样 eq，前端 YYYYMM 直查 yyyy-MM 历史行恒空。
            String digits = period.replaceAll("[^0-9]", "");
            if (digits.length() >= 6) {
                String ym = digits.substring(0, 4) + "-" + digits.substring(4, 6);
                String compact = digits.substring(0, 4) + digits.substring(4, 6);
                qw.in(RcBadDebt::getPeriod, ym, compact);
            } else {
                qw.eq(RcBadDebt::getPeriod, period);
            }
        }
        // R5-D-097：公司维度读取过滤，与 RcReceivableService.page 同款守卫同一规则（rc_bad_debt.company_id 为直接列）
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(RcBadDebt::getCompanyId, scopeC);
        }
        List<RcBadDebt> list = mapper.selectList(qw.orderByDesc(RcBadDebt::getId));
        // R5-D-094：列表补全客户名称/应收单号（前端不再裸出 customerId/receivableId 主键）
        for (RcBadDebt d : list) {
            if (d.getCustomerId() != null) {
                CrmCustomer c = crmCustomerMapper.selectById(d.getCustomerId());
                if (c != null) d.setCustomerName(c.getCustomerName());
            }
            if (d.getReceivableId() != null) {
                RcReceivable r = receivableMapper.selectById(d.getReceivableId());
                if (r != null) d.setReceivableBillNo(r.getBillNo());
            }
        }
        return list;
    }

    private String nextBadDebtVoucherNo() {
        try {
            return "BD-" + numberRuleService.next("BAD_DEBT");
        } catch (Exception e) {
            return "BD-" + System.currentTimeMillis();
        }
    }
}