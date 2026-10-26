// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.tracing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.bank.entity.BankReconcile;
import com.fssc.bank.mapper.BankReconcileMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillReceipt;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillReceiptMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.document.entity.OpArchive;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.tracing.dto.DocTraceChain;
import com.fssc.tracing.dto.DocTraceChain.TraceNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 单据全链路溯源服务（P2-1 页面勾稽联查）。
 * 给定 sourceDocType + sourceDocId，返回单据→发票→凭证→付款→银行对账→档案全链路。
 * 联查、单据追溯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("replica")
public class DocTracingService {

    private final ExpBillMapper expBillMapper;
    private final ExpBillReceiptMapper expBillReceiptMapper;
    private final PoOrderMapper poOrderMapper;
    //  跨域直连的唯一允许点（read-only 联查聚合）：本服务类级路由 replica，全链路溯源天生跨域；
    //    发票/档案域 Facade 的实现在本进程不装配，故保留 Mapper 只读。
    private final InvInvoiceMapper invInvoiceMapper;
    private final RcReceivableMapper rcReceivableMapper;
    private final OpVoucherMapper opVoucherMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final BankReconcileMapper bankReconcileMapper;
    private final OpArchiveMapper opArchiveMapper;

    /**
     * 全链路溯源：单据→发票→凭证→付款→银行对账→档案。
     *
     * @param sourceDocType 来源单据类型（EXP_BILL/PO_ORDER/INV_INVOICE/RC_RECEIVABLE等）
     * @param sourceDocId   来源单据ID
     */
    public DocTraceChain trace(String sourceDocType, Long sourceDocId) {
        DocTraceChain chain = new DocTraceChain();
        chain.setSourceDocType(sourceDocType);
        chain.setSourceDocId(sourceDocId);

        // 1. 解析源单据基本信息
        resolveSourceDoc(chain, sourceDocType, sourceDocId);

        // 外部单据类型 → 凭证/付款/银行侧 source_doc_type 的映射
        List<String> mappedTypes = mapVoucherTypes(sourceDocType);

        // 2. 查关联发票
        chain.setInvoices(queryInvoices(sourceDocType, sourceDocId));

        // 3. 查关联凭证（用映射后的类型集合查询，兼容直接传入凭证侧类型的情况）
        LambdaQueryWrapper<OpVoucher> vq = new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceDocId, sourceDocId)
                .orderByDesc(OpVoucher::getCreateTime);
        if (mappedTypes.size() == 1) {
            vq.eq(OpVoucher::getSourceDocType, mappedTypes.get(0));
        } else {
            vq.in(OpVoucher::getSourceDocType, mappedTypes);
        }
        List<OpVoucher> vouchers = opVoucherMapper.selectList(vq);
        chain.setVouchers(vouchers.stream().map(this::toVoucherNode).collect(Collectors.toList()));

        // 4. 查关联付款批次
        LambdaQueryWrapper<OpPayBatch> pq = new LambdaQueryWrapper<OpPayBatch>()
                .eq(OpPayBatch::getSourceDocId, sourceDocId)
                .orderByDesc(OpPayBatch::getCreateTime);
        if (mappedTypes.size() == 1) {
            pq.eq(OpPayBatch::getSourceDocType, mappedTypes.get(0));
        } else {
            pq.in(OpPayBatch::getSourceDocType, mappedTypes);
        }
        // AUTO_GROUP 付款批次也按 source_doc_id 匹配
        List<OpPayBatch> payBatches = opPayBatchMapper.selectList(pq);
        chain.setPayBatches(payBatches.stream().map(this::toPayBatchNode).collect(Collectors.toList()));

        // 4b. 查收款事实（资金流入侧，D-DEC-394）。bill_id 指向 exp_bill.id ⇒ 只在费用域单据类型下查，
        //     否则 PO/发票/应收的同值主键会串出无关收款行。
        chain.setReceipts(queryReceipts(sourceDocType, sourceDocId));

        // 5. 查银行对账记录
        LambdaQueryWrapper<BankReconcile> bq = new LambdaQueryWrapper<BankReconcile>()
                .eq(BankReconcile::getSourceDocId, sourceDocId)
                .orderByDesc(BankReconcile::getCreateTime);
        if (mappedTypes.size() == 1) {
            bq.eq(BankReconcile::getSourceDocType, mappedTypes.get(0));
        } else {
            bq.in(BankReconcile::getSourceDocType, mappedTypes);
        }
        List<BankReconcile> bankRecs = bankReconcileMapper.selectList(bq);
        chain.setBankReconciles(bankRecs.stream().map(this::toBankReconNode).collect(Collectors.toList()));

        // 6. 查归档记录（按 billId 或 voucherId 关联）
        List<Long> voucherIds = vouchers.stream().map(OpVoucher::getId).collect(Collectors.toList());
        chain.setArchives(queryArchives(sourceDocId, voucherIds));

