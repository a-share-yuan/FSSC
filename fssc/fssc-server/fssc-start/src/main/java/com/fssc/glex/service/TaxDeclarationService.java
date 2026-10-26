// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.mapper.TaxDeclarationMapper;
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
 * 全税种申报服务（P1-3 税务一体化）。
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
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    /** P4-3 审计扩面：单据状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

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
        record.setDeclarationNo(java.util.Optional.ofNullable(record.getDeclarationNo())
                .orElseGet(() -> { try { return numberRuleService.next("TAX_DECLARATION"); }
                    catch (Exception e) { return "TX" + System.currentTimeMillis(); } }));
        record.setStatus("DRAFT");
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

    // ROOT-11（人工裁决 09-27 第三轮·明写自定）：下面四个状态跃迁都是"写 tax_declaration 一张业务表
    // + 写 audit_trail 一条记录"成对写入数据库，没有事务边界时记录写失败会留下"状态跃迁了却查不到谁跃的"的
    // 半截记录。两套外部资料都不覆盖"哪些方法必须有事务边界"（最近旁证只有〔丙·开发指南〕把状态
    // 回写分"聚合 VO／数据库表"两层），故本条按自定处理，不写成对比结论。
    // 单表写、无记录的方法一律不补（同一裁定的另一半）。
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
        // P4-3 审计记录：状态跃迁 DRAFT → PENDING
        java.util.Map<String, Object> auditBefore = java.util.Collections.singletonMap("status", row.getStatus());
        java.util.Map<String, Object> auditAfter = java.util.Collections.singletonMap("status", "PENDING");
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "提交税务申报（DRAFT→PENDING）");
    }

    /**
     * 审批通过：PENDING → APPROVED。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        TaxDeclaration row = requireRow(id);
        if (!"PENDING".equals(row.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报 " + row.getDeclarationNo() + " 当前状态不可审批");
        }
        TaxDeclaration upd = new TaxDeclaration();
        upd.setId(id);
        upd.setStatus("APPROVED");
        declarationMapper.updateById(upd);
        log.info("税务申报 {} 审批通过", row.getDeclarationNo());
        // P4-3 审计记录：状态跃迁 PENDING → APPROVED
        java.util.Map<String, Object> auditBefore = java.util.Collections.singletonMap("status", row.getStatus());
        java.util.Map<String, Object> auditAfter = java.util.Collections.singletonMap("status", "APPROVED");
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "审批通过税务申报（PENDING→APPROVED）");
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
        // P4-3 审计记录：状态跃迁 APPROVED → FILED，并记录申报日期变化
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", row.getStatus());
        auditBefore.put("declareDate", row.getDeclareDate());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "FILED");
        auditAfter.put("declareDate", upd.getDeclareDate());
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "确认申报（APPROVED→FILED）");
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
        // P4-3 审计记录：状态跃迁 FILED → PAID，并记录已缴/余额金额变化
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", row.getStatus());
        auditBefore.put("paidAmount", row.getPaidAmount());
        auditBefore.put("balanceAmount", row.getBalanceAmount());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "PAID");
        auditAfter.put("paidAmount", upd.getPaidAmount());
        auditAfter.put("balanceAmount", upd.getBalanceAmount());
        auditTrailService.recordUpdate("GL_TAX", id, row.getDeclarationNo(), "tax_declaration",
                String.valueOf(id), auditBefore, auditAfter, "确认缴纳（FILED→PAID）");
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
