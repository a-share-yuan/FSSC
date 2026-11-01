// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.service.InvoiceService;
import com.fssc.framework.datasource.FsscDataSource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 发票管理（/api/tenant/invoice/**）：录入（价税分离）/ 关联单据 / 查询 */
@RestController
@RequestMapping("/api/tenant/invoice")
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @RequiresPerm("inv:create")
    @OperLog(module = "INVOICE", operType = "INSERT", desc = "录入发票")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody InvInvoice invoice) {
        // 人工补录入口的必填兜底（同一 service 方法还被 OCR 入池复用，故校验落在本边界）
        invoiceService.requireManualHeader(invoice);
        return Result.success(invoiceService.create(invoice));
    }

    /** 销售发票开具（OTC02 INVOICE_ISSUE）：登记并发起开票审批流 */
    @RequiresPerm("inv:issue")
    @OperLog(module = "INVOICE", operType = "INSERT", desc = "发票开具")
    @PostMapping("/issue")
    public Result<Long> issue(@RequestBody InvInvoice invoice) {
        return Result.success(invoiceService.issue(invoice));
    }

    /**
     * 销项发票作废（仅当月且未被收款核销引用）：status→VOID，remark 记录。
     * body: {"invoiceId": 1, "reason": "开票有误"}
     */
    @RequiresPerm("inv:void")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "销项发票作废")
    @PostMapping("/void")
    public Result<Void> voidInvoice(@RequestBody InvoiceOpReq req) {
        invoiceService.voidIssued(req.getInvoiceId(), req.getReason());
        return Result.success();
    }

    /**
     * 销项发票跨月红冲：生成负数金额红字发票（原号+R，original_id 关联原票），原票置 RED_FLUSHED。
     * body: {"invoiceId": 1, "reason": "跨月退货"}
     */
    @RequiresPerm("inv:redflush")
    @OperLog(module = "INVOICE", operType = "INSERT", desc = "销项发票红冲")
    @PostMapping("/red-flush")
    public Result<Long> redFlush(@RequestBody InvoiceOpReq req) {
        return Result.success(invoiceService.redFlush(req.getInvoiceId(), req.getReason()));
    }

    /**  作废/红冲请求体 */
    @lombok.Data
    public static class InvoiceOpReq {
        private Long invoiceId;
        private String reason;
    }

    /** 进项补录金额更正请求体 */
    @lombok.Data
    public static class InvoiceCorrectReq {
        private Long invoiceId;
        private BigDecimal netAmount;
        private BigDecimal taxAmount;
        private BigDecimal amount;
        private String reason;
    }

    /**
     * 进项「发票补录」金额更正（出口）：票号上有唯一键，录错额后作废也重录不回来，
     * 故给一张未被勾稽引用的进项票一个改额通道。鉴权走专码 inv:correct（与 inv:void/inv:unbind 同批授予）。
     */
    @RequiresPerm("inv:correct")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "进项补录票金额更正")
    @PostMapping("/correct")
    public Result<Void> correctEntry(@RequestBody InvoiceCorrectReq req) {
        invoiceService.correctEntry(req.getInvoiceId(), req.getNetAmount(), req.getTaxAmount(),
                req.getAmount(), req.getReason());
        return Result.success();
    }

    @RequiresPerm("inv:bind")
    @OperLog(module = "INVOICE", operType = "INSERT", desc = "发票关联单据")
    @PostMapping("/bind")
    public Result<Long> bind(@RequestBody BindReq req) {
        // H5 建单批量绑票通道传 invoiceIds（无逐票金额）→ 逐票按"剩余可用面额"自动占用，
        // 不再因 invoiceId=null 抛错被前端静默吞掉（历史上导致付款/报销单核销金额恒 0 → 三单匹配恒判超付）
        if (req.getInvoiceId() == null && req.getInvoiceIds() != null && !req.getInvoiceIds().isEmpty()) {
            Long first = null;
            for (Long iid : req.getInvoiceIds()) {
                if (iid == null) continue;
                Long id = invoiceService.bindToBill(req.getBillId(), req.getLineId(), iid,
                        invoiceService.remainingAmount(iid));
                if (first == null) first = id;
            }
            return Result.success(first);
        }
        // 手工「关联单据」弹窗：覆盖金额是必填项（前端 INVOICE_BIND 规则已拦，此处兜底）。
        // 金额为 0 的占用记录会让发票置 LOCKED_IN_USE 却对核销毫无贡献，故一并拦下。
        // 批量通道（invoiceIds，H5 建单）按剩余面额自动占用，不走本分支。
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入本次覆盖/占用金额（须大于 0）");
        }
        return Result.success(invoiceService.bindToBill(req.getBillId(), req.getLineId(), req.getInvoiceId(), req.getAmount()));
    }

    /** 反核销请求体：优先 billId；给 billNo 时按单据号解析（发票池「解除关联」按号输入）。 */
    public static class UnbindReq {
        private Long billId;
        private String billNo;
        private Long invoiceId;

        public Long getBillId() { return billId; }
        public void setBillId(Long billId) { this.billId = billId; }
        public String getBillNo() { return billNo; }
        public void setBillNo(String billNo) { this.billNo = billNo; }
        public Long getInvoiceId() { return invoiceId; }
        public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    }

    /**
     * 反核销（配套 · 单据反操作链第一环）：解除发票-单据勾稽并解锁发票。
     * 前置：本单据无 PAID 付款记录（付款已执行不可反）。权限用 inv:unbind（反核销独立授权）。
     */
    @RequiresPerm("inv:unbind")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "发票解除关联（反核销）")
    @PostMapping("/unbind")
    public Result<Integer> unbind(@RequestBody UnbindReq req) {
        if (req.getInvoiceId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定发票");
        }
        Long billId = req.getBillId();
        if (billId == null) {
            if (req.getBillNo() == null || req.getBillNo().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请指定要解除关联的业务单据");
            }
            ExpBill bill = invoiceService.billByNo(req.getBillNo().trim());
            if (bill == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "业务单据不存在: " + req.getBillNo());
            }
            billId = bill.getId();
        }
        return Result.success(invoiceService.unbindFromBill(billId, req.getInvoiceId()));
    }

    @RequiresPerm("inv:list")
    @GetMapping("/page")
    public Result<PageResult<InvInvoice>> page(PageQuery query, String keyword,
                                               String invoiceNo, String invoiceCode, String sellerName,
                                               String invoiceDateFrom, String invoiceDateTo,
                                               String scanDateFrom, String scanDateTo, String billNo,
                                               String verifyStatus, String certifyStatus, String direction) {
        return Result.success(invoiceService.page(query.getPageNum(), query.getPageSize(), keyword,
                invoiceNo, invoiceCode, sellerName, invoiceDateFrom, invoiceDateTo,
                scanDateFrom, scanDateTo, billNo, verifyStatus, certifyStatus, direction));
    }

    // 进项发票全生命周期

    /** 批量进项认证勾选（仅专票可抵扣；指定所属期） */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("inv:certify")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "进项认证勾选")
    @PostMapping("/certify")
    public Result<Integer> certify(@RequestBody CertifiedReq req) {
        return Result.success(invoiceService.certify(req.getIds(), req.getPeriod()));
    }

    /** 取消勾选 */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("inv:certify")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "取消进项勾选")
    @PostMapping("/certify/cancel")
    public Result<Integer> cancelCertify(@RequestBody IdsReq req) {
        return Result.success(invoiceService.cancelCertify(req.getIds()));
    }

    /** 进项转出（不可抵扣） */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("inv:certify")
    @OperLog(module = "INVOICE", operType = "UPDATE", desc = "进项转出")
    @PostMapping("/certify/transfer-out")
    public Result<Integer> transferOut(@RequestBody IdsReq req) {
        return Result.success(invoiceService.transferOut(req.getIds()));
    }

    /** 票账比对·增值税申报汇总（销项 − 认证进项 = 应纳增值税） */
    @RequiresFeature("FSSC_TAX")
    @RequiresPerm("inv:list")
    @GetMapping("/filing-summary")
    public Result<InvoiceService.VatFiling> filingSummary(@RequestParam String period) {
        return Result.success(invoiceService.filingSummary(period));
    }

    /** 发票异常清单（红冲/作废/销方黑名单），供 7×24 监控与税务岗复核 */
    @RequiresPerm("inv:list")
    @GetMapping("/anomaly")
    public Result<PageResult<InvInvoice>> anomaly(PageQuery query, @RequestParam(required = false) String anomalyFlag) {
        return Result.success(invoiceService.anomalyList(query.getPageNum(), query.getPageSize(), anomalyFlag));
    }

    /** #12 发票占用计数对账：比对 used_count 与 Σinv_bill_invoice 记录数；fix=true 时自愈 */
    @RequiresPerm("inv:audit")
    @GetMapping("/used-count/audit")
    public Result<List<Map<String, Object>>> auditUsedCount(@RequestParam(defaultValue = "false") boolean fix) {
        return Result.success(invoiceService.auditUsedCount(fix));
    }

    @Data
    public static class CertifiedReq {
        private List<Long> ids;
        private String period;
    }

    @Data
    public static class IdsReq {
        private List<Long> ids;
    }

    @RequiresPerm("inv:list")
    @GetMapping("/bill-invoices")
    public Result<List<InvBillInvoice>> billInvoices(@RequestParam Long billId) {
        return Result.success(invoiceService.billInvoices(billId));
    }

    /**
     * 发票「关联单据」候选单据。
     *
     * <p>权限用 {@code inv:bind} 而非 {@code exp:bill:list}：这是绑票动作自己的查询窗口，
     * 能绑票的人必须能选到要绑的单据；复用 {@code /expense/bill/myPage} 的话（硬过滤
     * {@code apply_user_id=登录人}），共享会计选不到他人单据，三单匹配的唯一出口不可达。</p>
     *
     * <p>只读：返回候选单据 + 本单已勾稽发票额与缺口，不写任何状态。</p>
     */
    @RequiresPerm("inv:bind")
    @GetMapping("/bindable-bills")
    public Result<PageResult<Map<String, Object>>> bindableBills(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String status) {
        return Result.success(invoiceService.bindableBills(query.getPageNum(), query.getPageSize(),
                keyword, billType, status));
    }

    @Data
    public static class BindReq {
        private Long billId;
        private Long lineId;
        private Long invoiceId;
        private BigDecimal amount;
        /** H5 批量绑票通道（invoiceId 缺省时生效，逐票自动按剩余可用面额占用） */
        private List<Long> invoiceIds;
    }
}
