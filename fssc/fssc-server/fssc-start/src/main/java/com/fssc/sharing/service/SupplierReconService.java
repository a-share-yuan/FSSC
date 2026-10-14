// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.sharing.entity.SupplierRecon;
import com.fssc.sharing.entity.SupplierReconDetail;
import com.fssc.sharing.entity.SupplierReconDiff;
import com.fssc.sharing.mapper.SupplierReconDetailMapper;
import com.fssc.sharing.mapper.SupplierReconDiffMapper;
import com.fssc.sharing.mapper.SupplierReconMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商对账（对标金蝶K/3 Cloud应付管理 + SAP F.41/F.13 + 用友U8+应付管理）。
 *
 * 核心流程：
 * 1. 生成对账单：按期间过滤发票和付款，计算期初余额、本期发生额、期末余额，生成逐笔明细
 * 4. 对账函：生成对账函编号，标记已发送状态（SAP供应商确认函）
 * 5. 供应商确认：供应商签章回函/在线确认（预留LinkU门户接口）
 * 7. 确认完成：双方确认一致后放行三单匹配
 *
 * 状态流转：DRAFT 已生成 → SENT 对账函已发送 → CONFIRMED 双方确认 / DIFF 有差异 → RESOLVED 差异已处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierReconService {

    private static final List<String> PAID = List.of("PAID", "SUCCESS");

    private final SupplierReconMapper reconMapper;
    private final SupplierReconDetailMapper detailMapper;
    private final SupplierReconDiffMapper diffMapper;
    private final ExpBillPaymentMapper paymentMapper;
    private final InvInvoiceMapper invoiceMapper;
    private final MdSupplierMapper supplierMapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.system.service.NumberRuleService numberRuleService;

    // ==================== 对账单生成 ====================

    /**
     * 生成/覆盖对账单（同供应商同期间幂等）。
     * 按期间过滤发票和付款，计算期初期末余额，生成逐笔明细。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generate(Long supplierId, String period) {
        MdSupplier sup = supplierMapper.selectById(supplierId);
        if (sup == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商不存在: " + supplierId);
        }
        String name = sup.getSupplierName();

        YearMonth ym = YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();

        // 本期发票（按 invoice_date 过滤期间）
        List<InvInvoice> periodInvoices = invoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getSellerName, name)
                .ge(InvInvoice::getInvoiceDate, periodStart)
                .le(InvInvoice::getInvoiceDate, periodEnd));
        BigDecimal periodPayable = periodInvoices.stream().map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本期已付付款（按 pay_time 过滤期间）
        List<ExpBillPayment> periodPays = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getPayeeName, name)
                .in(ExpBillPayment::getPayStatus, PAID)
                .ge(ExpBillPayment::getPayTime, periodStart.atStartOfDay())
                .le(ExpBillPayment::getPayTime, periodEnd.atTime(23, 59, 59)));
        BigDecimal periodPaid = periodPays.stream().map(p -> nz(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 期初余额 = 截至上期的累计应付 - 累计已付
        List<InvInvoice> beforeInvoices = invoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getSellerName, name)
                .lt(InvInvoice::getInvoiceDate, periodStart));
        BigDecimal beforePayable = beforeInvoices.stream().map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExpBillPayment> beforePays = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getPayeeName, name)
                .in(ExpBillPayment::getPayStatus, PAID)
                .lt(ExpBillPayment::getPayTime, periodStart.atStartOfDay()));
        BigDecimal beforePaid = beforePays.stream().map(p -> nz(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingBalance = beforePayable.subtract(beforePaid);
        BigDecimal closingBalance = openingBalance.add(periodPayable).subtract(periodPaid);

        // 累计应付/已付（向后兼容）
        BigDecimal totalPayable = beforePayable.add(periodPayable);
        BigDecimal totalPaid = beforePaid.add(periodPaid);
        BigDecimal balance = totalPayable.subtract(totalPaid);

        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();

        // 幂等：同供应商同期间已存在则覆盖
        SupplierRecon exist = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getSupplierId, supplierId)
                .eq(SupplierRecon::getPeriod, period)
                .last("limit 1"));

        Long reconId;
        if (exist != null) {
            SupplierRecon upd = new SupplierRecon();
            upd.setId(exist.getId());
            // R5-D-074①：生成（覆盖）时同样冗余回写供应商名称——历史行 supplier_name 为 NULL，
            // 只写 insert 路径会让既有行永远空白；这里按 supplier_id 查到的主数据名回填，治愈旧行。
            upd.setSupplierName(name);
            upd.setPayableAmount(totalPayable);
            upd.setPaidAmount(totalPaid);
            upd.setBalance(balance);
            upd.setOpeningBalance(openingBalance);
            upd.setPeriodPayable(periodPayable);
            upd.setPeriodPaid(periodPaid);
            upd.setClosingBalance(closingBalance);
            upd.setStatus("DRAFT");
            // R5-D-074②：生成态一律 NONE（未发函不得出现「待确认/已驳回」），
            // 供应商确认状态只能由 sendLetter 后（letter_status SENT/RECEIVED）推进。
            upd.setSupplierConfirmStatus("NONE");
            upd.setDiffRemark(null);
            upd.setUpdateBy(userId);
            upd.setUpdateTime(LocalDateTime.now());
            reconMapper.updateById(upd);
            reconId = exist.getId();
            // 清除旧明细，重新生成
            detailMapper.delete(new LambdaQueryWrapper<SupplierReconDetail>().eq(SupplierReconDetail::getReconId, reconId));
        } else {
            SupplierRecon r = new SupplierRecon();
            r.setTenantId(TenantContext.getTenantId());
            r.setSupplierId(supplierId);
            r.setSupplierName(name);
            r.setReconNo(nextReconNo());
            r.setPeriod(period);
            r.setPayableAmount(totalPayable);
            r.setPaidAmount(totalPaid);
            r.setBalance(balance);
            r.setOpeningBalance(openingBalance);
            r.setPeriodPayable(periodPayable);
            r.setPeriodPaid(periodPaid);
            r.setClosingBalance(closingBalance);
            r.setReconType("MANUAL");
            r.setLetterStatus("NONE");
            // R5-D-074②：新建态未发函，供应商确认状态置 NONE（历史库曾出现 letter_status=NONE 而
            // supplier_confirm_status=REJECTED 的状态机矛盾行——没发函不可能有供应商驳回）。
            r.setSupplierConfirmStatus("NONE");
            r.setLinkuSyncStatus("NONE");
            r.setStatus("DRAFT");
            r.setCreateBy(userId);
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            r.setDelFlag(0);
            LoginUser lu = UserContext.get();
            if (lu != null) r.setCompanyId(lu.getCompanyId());
            reconMapper.insert(r);
            reconId = r.getId();
        }

        // 生成逐笔明细：发票
        Long tenantId = TenantContext.getTenantId();
        for (InvInvoice inv : periodInvoices) {
            SupplierReconDetail d = new SupplierReconDetail();
            d.setTenantId(tenantId);
            d.setReconId(reconId);
            d.setItemType("INVOICE");
            d.setItemId(inv.getId());
            d.setItemNo(inv.getInvoiceNo());
            d.setItemDate(inv.getInvoiceDate());
            d.setAmount(inv.getAmount());
            d.setMatchStatus("UNMATCHED");
            detailMapper.insert(d);
        }
        // 生成逐笔明细：付款
        for (ExpBillPayment pay : periodPays) {
            SupplierReconDetail d = new SupplierReconDetail();
            d.setTenantId(tenantId);
            d.setReconId(reconId);
            d.setItemType("PAYMENT");
            d.setItemId(pay.getId());
            d.setItemNo(pay.getPaySerialNo() != null ? pay.getPaySerialNo() : "PAY-" + pay.getId());
            d.setItemDate(pay.getPayTime() != null ? pay.getPayTime().toLocalDate() : null);
            d.setAmount(pay.getAmount());
            d.setMatchStatus("UNMATCHED");
            detailMapper.insert(d);
        }

        return reconId;
    }


    /**
     * 自动勾对：按发票号、金额、PO号规则自动匹配发票与付款。
     * 匹配优先级：1.发票号完全匹配 2.金额完全匹配 3.金额最接近（容差0.01）
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoMatch(Long reconId) {
        List<SupplierReconDetail> invoices = detailMapper.selectList(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, reconId)
                .eq(SupplierReconDetail::getItemType, "INVOICE")
                .eq(SupplierReconDetail::getMatchStatus, "UNMATCHED"));
        List<SupplierReconDetail> payments = detailMapper.selectList(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, reconId)
                .eq(SupplierReconDetail::getItemType, "PAYMENT")
                .eq(SupplierReconDetail::getMatchStatus, "UNMATCHED"));

        int matchedCount = 0;
        // 规则1：按发票号匹配
        for (SupplierReconDetail inv : new ArrayList<>(invoices)) {
            for (SupplierReconDetail pay : new ArrayList<>(payments)) {
                if (pay.getMatchStatus().equals("MATCHED")) continue;
                if (inv.getItemNo() != null && inv.getItemNo().equals(pay.getItemNo())) {
                    doMatch(inv, pay, "INVOICE_NO");
                    invoices.remove(inv);
                    payments.remove(pay);
                    matchedCount++;
                    break;
                }
            }
        }
        // 规则2：按金额完全匹配
        for (SupplierReconDetail inv : new ArrayList<>(invoices)) {
            for (SupplierReconDetail pay : new ArrayList<>(payments)) {
                if (pay.getMatchStatus().equals("MATCHED")) continue;
                if (inv.getAmount() != null && inv.getAmount().compareTo(pay.getAmount()) == 0) {
                    doMatch(inv, pay, "AMOUNT");
                    invoices.remove(inv);
                    payments.remove(pay);
                    matchedCount++;
                    break;
                }
            }
        }
        // 规则3：金额最接近（容差0.01元）
        for (SupplierReconDetail inv : new ArrayList<>(invoices)) {
            SupplierReconDetail bestPay = null;
            BigDecimal minDiff = new BigDecimal("0.01");
            for (SupplierReconDetail pay : payments) {
                if (pay.getMatchStatus().equals("MATCHED")) continue;
                BigDecimal diff = inv.getAmount().subtract(pay.getAmount()).abs();
                if (diff.compareTo(minDiff) <= 0) {
                    minDiff = diff;
                    bestPay = pay;
                }
            }
            if (bestPay != null) {
                doMatch(inv, bestPay, "AMOUNT");
                invoices.remove(inv);
                payments.remove(bestPay);
                matchedCount++;
            }
        }
        return matchedCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public void manualMatch(Long reconId, Long invoiceDetailId, Long paymentDetailId) {
        SupplierReconDetail inv = detailMapper.selectById(invoiceDetailId);
        SupplierReconDetail pay = detailMapper.selectById(paymentDetailId);
        if (inv == null || pay == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "明细记录不存在");
        }
        if (!inv.getReconId().equals(reconId) || !pay.getReconId().equals(reconId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "明细记录不属于该对账单");
        }
        if (!"UNMATCHED".equals(inv.getMatchStatus()) || !"UNMATCHED".equals(pay.getMatchStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "明细已被勾对，无法重复操作");
        }
        doMatch(inv, pay, "MANUAL");
    }

    /** 取消勾对 */
    @Transactional(rollbackFor = Exception.class)
    public void unmatch(Long detailId) {
        SupplierReconDetail d = detailMapper.selectById(detailId);
        if (d == null) throw new BizException(ErrorCode.PARAM_INVALID, "明细记录不存在");
        if (!"MATCHED".equals(d.getMatchStatus()) && !"MANUAL".equals(d.getMatchStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该明细未勾对，无法取消");
        }
        // 清除双方勾对关系
        SupplierReconDetail upd1 = new SupplierReconDetail();
        upd1.setId(d.getId());
        upd1.setMatchStatus("UNMATCHED");
        upd1.setMatchedDetailId(null);
        upd1.setMatchType(null);
        upd1.setMatchRule(null);
        detailMapper.updateById(upd1);
        if (d.getMatchedDetailId() != null) {
            SupplierReconDetail upd2 = new SupplierReconDetail();
            upd2.setId(d.getMatchedDetailId());
            upd2.setMatchStatus("UNMATCHED");
            upd2.setMatchedDetailId(null);
            upd2.setMatchType(null);
            upd2.setMatchRule(null);
            detailMapper.updateById(upd2);
        }
    }

    private void doMatch(SupplierReconDetail inv, SupplierReconDetail pay, String rule) {
        String matchType = "MANUAL".equals(rule) ? "MANUAL" : "AUTO";
        SupplierReconDetail updInv = new SupplierReconDetail();
        updInv.setId(inv.getId());
        updInv.setMatchStatus("MATCHED");
        updInv.setMatchedDetailId(pay.getId());
        updInv.setMatchType(matchType);
        updInv.setMatchRule(rule);
        detailMapper.updateById(updInv);

        SupplierReconDetail updPay = new SupplierReconDetail();
        updPay.setId(pay.getId());
        updPay.setMatchStatus("MATCHED");
        updPay.setMatchedDetailId(inv.getId());
        updPay.setMatchType(matchType);
        updPay.setMatchRule(rule);
        detailMapper.updateById(updPay);
    }

    // ==================== 对账函（SAP 供应商确认函） ====================

    /**
     * 发送对账函：生成对账函编号，标记已发送状态。
     * 对标SAP F.41供应商确认函 + 用友U8+对账函生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public String sendLetter(Long reconId) {
        SupplierRecon r = require(reconId);
        String letterNo = nextLetterNo();
        SupplierRecon upd = new SupplierRecon();
        upd.setId(reconId);
        upd.setLetterNo(letterNo);
        upd.setLetterStatus("SENT");
        upd.setLetterSentTime(LocalDateTime.now());
        // R5-D-074②：发函成功是唯一把供应商确认状态从 NONE 推进到 PENDING 的入口
        //（状态机：NONE 未发函 → PENDING 等待供应商回函 → CONFIRMED/REJECTED，杜绝「未发函先驳回」）。
        upd.setSupplierConfirmStatus("PENDING");
        upd.setStatus("SENT");
        upd.setUpdateBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        upd.setUpdateTime(LocalDateTime.now());
        reconMapper.updateById(upd);
        return letterNo;
    }

    // ==================== 供应商确认 ====================

    /**
     * 供应商确认回函。
     * 对标SAP供应商签章回函 + 票易通供应商门户在线确认（预留LinkU接口）。
     * @param confirmStatus CONFIRMED 确认一致 / REJECTED 有异议
     * @param remark 供应商确认备注
     */
    @Transactional(rollbackFor = Exception.class)
    public void supplierConfirm(Long reconId, String confirmStatus, String remark) {
        SupplierRecon r = require(reconId);
        // R5-D-074②：状态机硬闸——未发函（letter_status 非 SENT/RECEIVED）不得推进供应商确认状态。
        // 历史库存在 letter_status=NONE 而 supplier_confirm_status=REJECTED 的矛盾行（没发函何来驳回），
        // 根因就是本方法无前置校验；这里 fail-closed 拦截，不再允许静默写坏状态机。
        if (!"SENT".equals(r.getLetterStatus()) && !"RECEIVED".equals(r.getLetterStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "对账单尚未发送对账函（letter_status=" + r.getLetterStatus() + "），供应商确认状态不可推进，请先发送对账函");
        }
        // D-DEC-319（批次0 P-4 / A2 回滚腿）：发起前采集确认前快照入流程变量。
        // RECON_ACTIVE 驳回时由 reconBizAction 按快照把状态洗回（此前 CONFIRMED 先落库、驳回无腿＝脏账）。
        String prevStatus = r.getStatus();
        String prevSupplierConfirmStatus = r.getSupplierConfirmStatus();
        SupplierRecon upd = new SupplierRecon();
        upd.setId(reconId);
        upd.setSupplierConfirmStatus(confirmStatus);
        upd.setSupplierConfirmTime(LocalDateTime.now());
        upd.setSupplierConfirmRemark(remark);
        upd.setLetterReceivedTime(LocalDateTime.now());
        if ("CONFIRMED".equals(confirmStatus)) {
            upd.setStatus("CONFIRMED");
        } else if ("REJECTED".equals(confirmStatus)) {
            upd.setStatus("DIFF");
            upd.setDiffRemark(remark);
        }
        upd.setUpdateBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        upd.setUpdateTime(LocalDateTime.now());
        reconMapper.updateById(upd);

        if ("CONFIRMED".equals(confirmStatus)) {
            // 发起对账确认审批流（快照随变量入流，驳回腿消费）
            try {
                java.util.Map<String, Object> extra = new java.util.HashMap<>();
                extra.put("prevStatus", prevStatus == null ? "" : prevStatus);
                extra.put("prevSupplierConfirmStatus", prevSupplierConfirmStatus == null ? "" : prevSupplierConfirmStatus);
                wfEngineFacade.start("RECON_ACTIVE", reconId,
                        UserContext.get() == null ? null : UserContext.get().getUserId(),
                        "供应商对账确认-" + r.getSupplierName(), r.getClosingBalance(), null, extra);
            } catch (Exception ex) {
                log.error("对账单 {} 发起审批流失败: {}", reconId, ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
    }


    /** 添加差异记录 */
    @Transactional(rollbackFor = Exception.class)
    public Long addDiff(Long reconId, String diffType, BigDecimal ourAmount,
                        BigDecimal supplierAmount, String reason) {
        require(reconId);
        SupplierReconDiff diff = new SupplierReconDiff();
        diff.setTenantId(TenantContext.getTenantId());
        diff.setReconId(reconId);
        diff.setDiffType(diffType);
        diff.setOurAmount(nz(ourAmount));
        diff.setSupplierAmount(nz(supplierAmount));
        diff.setDiffAmount(nz(ourAmount).subtract(nz(supplierAmount)));
        diff.setReason(reason);
        diff.setAdjustmentType("NONE");
        diff.setStatus("PENDING");
        diff.setCreateBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        diff.setCreateTime(LocalDateTime.now());
        diffMapper.insert(diff);

        // 同时更新对账单状态为有差异
        SupplierRecon upd = new SupplierRecon();
        upd.setId(reconId);
        upd.setStatus("DIFF");
        upd.setDiffRemark(reason);
        reconMapper.updateById(upd);
        return diff.getId();
    }

    /** 处理差异 */
    @Transactional(rollbackFor = Exception.class)
    public void resolveDiff(Long diffId, String adjustmentType, String voucherNo, String remark) {
        SupplierReconDiff diff = diffMapper.selectById(diffId);
        if (diff == null) throw new BizException(ErrorCode.PARAM_INVALID, "差异记录不存在: " + diffId);
        SupplierReconDiff upd = new SupplierReconDiff();
        upd.setId(diffId);
        upd.setAdjustmentType(adjustmentType);
        upd.setAdjustmentVoucherNo(voucherNo);
        upd.setStatus("RESOLVED");
        upd.setRemark(remark);
        diffMapper.updateById(upd);

        // 检查该对账单是否还有未处理差异
        Long pending = diffMapper.selectCount(new LambdaQueryWrapper<SupplierReconDiff>()
                .eq(SupplierReconDiff::getReconId, diff.getReconId())
                .eq(SupplierReconDiff::getStatus, "PENDING"));
        if (pending == null || pending == 0) {
            SupplierRecon rUpd = new SupplierRecon();
            rUpd.setId(diff.getReconId());
            rUpd.setStatus("RESOLVED");
            reconMapper.updateById(rUpd);
        }
    }

    /** 差异列表 */
    public List<SupplierReconDiff> listDiffs(Long reconId) {
        return diffMapper.selectList(new LambdaQueryWrapper<SupplierReconDiff>()
                .eq(SupplierReconDiff::getReconId, reconId)
                .orderByDesc(SupplierReconDiff::getId));
    }

    // ==================== 查询接口 ====================

    public PageResult<SupplierRecon> page(long pageNum, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<SupplierRecon> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(SupplierRecon::getSupplierName, keyword);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(SupplierRecon::getStatus, status);
        }
        qw.orderByDesc(SupplierRecon::getId);
        Page<SupplierRecon> p = reconMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 对账单明细列表 */
    public List<SupplierReconDetail> listDetails(Long reconId) {
        return detailMapper.selectList(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, reconId)
                .orderByAsc(SupplierReconDetail::getItemType)
                .orderByAsc(SupplierReconDetail::getItemDate));
    }

    /** 对账单汇总信息（含已勾对/未勾对统计） */
    public Map<String, Object> getSummary(Long reconId) {
        SupplierRecon r = require(reconId);
        List<SupplierReconDetail> details = listDetails(reconId);
        long invoiceTotal = details.stream().filter(d -> "INVOICE".equals(d.getItemType())).count();
        long paymentTotal = details.stream().filter(d -> "PAYMENT".equals(d.getItemType())).count();
        long matchedCount = details.stream().filter(d -> "MATCHED".equals(d.getMatchStatus())).count();
        long unmatchedCount = details.stream().filter(d -> "UNMATCHED".equals(d.getMatchStatus())).count();
        BigDecimal matchedAmount = details.stream()
                .filter(d -> "MATCHED".equals(d.getMatchStatus()) && "INVOICE".equals(d.getItemType()))
                .map(d -> nz(d.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> m = new HashMap<>();
        m.put("recon", r);
        m.put("invoiceCount", invoiceTotal);
        m.put("paymentCount", paymentTotal);
        m.put("matchedCount", matchedCount);
        m.put("unmatchedCount", unmatchedCount);
        m.put("matchedAmount", matchedAmount);
        m.put("diffCount", diffMapper.selectCount(new LambdaQueryWrapper<SupplierReconDiff>()
                .eq(SupplierReconDiff::getReconId, reconId)));
        return m;
    }

    /** 供应商是否有已确认对账单（供三单匹配联动） */
    public boolean hasConfirmed(Long supplierId) {
        if (supplierId == null) return false;
        Long c = reconMapper.selectCount(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getSupplierId, supplierId)
                .eq(SupplierRecon::getStatus, "CONFIRMED"));
        return c != null && c > 0;
    }

    /** 按供应商名称判断是否有已确认对账单（三单匹配按 payee_name 联动） */
    public boolean hasConfirmedByName(String supplierName) {
        if (supplierName == null || supplierName.isBlank()) return false;
        Long c = reconMapper.selectCount(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getSupplierName, supplierName)
                .eq(SupplierRecon::getStatus, "CONFIRMED"));
        return c != null && c > 0;
    }

    // ==================== LinkU 对接接口（回调接收模式） ====================

    /**
     * @deprecated 方向错误：此方法为 FSSC 主动推送对账单到 LinkU。
     *             正确架构是 LinkU 完成供应商协同后通过 Webhook 回调 FSSC，
     *             FSSC 被动接收结果。此方法仅保留兼容，不再使用。
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public Long syncToLinkU(Long reconId) {
        SupplierRecon r = require(reconId);
        log.info("同步对账单到LinkU: reconId={}, supplier={}, period={}", reconId, r.getSupplierName(), r.getPeriod());

        // 模拟 LinkU 返回的 ID
        Long linkuReconId = System.currentTimeMillis();

        SupplierRecon upd = new SupplierRecon();
        upd.setId(reconId);
        upd.setLinkuSyncStatus("SYNCED");
        upd.setLinkuSyncTime(LocalDateTime.now());
        upd.setLinkuReconId(linkuReconId);
        if ("NONE".equals(r.getLetterStatus())) {
            upd.setLetterStatus("SENT");
            upd.setLetterSentTime(LocalDateTime.now());
            // R5-D-074②：同发函口径——发函动作把确认状态推进到 PENDING（此前才有权等待供应商回函）
            upd.setSupplierConfirmStatus("PENDING");
        }
        reconMapper.updateById(upd);
        log.info("对账单同步LinkU成功: reconId={}, linkuReconId={}", reconId, linkuReconId);
        return linkuReconId;
    }

    /**
     * 接收 LinkU 供应商确认回函。
     * 预留接口：后续 LinkU 通过 Webhook 回调此方法。
     * 此方法由 LinkU Webhook 匿名回调，不启动审批流（审批流由租户用户后续触发）。
     *
     * @param linkuReconId LinkU 侧对账单 ID
     * @param confirmStatus CONFIRMED / REJECTED
     * @param remark 供应商备注
     */
    @Transactional(rollbackFor = Exception.class)
    public void receiveLinkUConfirm(Long linkuReconId, String confirmStatus, String remark) {
        SupplierRecon r = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getLinkuReconId, linkuReconId)
                .last("limit 1"));
        if (r == null) {
            log.warn("LinkU回调：未找到对账单, linkuReconId={}", linkuReconId);
            return;
        }
        // R5-D-074②：同一状态机硬闸（匿名回调面）——未发函的本地对账单不得被 LinkU 回函推进确认状态，
        // 否则又会生产 letter_status=NONE 而 confirm=REJECTED 的矛盾行。fail-closed：仅告警、拒绝落库。
        if (!"SENT".equals(r.getLetterStatus()) && !"RECEIVED".equals(r.getLetterStatus())) {
            log.warn("LinkU回调：对账单未发函(letter_status={})，拒绝推进供应商确认状态, reconId={}",
                    r.getLetterStatus(), r.getId());
            return;
        }
        // 直接更新对账单状态，不调用 supplierConfirm（避免在匿名上下文中启动审批流导致事务回滚）
        SupplierRecon upd = new SupplierRecon();
        upd.setId(r.getId());
        upd.setSupplierConfirmStatus(confirmStatus);
        upd.setSupplierConfirmTime(LocalDateTime.now());
        upd.setSupplierConfirmRemark(remark);
        upd.setLetterReceivedTime(LocalDateTime.now());
        if ("CONFIRMED".equals(confirmStatus)) {
            upd.setStatus("CONFIRMED");
        } else if ("REJECTED".equals(confirmStatus)) {
            upd.setStatus("DIFF");
            upd.setDiffRemark(remark);
        }
        upd.setUpdateTime(LocalDateTime.now());
        reconMapper.updateById(upd);
        log.info("LinkU供应商确认回函处理完成: reconId={}, confirmStatus={}", r.getId(), confirmStatus);
    }

    /**
     * 接收 LinkU 三单匹配结果回传。
     * LinkU 完成发票↔PO↔收货单匹配后，将结果回传给 FSSC。
     * 已实现：按匹配状态更新明细（MATCHED/PARTIAL→MANUAL/UNMATCHED）+ MATCHED 触发 SUPPLIER_PAY 付款审批。
     */
    public void receiveLinkUMatchResult(Long linkuReconId, String matchStatus, Map<String, Object> matchSummary,
                                        List<Map<String, Object>> matchItems) {
        log.info("LinkU三单匹配结果回传: linkuReconId={}, matchStatus={}, itemCount={}",
                linkuReconId, matchStatus, matchItems != null ? matchItems.size() : 0);
        // 查找本地对账单
        SupplierRecon recon = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getLinkuReconId, linkuReconId));
        if (recon == null) {
            log.warn("LinkU匹配结果回传: 未找到 linkuReconId={} 对应的本地对账单", linkuReconId);
            return;
        }
        // 按匹配状态更新明细
        String detailMatchStatus = switch (matchStatus == null ? "" : matchStatus) {
            case "MATCHED" -> "MATCHED";
            case "PARTIAL" -> "MANUAL";
            default -> "UNMATCHED";
        };
        List<SupplierReconDetail> details = detailMapper.selectList(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, recon.getId()));
        for (SupplierReconDetail d : details) {
            d.setMatchStatus(detailMatchStatus);
            d.setMatchType("AUTO");
            d.setUpdateTime(LocalDateTime.now());
            detailMapper.updateById(d);
        }
        // MATCHED -> 触发付款审批；UNMATCHED -> 冻结付款
        if ("MATCHED".equals(matchStatus)) {
            try {
                BigDecimal totalAmount = details.stream()
                        .filter(d -> "INVOICE".equals(d.getItemType()))
                        .map(d -> nz(d.getAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                wfEngineFacade.start("SUPPLIER_PAY", recon.getId(),
                        UserContext.get() == null ? null : UserContext.get().getUserId(),
                        "供应商付款审批-" + recon.getReconNo(), totalAmount);
                log.info("供应商对账匹配完成，已触发付款审批: reconId={}", recon.getId());
            } catch (Exception ex) {
                // D-ROOT2-01：SUPPLIER_PAY 至今无流程模型（wf_model 无此 key、wf_process_config 无绑定），
                // 冒泡会让 LinkU 回调整体失败、对账单卡在 MATCHED。建模裁决通过前先降级留痕，模型一上线即删本 catch。
                log.error("触发付款审批失败（SUPPLIER_PAY 未建模，付款需手工发起）: reconId={}", recon.getId(), ex);
            }
        }
    }

    /**
     * 接收 LinkU 差异处理结果回传。
     * LinkU 差异自动路由处理完成后，将结果回传给 FSSC 做入账调整。
     * 已实现：差异调节记录写入（SupplierReconDiff，含调整凭证号）+ RESOLVED 回写对账单状态。
     */
    public void receiveLinkUDiffResolved(Long linkuReconId, Long diffId, String diffType,
                                         java.math.BigDecimal diffAmount, String handleStatus,
                                         String handleBy, String handleRemark, String adjustmentType) {
        log.info("LinkU差异处理结果回传: linkuReconId={}, diffId={}, diffType={}, diffAmount={}, handleStatus={}, adjustmentType={}",
                linkuReconId, diffId, diffType, diffAmount, handleStatus, adjustmentType);
        // 查找本地对账单
        SupplierRecon recon = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getLinkuReconId, linkuReconId));
        if (recon == null) {
            log.warn("LinkU差异处理结果回传: 未找到 linkuReconId={} 对应的本地对账单", linkuReconId);
            return;
        }
        // 写入差异调节记录
        SupplierReconDiff diff = new SupplierReconDiff();
        diff.setTenantId(recon.getTenantId());
        diff.setReconId(recon.getId());
        diff.setDiffType(diffType != null ? diffType : "OTHER");
        diff.setDiffAmount(diffAmount);
        diff.setAdjustmentType(adjustmentType != null ? adjustmentType : "NONE");
        diff.setStatus("RESOLVED".equals(handleStatus) ? "RESOLVED" : "PENDING");
        diff.setRemark(handleRemark);
        diff.setCreateBy(handleBy != null ? Long.parseLong(handleBy) : null);
        diff.setCreateTime(LocalDateTime.now());
        diff.setUpdateTime(LocalDateTime.now());
        // 生成调整凭证号
        if (StringUtils.hasText(adjustmentType) && !"NONE".equals(adjustmentType)) {
            String voucherNo = "DR" + recon.getReconNo() + System.currentTimeMillis() % 10000;
            diff.setAdjustmentVoucherNo(voucherNo);
        }
        diffMapper.insert(diff);
        // 若差异已处理，更新对账单状态
        if ("RESOLVED".equals(handleStatus)) {
            recon.setStatus("RESOLVED");
            recon.setUpdateTime(LocalDateTime.now());
            reconMapper.updateById(recon);
        }
        log.info("差异处理结果已写入: reconId={}, diffId={}", recon.getId(), diff.getId());
    }

    /**
     * 接收 LinkU 对账确认单回传。
     * LinkU 生成对账确认单后回传 FSSC，FSSC 据此生成凭证并归档。
     * 已实现：对账单置 CONFIRMED + 确认凭证号（RC 前缀）+ 启动 ARCHIVE_RECON 归档流程。
     */
    public void receiveLinkUReconConfirmed(Long linkuReconId, String reconNo, String supplierCode,
                                           String supplierName, String period,
                                           java.math.BigDecimal openingBalance, java.math.BigDecimal periodPayable,
                                           java.math.BigDecimal periodPaid, java.math.BigDecimal closingBalance,
                                           String confirmedAt, String confirmedBy) {
        log.info("LinkU对账确认单回传: linkuReconId={}, reconNo={}, supplier={}, period={}, closingBalance={}, confirmedBy={}",
                linkuReconId, reconNo, supplierName, period, closingBalance, confirmedBy);
        // 查找本地对账单
        SupplierRecon recon = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getLinkuReconId, linkuReconId));
        if (recon == null) {
            log.warn("LinkU对账确认单回传: 未找到 linkuReconId={} 对应的本地对账单", linkuReconId);
            return;
        }
        // 更新对账单为最终确认状态
        recon.setStatus("CONFIRMED");
        recon.setSupplierConfirmStatus("CONFIRMED");
        recon.setSupplierConfirmTime(LocalDateTime.now());
        recon.setSupplierConfirmRemark("LinkU确认: " + confirmedBy + " @ " + confirmedAt);
        recon.setConfirmTime(LocalDateTime.now());
        // 生成确认凭证号——R5-D-074③：remark/凭证号一律取本行 recon.getReconNo() 生成，
        // 杜绝历史库「[按单勾选 BCP15-202610]」写进 recon_no=BCP9-202610 的串号 remark 复发；
        // 若 reconNo 缺失则如实标注，不得借用其他行的单号拼串。
        String voucherNo = "RC" + recon.getReconNo() + System.currentTimeMillis() % 10000;
        recon.setRemark("确认凭证号: " + voucherNo + " | 确认人: " + confirmedBy);
        recon.setUpdateTime(LocalDateTime.now());
        reconMapper.updateById(recon);
        // 触发归档流程
        try {
            wfEngineFacade.start("ARCHIVE_RECON", recon.getId(),
                    UserContext.get() == null ? null : UserContext.get().getUserId(),
                    "对账单归档-" + recon.getReconNo(), closingBalance);
            log.info("对账确认单归档流程已启动: reconId={}", recon.getId());
        } catch (Exception ex) {
            // D-ROOT2-02：ARCHIVE_RECON 无流程模型，冒泡会让 LinkU 确认回调整体失败（对账单已 CONFIRMED 却回不了票）。
            log.error("启动归档流程失败（ARCHIVE_RECON 未建模，归档需手工补）: reconId={}", recon.getId(), ex);
        }
    }

    /**
     * 接收 LinkU 发票验真结果回传。
     * LinkU 完成全电发票采集验真后，将验真结果回传 FSSC 归档。
     * 已实现：验真结果/查重/影像 URL 关联写入对账明细 remark。
     */
    public void receiveLinkUInvoiceVerified(Long linkuReconId, String invoiceNo, String invoiceCode,
                                            String invoiceDate, java.math.BigDecimal invoiceAmount,
                                            java.math.BigDecimal taxAmount, String verifyStatus,
                                            String verifyRemark, String duplicateCheck, String ocrFileUrl) {
        log.info("LinkU发票验真结果回传: linkuReconId={}, invoiceNo={}, verifyStatus={}, duplicateCheck={}",
                linkuReconId, invoiceNo, verifyStatus, duplicateCheck);
        // 查找本地对账单
        SupplierRecon recon = reconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getLinkuReconId, linkuReconId));
        if (recon == null) {
            log.warn("LinkU发票验真结果回传: 未找到 linkuReconId={} 对应的本地对账单", linkuReconId);
            return;
        }
        // 关联验真结果到对账明细
        List<SupplierReconDetail> details = detailMapper.selectList(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, recon.getId())
                .eq(StringUtils.hasText(invoiceNo), SupplierReconDetail::getItemNo, invoiceNo));
        for (SupplierReconDetail d : details) {
            String verifyInfo = "验真:" + verifyStatus;
            if (StringUtils.hasText(verifyRemark)) {
                verifyInfo += " | " + verifyRemark;
            }
            if (StringUtils.hasText(duplicateCheck)) {
                verifyInfo += " | 查重:" + duplicateCheck;
            }
            if (StringUtils.hasText(ocrFileUrl)) {
                verifyInfo += " | 影像:" + ocrFileUrl;
            }
            d.setRemark(verifyInfo);
            d.setUpdateTime(LocalDateTime.now());
            detailMapper.updateById(d);
        }
        log.info("发票验真结果已关联到 {} 条对账明细: reconId={}", details.size(), recon.getId());
    }

    // ==================== 工具方法 ====================

    /**
     * 查询对账单的 LinkU 同步状态（只读）。
     */
    public SupplierRecon getLinkUStatus(Long id) {
        return require(id);
    }

    private SupplierRecon require(Long id) {
        SupplierRecon r = reconMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单不存在: " + id);
        }
        return r;
    }

    private String nextReconNo() {
        try {
            return numberRuleService.next("SUPPLIER_RECON");
        } catch (Exception e) {
            return "RC" + System.currentTimeMillis();
        }
    }

    private String nextLetterNo() {
        try {
            return numberRuleService.next("RECON_LETTER");
        } catch (Exception e) {
            return "RL" + System.currentTimeMillis();
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
