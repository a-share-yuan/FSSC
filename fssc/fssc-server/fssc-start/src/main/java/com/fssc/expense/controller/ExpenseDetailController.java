package com.fssc.expense.controller;

import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.entity.ExpTravel;
import com.fssc.expense.service.ExpenseDetailService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
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

/** 单据细分（/api/tenant/expense/detail/**）：差旅行 / 借款冲抵 / 关联单 */
@RestController
@RequestMapping("/api/tenant/expense/detail")
@RequiredArgsConstructor
public class ExpenseDetailController {

    private final ExpenseDetailService detailService;

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "保存差旅行")
    @PostMapping("/travels/{billId}")
    public Result<Void> saveTravels(@PathVariable Long billId, @RequestBody List<ExpTravel> travels) {
        detailService.saveTravels(billId, travels);
        return Result.success();
    }

    @RequiresPerm("exp:bill:list")
    @GetMapping("/travels/{billId}")
    public Result<List<ExpTravel>> travels(@PathVariable Long billId) {
        return Result.success(detailService.travels(billId));
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "添加借款冲抵")
    @PostMapping("/loan-offset/{billId}")
    public Result<Long> addLoanOffset(@PathVariable Long billId, @RequestBody ExpLoanOffset offset) {
        return Result.success(detailService.addLoanOffset(billId, offset));
    }

    @RequiresPerm("exp:bill:list")
    @GetMapping("/loan-offset/{billId}")
    public Result<List<ExpLoanOffset>> loanOffsets(@PathVariable Long billId) {
        return Result.success(detailService.loanOffsets(billId));
    }

    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "INSERT", desc = "添加单据关联")
    @PostMapping("/relation")
    public Result<Long> addRelation(@RequestParam Long sourceBillId, @RequestParam Long targetBillId,
                                    @RequestParam String relationType,
                                    @RequestParam(required = false) BigDecimal useAmount) {
        return Result.success(detailService.addRelation(sourceBillId, targetBillId, relationType, useAmount));
    }

    /** #13 预付冲销：报销单冲销预付款单(PAY/PREPAY)，并释放预付款占用的预算 */
    @RequiresPerm("exp:bill:save")
    @OperLog(module = "EXPENSE_BILL", operType = "UPDATE", desc = "冲销预付款")
    @PostMapping("/prepay-offset/{billId}")
    public Result<Long> writeOffPrepay(@PathVariable Long billId, @RequestBody Map<String, Object> req) {
        Object p = req.get("prepayBillId");
        Object a = req.get("amount");
        if (p == null || a == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "prepayBillId 与 amount 必填");
        }
        return Result.success(detailService.writeOffPrepay(billId,
                Long.valueOf(p.toString()), new BigDecimal(a.toString())));
    }

    @RequiresPerm("exp:bill:list")
    @GetMapping("/relation/{billId}")
    public Result<List<ExpBillRelation>> relations(@PathVariable Long billId) {
        return Result.success(detailService.relations(billId));
    }

    /**
     * 借款冲抵候选（D-UI-03 配套，2026-10-19）。
     *
     * <p>与 {@code /loan-offset/{billId}}（回读本单已登记的冲抵）配对：那个是「已选了什么」，
     * 这个是「还能选什么」。返回里 {@code availableBalance} 已扣掉在途占用，
     * 与 {@code addLoanOffset} 的放行闸同源，前端不必再拿账面余额硬勾全额然后被拒。</p>
     */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/loan-candidates")
    public Result<List<com.fssc.expense.dto.LoanCandidateDTO>> loanCandidates(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer limit) {
        return Result.success(detailService.loanCandidates(companyId, limit));
    }
}
