// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArWriteoffFund;
import com.fssc.arflow.entity.ArWriteoffRecord;
import com.fssc.arflow.mapper.ArWriteoffFundMapper;
import com.fssc.arflow.mapper.ArWriteoffRecordMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.receivable.service.RcReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 核销工作台服务（蓝图核销管理 9.4，流程15/16/17，状态机4.3）：
 * 待核销发票（未清应收）× 收款/票据资金池 → 手动匹配 / 自动核销（FIFO 按到期日）→
 * 生成收款核销单（rc_receipt+line 强勾稽）并即时冲减 → 核销记录（核销查询页数据源）。
 * 对冲核销（流程17）：同一客户应收应付对冲，借2202/贷1122 同时核销，支持债务抵消协议编号登记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArWriteoffService {

    private final ArWriteoffFundMapper fundMapper;
    private final com.fssc.collab.service.ScpReconService scpReconService;
    private final com.fssc.system.service.PeriodLockService periodLockService;
    private final ArWriteoffRecordMapper recordMapper;
    private final RcReceivableMapper receivableMapper;
    private final RcReceiptService receiptService;
    private final ArFlowVoucherService voucherService;

    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    // ---------------- 资金池 ----------------

    /** 资金登记（收款/票据来源）；票据资金带 noteId 供票据核销归类。 */
    @Transactional(rollbackFor = Exception.class)
    public ArWriteoffFund registerFund(ArWriteoffFund f) {

        // 蓝图规则6.5：账期已被月结关闭时禁止录入当月业务
        periodLockService.assertNotLocked(null, java.time.YearMonth.now().toString(), "登记核销资金");
        if (f.getFundAmount() == null || f.getFundAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金金额必须大于0");
        }
        if (!StringUtils.hasText(f.getFundNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金单号必填（收款单号/票据编号）");
        }
        // 核销按客户匹配应收，客户缺失时自动/手动匹配都无从圈定候选单据
        if (f.getCustomerId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户必填");
        }
        if (ArWriteoffFund.T_NOTE.equals(f.getFundType()) && f.getNoteId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据来源资金必须回指已登记票据（票据ID）");
        }
        Long dup = fundMapper.selectCount(new LambdaQueryWrapper<ArWriteoffFund>()
                .eq(ArWriteoffFund::getFundNo, f.getFundNo())
                .ne(ArWriteoffFund::getStatus, ArWriteoffFund.ST_VOIDED));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金单号 " + f.getFundNo() + " 已登记");
        }
        if (!ArWriteoffFund.T_NOTE.equals(f.getFundType()) && !ArWriteoffFund.T_OFFSET.equals(f.getFundType())) {
            f.setFundType(ArWriteoffFund.T_RECEIPT);
        }
        f.setId(null);
        f.setTenantId(TenantContext.getTenantId());
        f.setRemainAmount(f.getFundAmount());
        if (f.getFundDate() == null) {
            f.setFundDate(LocalDate.now());
        }
        f.setStatus(ArWriteoffFund.ST_AVAILABLE);
        fundMapper.insert(f);
        return f;
    }

    public List<ArWriteoffFund> funds(String fundType, String status) {
        LambdaQueryWrapper<ArWriteoffFund> q = new LambdaQueryWrapper<>();
        if (fundType != null && !fundType.isBlank()) {
            q.eq(ArWriteoffFund::getFundType, fundType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(ArWriteoffFund::getStatus, status);
        }
        return fundMapper.selectList(q.orderByDesc(ArWriteoffFund::getId));
    }

    /** 待核销发票：该客户未清应收（余额>0），按到期日+id 先进先出排序。 */
    public List<RcReceivable> pendingInvoices(Long customerId, String keyword) {
        LambdaQueryWrapper<RcReceivable> q = new LambdaQueryWrapper<>();
        if (customerId != null) {
            q.eq(RcReceivable::getCustomerId, customerId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(RcReceivable::getBillNo, keyword);
        }
        q.gt(RcReceivable::getRemainAmount, BigDecimal.ZERO);
        q.ne(RcReceivable::getStatus, "DONE");
        q.orderByAsc(RcReceivable::getDueDate).orderByAsc(RcReceivable::getId);
        return receivableMapper.selectList(q);
    }

    // ---------------- 匹配核销 ----------------

    /** 自动核销（蓝图：系统自动匹配）：按到期日+id FIFO 拆分资金核销未清应收。 */
    @Transactional(rollbackFor = Exception.class)
    public ArWriteoffRecord autoMatch(Long fundId) {
        ArWriteoffFund fund = requireFund(fundId);
        requireAvailable(fund);
        List<RcReceivable> open = pendingInvoices(fund.getCustomerId(), null);
        if (open.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户 " + nvlName(fund) + " 无待核销应收发票");
        }
        List<RcReceiptLine> lines = new ArrayList<>();
        BigDecimal rest = fund.getRemainAmount();
        for (RcReceivable r : open) {
            if (rest.signum() <= 0) {
                break;
            }
            BigDecimal pay = r.getRemainAmount().min(rest);
            RcReceiptLine line = new RcReceiptLine();
            line.setReceivableId(r.getId());
            line.setOffsetAmount(pay);
            lines.add(line);
            rest = rest.subtract(pay);
        }
        return execute(fund, lines, true, "自动核销(FIFO按到期日)");
    }

    /** 手动匹配核销：lines = [{receivableId, offsetAmount}]，金额校验后按行核销。 */
    @Transactional(rollbackFor = Exception.class)
    public ArWriteoffRecord manualMatch(Long fundId, List<RcReceiptLine> lines) {
        ArWriteoffFund fund = requireFund(fundId);
        requireAvailable(fund);
        if (lines == null || lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手动匹配必须至少选择一张待核销发票");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            RcReceiptLine l = lines.get(i);
            if (l.getReceivableId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行未选择应收单");
            }
            if (l.getOffsetAmount() == null || l.getOffsetAmount().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行核销金额必须大于0");
            }
            RcReceivable rec = receivableMapper.selectById(l.getReceivableId());
            if (rec == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行应收单不存在");
            }
            if (fund.getCustomerId() != null && !fund.getCustomerId().equals(rec.getCustomerId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 行应收单 " + rec.getBillNo() + " 与资金客户不一致");
            }
            if (nvl(rec.getRemainAmount()).compareTo(l.getOffsetAmount()) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 行核销金额超过应收单 " + rec.getBillNo() + " 余额 " + rec.getRemainAmount());
            }
            sum = sum.add(l.getOffsetAmount());
        }
        if (sum.compareTo(fund.getRemainAmount()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "核销合计 " + sum + " 超过资金未核销余额 " + fund.getRemainAmount());
        }
        return execute(fund, lines, false, "手动匹配核销");
    }

    /**
     * 对冲核销（流程17）：同一客户应收应付对冲，虚拟收款按指定明细/FIFO 核销应收，
     * 凭证 借2202应付账款/贷1122应收账款；债务抵消协议编号落资金池。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArWriteoffRecord offsetWriteoff(Long customerId, String customerName, BigDecimal amount,
                                           String agreementNo, List<RcReceiptLine> lines) {

        // 蓝图规则6.5：账期已被月结关闭时禁止录入当月业务
        periodLockService.assertNotLocked(null, java.time.YearMonth.now().toString(), "对冲核销");
        if (customerId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对冲客户必填");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对冲金额必须大于0");
        }
        ArWriteoffFund fund = new ArWriteoffFund();
        fund.setFundNo("DZ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        fund.setFundType(ArWriteoffFund.T_OFFSET);
        fund.setCustomerId(customerId);
        fund.setCustomerName(customerName);
        fund.setFundAmount(amount);
        fund.setRemainAmount(BigDecimal.ZERO);
        fund.setFundDate(LocalDate.now());
        fund.setAgreementNo(agreementNo);
        fund.setStatus(ArWriteoffFund.ST_EXHAUSTED);
        fund.setRemark("对冲核销虚拟收付" + (agreementNo == null || agreementNo.isBlank() ? "" : "，抵消协议 " + agreementNo));
        fundMapper.insert(fund);

        ArWriteoffRecord rec = new ArWriteoffRecord();
        rec.setTenantId(TenantContext.getTenantId());
        rec.setRecordNo(nextRecordNo());
        rec.setWriteoffType(ArWriteoffRecord.T_OFFSET);
        rec.setFundId(fund.getId());
        rec.setFundNo(fund.getFundNo());
        rec.setCustomerId(customerId);
        rec.setCustomerName(customerName);
        rec.setWriteoffAmount(amount);
        rec.setAutoFlag(0);
        rec.setOperator(currentUser());
        rec.setNote("应收应付对冲" + (agreementNo == null || agreementNo.isBlank() ? "" : "，抵消协议 " + agreementNo));
        rec.setStatus(ArWriteoffRecord.ST_PENDING);
        recordMapper.insert(rec);
        String auditOldStatus = rec.getStatus();
        try {
            List<RcReceiptLine> effective = (lines == null || lines.isEmpty())
                    ? fifoLines(customerId, amount) : lines;
            RcReceipt receipt = createAndApplyReceipt(fund, effective);
            rec.setInvoiceNos(invoiceNosOf(effective));
            rec.setReceiptNo(receipt.getBillNo());
            // D-DEC-409（X-28 甲）：出证前已先立到账载体（上面的 rc_receipt 核销单），且对冲腿＝借 2202／贷 1122 不动银行存款，
            // 不属"无到账凭空出核销凭证"那一族；借 1002 的收款核销腿闸在 VoucherService.generateCollectVoucher。
            rec.setVoucherNo(voucherService.generateOffsetVoucher(fund.getTenantId(), companyId(), amount,
                    customerName, rec.getRecordNo(), rec.getId()));
            rec.setStatus(ArWriteoffRecord.ST_DONE);
        } catch (Exception e) {
            rec.setStatus(ArWriteoffRecord.ST_FAILED);
            rec.setFailReason(e.getMessage());
            recordMapper.updateById(rec);
            throw e;
        }
        recordMapper.updateById(rec);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("receiptNo", null);
        auditBefore.put("voucherNo", null);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", rec.getStatus());
        auditAfter.put("receiptNo", rec.getReceiptNo());
        auditAfter.put("voucherNo", rec.getVoucherNo());
        auditTrailService.recordUpdate("AR_WRITEOFF", rec.getId(), rec.getRecordNo(), "ar_writeoff_record",
                String.valueOf(rec.getId()), auditBefore, auditAfter, "对冲核销");
        log.info("[arflow] 对冲核销 {} 完成：客户 {} 金额 {}", rec.getRecordNo(), customerName, amount);
        return rec;
    }

    // ---------------- 核销查询 ----------------

    public List<ArWriteoffRecord> records(String writeoffType, String status, String keyword) {
        LambdaQueryWrapper<ArWriteoffRecord> q = new LambdaQueryWrapper<>();
        if (writeoffType != null && !writeoffType.isBlank()) {
            q.eq(ArWriteoffRecord::getWriteoffType, writeoffType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(ArWriteoffRecord::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ArWriteoffRecord::getRecordNo, keyword)
                    .or().like(ArWriteoffRecord::getCustomerName, keyword)
                    .or().like(ArWriteoffRecord::getFundNo, keyword));
        }
        return recordMapper.selectList(q.orderByDesc(ArWriteoffRecord::getId));
    }

    // ---------------- 内部 ----------------

    /** 执行核销：建收款单（明细强勾稽）→ 即时冲减应收 → 扣资金池余额 → 记录核销结果。 */
    private ArWriteoffRecord execute(ArWriteoffFund fund, List<RcReceiptLine> lines, boolean auto, String note) {
        ArWriteoffRecord rec = new ArWriteoffRecord();
        rec.setTenantId(TenantContext.getTenantId());
        rec.setRecordNo(nextRecordNo());
        rec.setWriteoffType(ArWriteoffFund.T_NOTE.equals(fund.getFundType())
                ? ArWriteoffRecord.T_NOTE : ArWriteoffRecord.T_RECEIPT);
        rec.setFundId(fund.getId());
        rec.setFundNo(fund.getFundNo());
        rec.setCustomerId(fund.getCustomerId());
        rec.setCustomerName(fund.getCustomerName());
        rec.setAutoFlag(auto ? 1 : 0);
        rec.setOperator(currentUser());
        rec.setNote(note);
        try {
            RcReceipt receipt = createAndApplyReceipt(fund, lines);
            rec.setInvoiceNos(invoiceNosOf(lines));
            rec.setReceiptNo(receipt.getBillNo());
            rec.setWriteoffAmount(receipt.getAmount());
            rec.setStatus(ArWriteoffRecord.ST_DONE);
        } catch (Exception e) {
            rec.setStatus(ArWriteoffRecord.ST_FAILED);
            rec.setFailReason(e.getMessage());
            recordMapper.insert(rec);
            throw e;
        }
        recordMapper.insert(rec);
        return rec;
    }

    private RcReceipt createAndApplyReceipt(ArWriteoffFund fund, List<RcReceiptLine> lines) {
        BigDecimal sum = lines.stream().map(l -> nvl(l.getOffsetAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        RcReceipt r = new RcReceipt();
        r.setCustomerId(fund.getCustomerId());
        r.setBillNo(fund.getFundNo() + "-HX");
        r.setReceiptDate(LocalDate.now());
        r.setPayMethod(ArWriteoffFund.T_NOTE.equals(fund.getFundType()) ? "票据" : "银行");
        r.setAmount(sum);
        r.setLines(lines);
        Long receiptId = receiptService.create(r);
        receiptService.applyReceipt(receiptId);
        r.setId(receiptId);
        // AR蓝图状态机4.1：应收全额核销后回写预制发票状态 INVOICED → WRITTEN_OFF
        for (RcReceiptLine l : lines) {
            RcReceivable rec = receivableMapper.selectById(l.getReceivableId());
            if (rec != null) {
                scpReconService.markInvoiceWrittenOff(rec.getBillNo());
            }
        }

        BigDecimal remain = fund.getRemainAmount().subtract(sum);
        ArWriteoffFund u = new ArWriteoffFund();
        u.setId(fund.getId());
        u.setRemainAmount(remain);
        String auditOldFundStatus = fund.getStatus();
        BigDecimal auditOldRemain = fund.getRemainAmount();
        u.setStatus(remain.signum() == 0 ? ArWriteoffFund.ST_EXHAUSTED : ArWriteoffFund.ST_AVAILABLE);
        fundMapper.updateById(u);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldFundStatus);
        auditBefore.put("remainAmount", auditOldRemain);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", u.getStatus());
        auditAfter.put("remainAmount", remain);
        auditTrailService.recordUpdate("AR_WRITEOFF", fund.getId(), fund.getFundNo(), "ar_writeoff_fund",
                String.valueOf(fund.getId()), auditBefore, auditAfter, "核销扣减资金池余额");
        return r;
    }

    private List<RcReceiptLine> fifoLines(Long customerId, BigDecimal amount) {
        List<RcReceiptLine> lines = new ArrayList<>();
        BigDecimal rest = amount;
        for (RcReceivable r : pendingInvoices(customerId, null)) {
            if (rest.signum() <= 0) {
                break;
            }
            BigDecimal pay = r.getRemainAmount().min(rest);
            RcReceiptLine line = new RcReceiptLine();
            line.setReceivableId(r.getId());
            line.setOffsetAmount(pay);
            lines.add(line);
            rest = rest.subtract(pay);
        }
        if (rest.signum() > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该客户待核销应收余额不足以覆盖对冲金额（差 " + rest + "）");
        }
        return lines;
    }

    private String invoiceNosOf(List<RcReceiptLine> lines) {
        List<String> nos = new ArrayList<>();
        for (RcReceiptLine l : lines) {
            RcReceivable rec = receivableMapper.selectById(l.getReceivableId());
            nos.add(rec == null ? String.valueOf(l.getReceivableId()) : rec.getBillNo());
        }
        return String.join(",", nos);
    }

    private ArWriteoffFund requireFund(Long id) {
        ArWriteoffFund f = fundMapper.selectById(id);
        if (f == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金记录不存在 id=" + id);
        }
        return f;
    }

    private void requireAvailable(ArWriteoffFund fund) {
        if (ArWriteoffFund.ST_EXHAUSTED.equals(fund.getStatus()) || fund.getRemainAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金 " + fund.getFundNo() + " 已无可核销余额");
        }
        if (fund.getCustomerId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金 " + fund.getFundNo() + " 未指定客户，无法匹配应收");
        }
    }

    private String nvlName(ArWriteoffFund fund) {
        return fund.getCustomerName() == null ? String.valueOf(fund.getCustomerId()) : fund.getCustomerName();
    }

    private String nextRecordNo() {
        return "HX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String currentUser() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUsername();
    }

    private Long companyId() {
        return 1L;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
