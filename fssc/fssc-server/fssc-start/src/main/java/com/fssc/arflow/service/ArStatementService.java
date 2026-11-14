package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.masterdata.service.ExchangeRateService;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.system.service.PeriodLockService;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户对账单服务（蓝图 SCC_AR_01/02/03）：模板导入（必含客户代码）→
 * 容差核对（对账单价税合计 vs 该客户未清应收金额，参数 ar.recon.tolerance-amt-pct）→
 * 核对失败两路处理：重新导入 或 财务调整确认（ERP折扣/索赔/溢交调整后）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArStatementService {

    public static final String CFG_TOL_AMT = "ar.recon.tolerance-amt-pct";
    public static final String CFG_TOL_TAX = "ar.recon.tolerance-tax-pct";

    private final ArStatementMapper mapper;
    private final PeriodLockService periodLockService;
    private final RcReceivableMapper receivableMapper;
    private final CrmCustomerMapper customerMapper;
    private final ConfigService configService;

    /**
     * 汇率主数据（md_exchange_rate）：外币折 RMB 的<b>首选</b>来源。
     * 替代 sys_config 硬编码键 {@code ar.fx.{ccy}}（见 {@link #applyFx}）。
     */
    private final ExchangeRateService exchangeRateService;

    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 导入对账单：必含客户代码；外币按 sys_config 汇率 ar.fx.{ccy} 折RMB。 */
    @Transactional(rollbackFor = Exception.class)
    public ArStatement importBill(ArStatement bill) {

        if (bill.getCustomerCode() == null || bill.getCustomerCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单导入必须包含客户代码（蓝图批注强制要求）");
        }
        // 共享服务操作人常跨公司作业：单据公司须取业务所属公司，不能沿用登录人公司
        bill.setCompanyId(resolveStatementCompany(bill));
        // 蓝图规则6.5：账期已被月结关闭时禁止录入当月业务
        periodLockService.assertNotLocked(bill.getCompanyId(), YearMonth.now().toString(), "导入对账单");
        if (bill.getAmount() == null || bill.getTaxAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单金额与税额必填");
        }
        bill.setId(null);
        bill.setBillNo("ARB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        if (bill.getCcy() == null || bill.getCcy().isBlank()) {
            bill.setCcy("CNY");
        }
        if (bill.getTotalAmount() == null) {
            bill.setTotalAmount(bill.getAmount().add(bill.getTaxAmount()));
        }
        applyFx(bill);
        bill.setStatus(ArStatement.ST_IMPORTED);
        bill.setCheckStatus("UNCHECKED");
        mapper.insert(bill);
        return bill;
    }

    /**
     * 对账单归属公司：调用方指定的公司确有该客户未清应收时沿用；
     * 否则取该客户未清应收的唯一所属公司（共享服务跨公司录入场景）。
     * 多公司并存时保留指定值，由核对环节报公司范围差异。
     */
    private Long resolveStatementCompany(ArStatement bill) {
        CrmCustomer customer = customerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getCustomerCode, bill.getCustomerCode())
                .last("limit 1"));
        if (customer == null) {
            return bill.getCompanyId();
        }
        LambdaQueryWrapper<RcReceivable> qw = new LambdaQueryWrapper<RcReceivable>()
                .select(RcReceivable::getCompanyId)
                .eq(RcReceivable::getCustomerId, customer.getId())
                .ne(RcReceivable::getStatus, "DONE");
        if (bill.getCcy() != null && !bill.getCcy().isBlank()) {
            qw.eq(RcReceivable::getCurrencyCode, bill.getCcy());
        }
        List<Long> companies = receivableMapper.selectList(qw).stream()
                .map(RcReceivable::getCompanyId).filter(Objects::nonNull).distinct().toList();
        if (companies.contains(bill.getCompanyId())) {
            return bill.getCompanyId();
        }
        return companies.size() == 1 ? companies.get(0) : bill.getCompanyId();
    }

    /** 数据核对：对账单价税合计与该客户（同公司）未清应收金额按容差比对。 */
    @Transactional(rollbackFor = Exception.class)
    public ArStatement check(Long id) {
        ArStatement bill = require(id);
        if (ArStatement.ST_RECONCILED.equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单 " + bill.getBillNo() + " 已对账完成");
        }
        List<String> issues = new ArrayList<>();
        // 对账单持客户编码，应收明细持 crm_customer.id：必须先经客户档案换算，直接比编码恒零行
        CrmCustomer customer = customerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getCustomerCode, bill.getCustomerCode())
                .last("limit 1"));
        if (customer == null) {
            issues.add("客户代码 " + bill.getCustomerCode() + " 不存在于客户档案");
        } else {
            LambdaQueryWrapper<RcReceivable> qw = new LambdaQueryWrapper<RcReceivable>()
                    .eq(RcReceivable::getCustomerId, customer.getId())
                    .ne(RcReceivable::getStatus, "DONE");
            if (bill.getCompanyId() != null) {
                qw.eq(RcReceivable::getCompanyId, bill.getCompanyId());
            }
            if (bill.getCcy() != null && !bill.getCcy().isBlank()) {
                qw.eq(RcReceivable::getCurrencyCode, bill.getCcy());
            }
            List<RcReceivable> receivables = receivableMapper.selectList(qw);
            if (receivables.isEmpty()) {
                issues.add("无该客户的未清应收明细（发运数据未接收？）");
            } else {
                // 应收单金额为价税合计规则（无独立税额列），故与对账单价税合计整单比对
                BigDecimal sumAmount = receivables.stream()
                        .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal statementTotal = bill.getTotalAmount() != null
                        ? bill.getTotalAmount() : bill.getAmount().add(bill.getTaxAmount());
                compare("价税合计", statementTotal, sumAmount, tolerance(CFG_TOL_AMT), issues);
            }
        }
        bill.setCheckTime(LocalDateTime.now());
        String auditOldStatus = bill.getStatus();
        String auditOldCheckStatus = bill.getCheckStatus();
        String auditOldDiffReason = bill.getDiffReason();
        if (issues.isEmpty()) {
            bill.setCheckStatus("CHECK_OK");
            bill.setDiffReason(null);
            bill.setStatus(ArStatement.ST_RECONCILED);
        } else {
            bill.setCheckStatus("CHECK_FAILED");
            bill.setDiffReason(String.join("；", issues));
            bill.setStatus(ArStatement.ST_ABNORMAL);
        }
        mapper.updateById(bill);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("checkStatus", auditOldCheckStatus);
        auditBefore.put("diffReason", auditOldDiffReason);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", bill.getStatus());
        auditAfter.put("checkStatus", bill.getCheckStatus());
        auditAfter.put("diffReason", bill.getDiffReason());
        auditTrailService.recordUpdate("AR_STATEMENT", bill.getId(), bill.getBillNo(), "ar_statement",
                String.valueOf(bill.getId()), auditBefore, auditAfter, "对账单数据核对");
        return bill;
    }

    /** 财务调整完成确认（蓝图6.2两路处理之二）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArStatement adjust(Long id, String note) {
        ArStatement bill = require(id);
        if (!ArStatement.ST_ABNORMAL.equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅对账异常单据可执行财务调整确认");
        }
        String auditOldStatus = bill.getStatus();
        String auditOldCheckStatus = bill.getCheckStatus();
        String auditOldDiffReason = bill.getDiffReason();
        bill.setStatus(ArStatement.ST_RECONCILED);
        bill.setCheckStatus("CHECK_OK");
        bill.setDiffReason("财务调整后确认：" + (note == null ? "" : note));
        mapper.updateById(bill);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("checkStatus", auditOldCheckStatus);
        auditBefore.put("diffReason", auditOldDiffReason);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", bill.getStatus());
        auditAfter.put("checkStatus", bill.getCheckStatus());
        auditAfter.put("diffReason", bill.getDiffReason());
        auditTrailService.recordUpdate("AR_STATEMENT", bill.getId(), bill.getBillNo(), "ar_statement",
                String.valueOf(bill.getId()), auditBefore, auditAfter, "对账异常财务调整确认");
        return bill;
    }

    /** 明细 JSON 回写（Excel 模板导入后调用）。 */
    public void updateLinesJson(ArStatement u) {
        mapper.updateById(u);
    }

    public List<ArStatement> list(String status, String customerName) {
        LambdaQueryWrapper<ArStatement> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ArStatement::getStatus, status);
        }
        if (customerName != null && !customerName.isBlank()) {
            q.like(ArStatement::getCustomerName, customerName);
        }
        return mapper.selectList(q.orderByDesc(ArStatement::getId));
    }

    // 工具

    private void applyFx(ArStatement bill) {
        boolean foreign = !"CNY".equalsIgnoreCase(bill.getCcy());
        if (!foreign) {
            bill.setRmbAmount(bill.getAmount());
            bill.setRmbTaxAmount(bill.getTaxAmount());
            return;
        }
        BigDecimal rate = resolveFxRate(bill.getCcy(), bill.getCompanyId());
        bill.setFxRate(rate);
        bill.setRmbAmount(bill.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        bill.setRmbTaxAmount(bill.getTaxAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 解析外币→CNY 汇率（改造：汇率表优先，硬编码键降级为兜底）。
     *
     * <p><b>原先</b>：只读 {@code sys_config} 的 {@code ar.fx.{ccy}} —— 4 条写死参数，
     * 无生效期、换汇比要改库、且与已有的汇率主数据表 {@code md_exchange_rate} 各干各的。</p>
     *
     * <p><b>改造后</b>查询顺序：
     * <ol>
     *   <li>汇率表 {@code md_exchange_rate}，按对账日期取当期生效价
     *       （{@code ExchangeRateService.lookup} 内已支持直查→反向→经 CNY 交叉）；</li>
     *   <li>汇率表无当期价时，回退到 {@code sys_config} 的 {@code ar.fx.{ccy}}（兼容历史，
     *       并打 warn 提示该币种汇率表未维护）；</li>
     *   <li>两者皆无才抛错，错误信息同时给出两条可操作的维护路径。</li>
     * </ol>
     * 这样"客户改汇率"从改库变成维护界面（汇率管理页已就绪），且历史单据不受影响
     * ——因为每张单据都把当次 fxRate 快照进了 {@code bill.fx_rate}。
     */
    private BigDecimal resolveFxRate(String ccy, Long companyId) {
        String cur = ccy == null ? "" : ccy.trim().toUpperCase();
        LocalDate bizDate = LocalDate.now();

        BigDecimal fromTable = null;
        try {
            fromTable = exchangeRateService.lookup(cur, "CNY", bizDate);
        } catch (Exception e) {
            log.warn("[fx] 汇率表查询异常 ccy={} date={}：{}", cur, bizDate, e.getMessage());
        }
        if (fromTable != null && fromTable.signum() > 0) {
            log.debug("[fx] 汇率表命中 ccy={} date={} rate={}", cur, bizDate, fromTable);
            return fromTable;
        }

        String rateStr = configService.getValue("ar.fx." + cur);
        if (rateStr != null && !rateStr.isBlank()) {
            log.warn("[fx] 汇率表未维护 {}，已回退 sys_config 参数 ar.fx.{}（建议改维护汇率表）", cur, cur);
            return new BigDecimal(rateStr.trim());
        }

        throw new BizException(ErrorCode.PARAM_INVALID,
                "外币对账单缺少 " + cur + "→CNY 汇率：请在「汇率管理」维护 " + cur + "/CNY 并设生效日期"
                        + "（或临时维护全局参数 ar.fx." + cur + "）");
    }

    /** 容差：百分比（如 0.1 表示 0.1%），缺省严格 0。 */
    private BigDecimal tolerance(String key) {
        String v = configService.getValue(key);
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(v.trim()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private void compare(String label, BigDecimal expect, BigDecimal actual,
                         BigDecimal tolerance, List<String> issues) {
        if (expect == null || actual == null) {
            issues.add(label + "缺少比对数据");
            return;
        }
        BigDecimal diff = expect.subtract(actual).abs();
        BigDecimal allowed = tolerance.multiply(expect.abs().max(BigDecimal.ONE));
        if (diff.compareTo(allowed) > 0) {
            issues.add(label + "超容差（对账单 " + expect + " vs 应收明细 " + actual + "）");
        }
    }

    private ArStatement require(Long id) {
        ArStatement b = mapper.selectById(id);
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单不存在 id=" + id);
        }
        return b;
    }
}
