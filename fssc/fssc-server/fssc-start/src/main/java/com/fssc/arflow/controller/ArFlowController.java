// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArClaim;
import com.fssc.arflow.entity.ArDispatchRule;
import com.fssc.arflow.entity.ArEbsAdjust;
import com.fssc.arflow.entity.ArInvoiceRule;
import com.fssc.arflow.entity.ArMailNotifyLog;
import com.fssc.arflow.entity.ArMailPackage;
import com.fssc.arflow.entity.ArMailRecipient;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArInvoiceRuleMapper;
import com.fssc.arflow.service.ArClaimService;
import com.fssc.arflow.service.ArDispatchService;
import com.fssc.arflow.service.ArEbsAdjustService;
import com.fssc.arflow.service.ArMailPackageService;
import com.fssc.arflow.service.ArMailRecipientService;
import com.fssc.arflow.service.ArOffsetExcelService;
import com.fssc.arflow.service.ArPreInvoiceService;
import com.fssc.arflow.service.ArReceiptExcelService;
import com.fssc.arflow.service.ArStatementExcelService;
import com.fssc.arflow.service.ArStatementService;
import com.fssc.common.core.Result;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.service.SubjectService;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.receivable.service.RcReceiptService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AR开票链 OpenAPI（/api/tenant/ar-flow/**）：
 * 对账单导入/核对/财务调整 → 拆分合并生成预制发票（含商务确认）→ 金税推送/回写 →
 * 红字/换票/提前开票核对 → 邮包；开票规则配置；10类服务申请状态机。
 */
@RestController
@RequestMapping("/api/tenant/ar-flow")
@RequiredArgsConstructor
public class ArFlowController {

    private final ArStatementService statementService;
    private final ArPreInvoiceService preInvoiceService;
    private final ArClaimService claimService;
    private final ArMailPackageService mailPackageService;
    private final ArMailRecipientService mailRecipientService;
    private final SubjectService subjectService;
    private final ArInvoiceRuleMapper ruleMapper;
    private final ArReceiptExcelService receiptExcelService;
    private final RcReceiptService rcReceiptService;
    private final CrmCustomerMapper crmCustomerMapper;
    private final ArStatementExcelService statementExcelService;
    private final ArOffsetExcelService offsetExcelService;
    private final ArEbsAdjustService ebsAdjustService;
    private final ArDispatchService dispatchService;
    private final RcReceivableMapper rcReceivableMapper;

