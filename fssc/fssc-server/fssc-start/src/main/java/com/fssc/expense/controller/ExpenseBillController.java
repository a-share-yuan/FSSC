package com.fssc.expense.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.service.ExpenseBillService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.util.LinkedHashMap;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 报销单据（/api/tenant/expense/bill/**）：草稿/提交/撤回/作废/详情/我的单据 */
@RestController
@RequestMapping("/api/tenant/expense/bill")
@RequiredArgsConstructor
public class ExpenseBillController {

    private final ExpenseBillService expenseBillService;

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "INSERT", desc = "保存报销草稿")
    @PostMapping("/saveDraft")
    public Result<Long> saveDraft(@RequestBody BillReq req) {
        return Result.success(expenseBillService.saveDraft(req.getBill(), req.getLines(), req.getAttaches()));
    }

    @RequiresPerm("exp:bill:submit")
    @OperLog(module = "EXPENSE_BILL", operType = "SUBMIT", desc = "提交报销单")
    @PostMapping("/submit/{id}")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestParam(required = false) Long approverUserId,
                               @RequestParam(required = false) Long firstAssigneeUserId) {
        expenseBillService.submit(id, approverUserId, firstAssigneeUserId);
        return Result.success();
    }

    /** 驳回单编辑重提：REJECTED → DRAFT，解除旧实例关联；修改后重新提交产生新流程实例。 */
    @RequiresPerm("exp:bill:submit")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "驳回单转编辑重提")
    @PostMapping("/edit/{id}")
    public Result<Void> editForResubmit(@PathVariable Long id) {
        expenseBillService.returnToEdit(id);
        return Result.success();
    }

    /**
     * 审批核减（核定金额三层）：核定报销 / 核定付款 / 核定退款。
     * 勾稽：核定报销≤申请金额；核定付款≤核定报销；核定退款=核定报销−核定付款。
     */
    @RequiresPerm("exp:bill:audit")
    @OperLog(module = "EXPENSE_BILL", operType = "AUDIT", desc = "审批核减（核定三层）")
    @PostMapping("/approve-deduct/{id}")
    public Result<Void> approveDeduct(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        expenseBillService.approveDeduct(id,
                decimal(req.get("approvedAmount")),
                decimal(req.get("approvedPayAmount")),
                decimal(req.get("approvedRefundAmount")),
                req.get("remark") == null ? null : req.get("remark").toString());
        return Result.success();
    }

    private static BigDecimal decimal(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return new BigDecimal(v.toString());
    }

    /**
     * #18 员工借款余额台账：按员工聚合「Σ借款 − Σ已冲销」，含逾期天数，供资金岗催收。
     * onlyOverdue=true 时仅返回已逾期且仍有余额的员工。
     */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/loan-ledger")
    public Result<Object> loanLedger(@RequestParam(required = false) Boolean onlyOverdue,
                                     @RequestParam(required = false) Integer pageNum,
                                     @RequestParam(required = false) Integer pageSize) {
        List<Map<String, Object>> all = expenseBillService.loanLedger(onlyOverdue);
        // 不传分页 = 全量（兼容现有前端）；传则按聚合后列表内存分页返回 {list,total}
        if (pageNum == null || pageSize == null) {
            return Result.success(all);
        }
        int pn = Math.max(1, pageNum);
        int ps = Math.max(1, pageSize);
        int from = Math.min((pn - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("list", all.subList(from, to));
        resp.put("total", all.size());
        return Result.success(resp);
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "CONVERT", desc = "事前申请转报销单")
    @PostMapping("/apply/convert/{applyId}")
    public Result<Long> convertApply(@PathVariable Long applyId) {
        return Result.success(expenseBillService.convertApply(applyId));
    }

    /** 发票池→报销草稿自动填单：按票面生成 CLAIM 草稿（含税规则）并自动占用发票，返回 {billId, billNo} */
    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "INSERT", desc = "发票生成报销草稿")
    @PostMapping("/from-invoice/{invoiceId}")
    public Result<Map<String, Object>> fromInvoice(@PathVariable Long invoiceId, @RequestBody FromInvoiceReq req) {
        return Result.success(expenseBillService.createClaimFromInvoice(invoiceId, req.getItemId(), req.getTitle()));
    }

    @Data
    public static class FromInvoiceReq {
        /** 费用项（md_expense_item，叶子） */
        private Long itemId;
        /** 单据标题（可选，默认「发票报销-号码」） */
        private String title;
    }

    @RequiresPerm("exp:bill:withdraw")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "撤回报销单")
    @PostMapping("/withdraw/{id}")
    public Result<Void> withdraw(@PathVariable Long id) {
        expenseBillService.withdraw(id);
        return Result.success();
    }

    /**
     * 反审核（配套 · 单据反操作链第二环）：APPROVED → DRAFT，退回申请人修改后重提。
     * 仅制单人本人可反审本人单据（撤回同族，exp:bill:withdraw；SoD：财务不代制单人反审）；
     * 前置校验在服务层（已制证/付款已执行/批次在途/存在有效勾稽均拒绝，文案给可执行指引）。
     */
    @RequiresPerm("exp:bill:withdraw")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "反审核（已审批→草稿）")
    @PostMapping("/unaudit/{id}")
    public Result<Void> unaudit(@PathVariable Long id) {
        expenseBillService.unaudit(id);
        return Result.success();
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "DELETE", desc = "作废报销草稿")
    @PostMapping("/void/{id}")
    public Result<Void> voidBill(@PathVariable Long id) {
        expenseBillService.voidBill(id);
        return Result.success();
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "关闭事前申请")
    @PostMapping("/close/{id}")
    public Result<Void> closeApply(@PathVariable Long id) {
        expenseBillService.closeApply(id);
        return Result.success();
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "终止事前申请报销")
    @PostMapping("/terminate/{id}")
    public Result<Void> terminateApply(@PathVariable Long id) {
        expenseBillService.terminateApply(id);
        return Result.success();
    }

    @RequiresPerm("exp:bill:list")
    @GetMapping("/detail/{id}")
    public Result<BillDetail> detail(@PathVariable Long id) {
        BillDetail d = new BillDetail();
        d.setBill(expenseBillService.detail(id));
        d.setLines(expenseBillService.lines(id));
        d.setAttaches(expenseBillService.attaches(id));
        return Result.success(d);
    }

    /**
     * 我的单据分页。startDate/endDate（申请日期闭区间）与 source（渠道）均为可选，
     * 不传或空串即不加条件——老前端不传时与本方法原先逐字同结果。
     */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/myPage")
    public Result<PageResult<ExpBill>> myPage(PageQuery query, String billType, String status,
                                              @RequestParam(required = false) BigDecimal minBalance,
                                              @RequestParam(required = false) String keyword,
                                              String startDate, String endDate, String source,
                                              @RequestParam(required = false) String billSubType,
                                              @RequestParam(required = false) Long customerId) {
        return Result.success(expenseBillService.myPage(query.getPageNum(), query.getPageSize(),
                billType, status, minBalance, keyword, startDate, endDate, source, billSubType, customerId));
    }

    /** 机票查询分页（keyword 服务端过滤 + used 核销状态服务端过滤 + relationUsed 回填） */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/air-ticket/page")
    public Result<PageResult<ExpBill>> airTicketPage(PageQuery query, @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer used) {
        return Result.success(expenseBillService.airTicketPage(query.getPageNum(), query.getPageSize(), keyword, used));
    }

    @Data
    public static class BillReq {
        private ExpBill bill;
        private List<ExpBillLine> lines;
        private List<ExpBillAttach> attaches;
    }

    @Data
    public static class BillDetail {
        private ExpBill bill;
        private List<ExpBillLine> lines;
        private List<ExpBillAttach> attaches;
    }
}