        return chain;
    }

    /**
     * 外部单据类型 → 凭证侧 source_doc_type 的映射。
     * 凭证/付款/银行表的 source_doc_type 与外部单据类型不一致时，需要转换。
     */
    private List<String> mapVoucherTypes(String sourceDocType) {
        if (sourceDocType == null) return Collections.emptyList();
        return switch (sourceDocType) {
            case "EXP_BILL"      -> Arrays.asList("EXPENSE", "EXPENSE_PAY");
            case "EXP_LOAN"      -> Collections.singletonList("EXP_LOAN");
            case "EXP_REPAY"     -> Collections.singletonList("EXP_REPAY");
            case "PO_ORDER"     -> Arrays.asList("EXPENSE_PAY", "TREASURY_PAY");
            case "INV_INVOICE"  -> Arrays.asList("EXPENSE_PAY", "TREASURY_PAY");
            case "ASSET_CARD"   -> Collections.singletonList("ASSET_DEPRECIATION");
            case "RC_RECEIVABLE"-> Arrays.asList("RECEIVABLE", "TREASURY_RECEIVE");
            // 直接传入凭证侧类型时原样使用
            case "EXPENSE", "EXPENSE_PAY", "ASSET_DEPRECIATION",
                 "STOCK_COST_SETTLE", "TREASURY_PAY", "TREASURY_RECEIVE",
                 "AUTO_GROUP", "EXPENSE_REIM", "EXPENSE_LOAN", "EXPENSE_REPAY" ->
                    Collections.singletonList(sourceDocType);
            default -> Collections.singletonList(sourceDocType);
        };
    }

    private void resolveSourceDoc(DocTraceChain chain, String sourceDocType, Long sourceDocId) {
        switch (sourceDocType == null ? "" : sourceDocType) {
            case "EXP_BILL", "EXPENSE", "EXPENSE_PAY",
                 "EXP_LOAN", "EXP_REPAY", "EXPENSE_LOAN", "EXPENSE_REPAY" -> {
                // EXPENSE/EXPENSE_PAY/EXP_LOAN/EXP_REPAY 的 source_doc_id 指向 exp_bill.id（借款/还款同为费用域单据）
                ExpBill bill = expBillMapper.selectById(sourceDocId);
                if (bill != null) {
                    chain.setSourceDocNo(bill.getBillNo());
                    chain.setSourceDocSummary(bill.getTitle());
                    chain.setSourceDocStatus(bill.getStatus());
                    chain.setSourceDocAmount(bill.getTotalAmount() == null ? null : bill.getTotalAmount().toPlainString());
                }
            }
            case "PO_ORDER" -> {
                PoOrder po = poOrderMapper.selectById(sourceDocId);
                if (po != null) {
                    chain.setSourceDocNo(po.getPoNo());
                    chain.setSourceDocSummary(po.getSupplierName());
                    chain.setSourceDocStatus(po.getStatus());
                    chain.setSourceDocAmount(po.getAmount() == null ? null : po.getAmount().toPlainString());
                }
            }
            case "INV_INVOICE" -> {
                InvInvoice inv = invInvoiceMapper.selectById(sourceDocId);
                if (inv != null) {
                    chain.setSourceDocNo(inv.getInvoiceNo());
                    chain.setSourceDocSummary(inv.getInvoiceTitle());
                    chain.setSourceDocStatus(inv.getStatus());
                    chain.setSourceDocAmount(inv.getAmount() == null ? null : inv.getAmount().toPlainString());
                }
            }
            case "RC_RECEIVABLE" -> {
                RcReceivable rc = rcReceivableMapper.selectById(sourceDocId);
                if (rc != null) {
                    chain.setSourceDocNo(rc.getBillNo());
                    chain.setSourceDocSummary(rc.getPeriod());
                    chain.setSourceDocStatus(rc.getStatus());
                    chain.setSourceDocAmount(rc.getAmount() == null ? null : rc.getAmount().toPlainString());
                }
            }
            case "ASSET_DEPRECIATION", "STOCK_COST_SETTLE", "TREASURY_PAY", "TREASURY_RECEIVE" -> {
                // 凭证侧类型：从凭证表反查摘要
                OpVoucher v = opVoucherMapper.selectOne(
                        new LambdaQueryWrapper<OpVoucher>()
                                .eq(OpVoucher::getSourceDocType, sourceDocType)
                                .eq(OpVoucher::getSourceDocId, sourceDocId)
                                .last("LIMIT 1"));
                if (v != null) {
                    chain.setSourceDocNo(v.getVoucherNo());
                    chain.setSourceDocSummary(v.getSummary());
                    chain.setSourceDocStatus(v.getStatus());
                    chain.setSourceDocAmount(v.getDebitTotal() == null ? null : v.getDebitTotal().toPlainString());
                }
            }
            default -> {
                chain.setSourceDocNo("(未知单据类型: " + sourceDocType + ")");
            }
        }
    }

    private List<TraceNode> queryInvoices(String sourceDocType, Long sourceDocId) {
        List<InvInvoice> invoices = invInvoiceMapper.selectList(
                new LambdaQueryWrapper<InvInvoice>()
                        .eq(InvInvoice::getSourceDocType, sourceDocType)
                        .eq(InvInvoice::getSourceDocId, sourceDocId)
                        .orderByDesc(InvInvoice::getCreateTime));
        return invoices.stream().map(inv -> {
            TraceNode n = new TraceNode();
            n.setNodeType("INVOICE");
            n.setId(inv.getId());
            n.setDocNo(inv.getInvoiceNo());
            n.setStatus(inv.getStatus());
            n.setAmount(inv.getAmount() == null ? null : inv.getAmount().toPlainString());
            n.setDate(inv.getInvoiceDate() == null ? null : inv.getInvoiceDate().toString());
            n.setSummary(inv.getSellerName());
            n.setCreateTime(inv.getCreateTime());
            return n;
        }).collect(Collectors.toList());
    }

    private TraceNode toVoucherNode(OpVoucher v) {
        TraceNode n = new TraceNode();
        n.setNodeType("VOUCHER");
        n.setId(v.getId());
        n.setDocNo(v.getVoucherNo());
        n.setStatus(v.getStatus());
        n.setAmount(v.getDebitTotal() == null ? null : v.getDebitTotal().toPlainString());
        n.setDate(v.getVoucherDate() == null ? null : v.getVoucherDate().toString());
        n.setSummary(v.getSummary());
        n.setCreateTime(v.getCreateTime());
        return n;
    }

    private TraceNode toPayBatchNode(OpPayBatch p) {
        TraceNode n = new TraceNode();
        n.setNodeType("PAY_BATCH");
        n.setId(p.getId());
        n.setDocNo(p.getBatchNo());
        n.setStatus(p.getStatus());
        n.setAmount(p.getTotalAmount() == null ? null : p.getTotalAmount().toPlainString());
        n.setSummary(p.getRemark());
        n.setCreateTime(p.getCreateTime());
        return n;
    }

    /** 收款事实（exp_bill_receipt）只在费用域单据类型下按 bill_id 查询 */
    private static final List<String> EXP_BILL_DOC_TYPES = Arrays.asList(
            "EXP_BILL", "EXPENSE", "EXPENSE_PAY", "EXPENSE_REIM",
            "EXP_LOAN", "EXPENSE_LOAN", "EXP_REPAY", "EXPENSE_REPAY");

    private List<TraceNode> queryReceipts(String sourceDocType, Long sourceDocId) {
        if (sourceDocType == null || !EXP_BILL_DOC_TYPES.contains(sourceDocType)) {
            return Collections.emptyList();
        }
        List<ExpBillReceipt> receipts = expBillReceiptMapper.selectList(
                new LambdaQueryWrapper<ExpBillReceipt>()
                        .eq(ExpBillReceipt::getBillId, sourceDocId)
                        .orderByDesc(ExpBillReceipt::getCreateTime));
        return receipts.stream().map(r -> {
            TraceNode n = new TraceNode();
            n.setNodeType("RECEIPT");
            n.setId(r.getId());
            n.setDocNo(r.getReceiptSerialNo());
            n.setStatus(r.getReceiptStatus());
            n.setAmount(r.getAmount() == null ? null : r.getAmount().toPlainString());
            n.setDate(r.getReceiptTime() == null ? null : r.getReceiptTime().toLocalDate().toString());
            n.setSummary(r.getPayerName() + (r.getRemark() == null ? "" : "／" + r.getRemark()));
            n.setCreateTime(r.getCreateTime());
            return n;
        }).collect(Collectors.toList());
    }

    private TraceNode toBankReconNode(BankReconcile r) {
        TraceNode n = new TraceNode();
        n.setNodeType("BANK_RECON");
        n.setId(r.getId());
        n.setDocNo(r.getSerialNo());
        n.setStatus(r.getMatchStatus());
        n.setAmount(r.getAmount() == null ? null : r.getAmount().toPlainString());
        n.setSummary(r.getPaymentNo());
        n.setCreateTime(r.getCreateTime());
        return n;
    }

    private List<TraceNode> queryArchives(Long sourceDocId, List<Long> voucherIds) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        if (voucherIds.isEmpty()) {
            qw.eq(OpArchive::getBillId, sourceDocId);
        } else {
            qw.and(w -> w.eq(OpArchive::getBillId, sourceDocId)
                    .or().in(OpArchive::getVoucherId, voucherIds));
        }
        qw.orderByDesc(OpArchive::getCreateTime);
        List<OpArchive> archives = opArchiveMapper.selectList(qw);
        return archives.stream().map(a -> {
            TraceNode n = new TraceNode();
            n.setNodeType("ARCHIVE");
            n.setId(a.getId());
            n.setDocNo(a.getArchiveNo());
            n.setStatus(a.getArchiveStatus());
            n.setSummary(a.getTitle());
            n.setCreateTime(a.getCreateTime());
            return n;
        }).collect(Collectors.toList());
    }
}