    // 对账单

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/statements")
    public Result<List<ArStatement>> statements(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String customerName) {
        return Result.success(statementService.list(status, customerName));
    }

    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "导入客户对账单")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/statements")
    public Result<ArStatement> importStatement(@RequestBody ArStatement bill) {
        return Result.success(statementService.importBill(bill));
    }

    /** 对账单模板 Excel 导入（附件11.4.4，12列；每客户一张对账单，明细落 lines_json） */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "对账单模板Excel导入")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping(value = "/statements/import-excel", consumes = {"multipart/form-data"})
    public Result<Map<String, Object>> importStatementExcel(@RequestParam("file") MultipartFile file,
                                                            @RequestParam(required = false) Long companyId) {
        return Result.success(statementExcelService.importAndSave(file, companyId));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/statements/{id}/check")
    public Result<ArStatement> check(@PathVariable Long id) {
        return Result.success(statementService.check(id));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/statements/{id}/adjust")
    public Result<ArStatement> adjust(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(statementService.adjust(id, (String) body.get("note")));
    }

    // 预制发票

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/pre-invoices")
    public Result<List<ArPreInvoice>> preInvoices(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String customerName) {
        return Result.success(preInvoiceService.list(status, customerName));
    }

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/pre-invoices/{id}")
    public Result<ArPreInvoice> preInvoice(@PathVariable Long id) {
        return Result.success(preInvoiceService.get(id));
    }

    /** 拆分合并生成预制发票（按客户开票规则） */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "拆分合并生成预制发票")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/generate")
    public Result<List<ArPreInvoice>> generate(@RequestParam String reconBillNo) {
        return Result.success(preInvoiceService.generate(reconBillNo));
    }

    /** 商务确认通过（参数 ar.bizconfirm.enabled 开启时生效） */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/biz-confirm")
    public Result<ArPreInvoice> bizConfirm(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(preInvoiceService.bizConfirm(id,
                body == null ? null : (String) body.get("confirmer")));
    }

    /** 商务确认驳回 */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/biz-reject")
    public Result<ArPreInvoice> bizReject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(preInvoiceService.bizReject(id, (String) body.get("reason")));
    }

    /** 推送金税（GOLDEN_TAX 接口配置存在走真实，未配置模拟） */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/push-tax")
    public Result<ArPreInvoice> pushTax(@PathVariable Long id) {
        return Result.success(preInvoiceService.pushTax(id));
    }

    /** 金税回写：body {invoiceNo,invoiceCode,invoiceDate} */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/writeback")
    public Result<ArPreInvoice> writeback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate date = body.get("invoiceDate") == null ? null
                : LocalDate.parse(String.valueOf(body.get("invoiceDate")).substring(0, 10));
        return Result.success(preInvoiceService.writeback(id,
                (String) body.get("invoiceNo"), (String) body.get("invoiceCode"), date));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/print")
    public Result<ArPreInvoice> print(@PathVariable Long id) {
        return Result.success(preInvoiceService.print(id));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/void")
    public Result<ArPreInvoice> voidInvoice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(preInvoiceService.voidInvoice(id, (String) body.get("reason")));
    }

    /** 红字发票：body {redNoticeNo} */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/red")
    public Result<ArPreInvoice> red(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(preInvoiceService.red(id, (String) body.get("redNoticeNo")));
    }

    /** 换票重开：body {reason} */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/exchange")
    public Result<List<ArPreInvoice>> exchange(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(preInvoiceService.exchange(id, (String) body.get("reason")));
    }

    /** 提前开票核对：body {matched,note} */
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/pre-invoices/{id}/pre-check")
    public Result<ArPreInvoice> preCheck(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean matched = Boolean.parseBoolean(String.valueOf(body.get("matched")));
        return Result.success(preInvoiceService.preCheck(id, matched, (String) body.get("note")));
    }

    // 服务申请单（10类）

    @RequiresPerm("arflow:claim:manage")
    @GetMapping("/claims")
    public Result<List<ArClaim>> claims(@RequestParam(required = false) String claimType,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String customerName) {
        return Result.success(claimService.list(claimType, status, customerName));
    }

    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "创建AR服务申请单")
    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims")
    public Result<ArClaim> createClaim(@RequestBody ArClaim c) {
        return Result.success(claimService.create(c));
    }

    /** 其它业务对冲借贷 Excel 引入（附件11.4.3，7列；行级借贷平衡+科目存在性+往来段必填，先校验后入库） */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "对冲借贷Excel引入")
    @RequiresPerm("arflow:claim:manage")
    @PostMapping(value = "/claims/import-offset-excel", consumes = {"multipart/form-data"})
    public Result<Map<String, Object>> importOffsetExcel(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) String reason) {
        return Result.success(offsetExcelService.importAndCreate(file, reason));
    }

    // AR接口2/4：ERP 推送

    /** 接口2 财务调整数据推送：body {adjustNo, customerCode, adjustType, amount, taxAmount, ebsVoucherNo, statementNo}，关联异常对账单自动重核 */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "外部财务系统财务调整推送")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/ebs-adjustments")
    public Result<ArEbsAdjust> pushAdjust(@RequestBody Map<String, Object> body) {
        return Result.success(ebsAdjustService.pushAdjust(body));
    }

    @GetMapping("/ebs-adjustments")
    public Result<List<ArEbsAdjust>> adjusts(@RequestParam(required = false) String customerCode) {
        return Result.success(ebsAdjustService.adjusts(customerCode));
    }

    /** 接口4 负数发票推送：body {ebsCreditMemoNo, preInvoiceNo|invoiceNo}，置红字前置待补通知单号 */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "外部财务系统负数发票推送")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/red-invoices/push")
    public Result<ArPreInvoice> pushRedInvoice(@RequestBody Map<String, Object> body) {
        return Result.success(ebsAdjustService.pushRedInvoice(body));
    }

    // AR派工规则（蓝图5.2）

    @RequiresPerm("ar:dispatch:manage")
    @GetMapping("/dispatch-rules")
    public Result<List<ArDispatchRule>> dispatchRules() {
        return Result.success(dispatchService.rules());
    }

    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "保存AR派工规则")
    @RequiresPerm("ar:dispatch:manage")
    @PostMapping("/dispatch-rules")
    public Result<ArDispatchRule> saveDispatchRule(@RequestBody ArDispatchRule rule) {
        return Result.success(dispatchService.saveRule(rule));
    }

    @RequiresPerm("ar:dispatch:manage")
    @PostMapping("/dispatch-rules/delete")
    public Result<Void> deleteDispatchRule(@RequestParam Long id) {
        dispatchService.deleteRule(id);
        return Result.success();
    }



    @RequiresPerm("arflow:claim:ekp")
    @PostMapping("/claims/{id}/ekp-approve")
    public Result<ArClaim> ekpApprove(@PathVariable Long id) {
        return Result.success(claimService.ekpApprove(id));
    }

    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/assign")
    public Result<ArClaim> assignClaim(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(claimService.assign(id, (String) body.get("user")));
    }

    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/review")
    public Result<ArClaim> reviewClaim(@PathVariable Long id) {
        return Result.success(claimService.review(id));
    }

    /** 审单岗补录科目：非默认类型（向客户索赔等）过账前置 */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "AR服务申请补录科目")
    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/accounts")
    public Result<ArClaim> supplementClaimAccounts(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(claimService.supplementAccounts(id,
                (String) body.get("debitAccount"), (String) body.get("creditAccount"), (String) body.get("taxAccount")));
    }

    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/reject")
    public Result<ArClaim> rejectClaim(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(claimService.reject(id, (String) body.get("reason")));
    }

    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/reverse-est")
    public Result<ArClaim> reverseEst(@PathVariable Long id) {
        return Result.success(claimService.reverseEstimate(id));
    }

    @RequiresPerm("arflow:claim:manage")
    @PostMapping("/claims/{id}/complete")
    public Result<ArClaim> completeClaim(@PathVariable Long id) {
        return Result.success(claimService.complete(id));
    }

    // 邮包

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/mail-packages")
    public Result<List<ArMailPackage>> mailPackages(@RequestParam(required = false) String status) {
        return Result.success(mailPackageService.list(status));
    }

    /** 创建邮包：body {...,invoiceNos:"PF...001,PF...002"} */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "创建发票邮包")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-packages")
    public Result<ArMailPackage> createMail(@RequestBody ArMailPackage p,
                                            @RequestParam(required = false) String invoiceNos) {
        return Result.success(mailPackageService.create(p, invoiceNos));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-packages/{id}/send")
    public Result<ArMailPackage> sendMail(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(mailPackageService.send(id, (String) body.get("courierCompany"),
                (String) body.get("courierNo")));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-packages/{id}/notify")
    public Result<ArMailPackage> notifyMail(@PathVariable Long id) {
        return Result.success(mailPackageService.notify(id));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-packages/{id}/sign")
    public Result<ArMailPackage> signMail(@PathVariable Long id) {
        return Result.success(mailPackageService.sign(id));
    }

    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-packages/{id}/archive")
    public Result<ArMailPackage> archiveMail(@PathVariable Long id) {
        return Result.success(mailPackageService.archive(id));
    }

    // 邮包收件人地址簿 / 邮件记录

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/mail-recipients")
    public Result<List<ArMailRecipient>> mailRecipients(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String customerCode) {
        return Result.success(mailRecipientService.list(keyword, customerCode));
    }

    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "新增邮包收件人")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-recipients")
    public Result<ArMailRecipient> createMailRecipient(@RequestBody ArMailRecipient r) {
        return Result.success(mailRecipientService.create(r));
    }

    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "更新邮包收件人")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-recipients/update")
    public Result<ArMailRecipient> updateMailRecipient(@RequestBody ArMailRecipient r) {
        return Result.success(mailRecipientService.update(r));
    }

    @OperLog(module = "AR_FLOW", operType = "DELETE", desc = "删除邮包收件人")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/mail-recipients/{id}/delete")
    public Result<Void> deleteMailRecipient(@PathVariable Long id) {
        mailRecipientService.delete(id);
        return Result.success();
    }

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/mail-notify-log")
    public Result<List<ArMailNotifyLog>> mailNotifyLog(@RequestParam(required = false) String packageNo,
                                                       @RequestParam(required = false) String status) {
        return Result.success(mailPackageService.notifyLogs(packageNo, status));
    }

    // 会计科目选项（应收补录科目等）

    /** 可选叶子科目：本公司科目 ∪ 租户共享科目，仅启用状态；返回编码+名称供下拉。 */
    @RequiresPerm({"arflow:billing:manage", "arflow:claim:manage"})
    @GetMapping("/subjects")
    public Result<List<MdSubject>> subjects(@RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) String keyword) {
        return Result.success(subjectService.options(companyId, keyword));
    }

    // 客户开票规则

    @RequiresPerm("arflow:billing:manage")
    @GetMapping("/invoice-rules")
    public Result<List<ArInvoiceRule>> invoiceRules() {
        return Result.success(ruleMapper.selectList(new LambdaQueryWrapper<ArInvoiceRule>()
                .orderByAsc(ArInvoiceRule::getCustomerCode)));
    }

    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "保存客户开票规则")
    @RequiresPerm("arflow:billing:manage")
    @PostMapping("/invoice-rules")
    public Result<ArInvoiceRule> saveRule(@RequestBody ArInvoiceRule rule) {
        if (rule.getStatus() == null) {
            rule.setStatus(ArInvoiceRule.ST_ENABLED);
        }
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return Result.success(rule);
    }

    /**
     * 收款核销模板 Excel 导入（任务3.2，附件三 11.4.1，真实 POI 解析）：
     * 客户名称→crm_customer 解析ID；国外收款必须发票号（按 AR-INV-{发票号} 定位应收单指定核销）；
     * 国内收款按客户FIFO自动核销（复用 RcReceiptService 强勾稽）。返回 {created, errors}。
     */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "收款核销模板Excel导入")
    @RequiresPerm("arflow:writeoff:manage")
    @PostMapping(value = "/receipts/import-excel", consumes = {"multipart/form-data"})
    public Result<Map<String, Object>> importReceiptExcel(@RequestParam("file") MultipartFile file) throws Exception {
        ArReceiptExcelService.ParseResult parsed = receiptExcelService.parse(file);
        int created = 0;
        for (Map<String, Object> row : parsed.rows()) {
            try {
                String customerName = (String) row.get("customerName");
                CrmCustomer cust = crmCustomerMapper.selectOne(
                        new LambdaQueryWrapper<CrmCustomer>()
                                .eq(CrmCustomer::getCustomerName, customerName)
                                .last("LIMIT 1"));
                if (cust == null) {
                    throw new BizExceptionLike("客户 " + customerName + " 不存在于客户主数据");
                }
                RcReceipt r = new RcReceipt();
                r.setCustomerId(cust.getId());
                Object no = row.get("receiptNo");
                r.setBillNo(no == null ? null : String.valueOf(no));
                Object rd = row.get("receiptDate");
                r.setReceiptDate(rd == null ? LocalDate.now() : (LocalDate) rd);
                Object amt = row.get("amount");
                r.setAmount(new BigDecimal(String.valueOf(amt)));
                String invoiceNo = (String) row.get("invoiceNo");
                if (invoiceNo != null && !invoiceNo.isBlank()) {
                    RcReceivable rec = rcReceivableMapper.selectOne(
                            new LambdaQueryWrapper<RcReceivable>()
                                    .eq(RcReceivable::getBillNo, "AR-INV-" + invoiceNo.trim())
                                    .last("LIMIT 1"));
                    if (rec == null) {
                        throw new BizExceptionLike("发票号 " + invoiceNo + " 未找到应收单（AR-INV-" + invoiceNo + "）");
                    }
                    RcReceiptLine line = new RcReceiptLine();
                    line.setReceivableId(rec.getId());
                    Object off = row.get("offsetAmount");
                    line.setOffsetAmount(off == null ? r.getAmount() : new BigDecimal(String.valueOf(off)));
                    List<RcReceiptLine> lines = new ArrayList<>();
                    lines.add(line);
                    r.setLines(lines);
                }
                rcReceiptService.create(r);
                created++;
            } catch (Exception e) {
                // 单行核销失败记入 errors 随结果返回，不中断整批导入
                parsed.errors().add("第" + row.get("_excelRow") + "行核销失败：" + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("errors", parsed.errors());
        return Result.success(out);
    }

    /** 轻量异常：复用 BizException 语义但避免在 controller 里扩散错误码导入 */
    private static class BizExceptionLike extends RuntimeException {
        private BizExceptionLike(String msg) {
            super(msg);
        }
    }
}
