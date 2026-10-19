package com.fssc.sharing.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdMatchTolerance;
import com.fssc.sharing.service.ThreeWayMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对公付款三单匹配（合同↔发票↔付款单）。
 * P1：异常复审入口 POST /{billId}/review → 发票校验异常审批（PTP03 INVOICE_MATCH）。
 */
@RestController
@RequestMapping("/api/tenant/pay/match")
@RequiredArgsConstructor
public class ThreeWayMatchController {

    private final ThreeWayMatchService matchService;

    @RequiresPerm("pay:match:list")
    @GetMapping("/page")
    public Result<PageResult<ThreeWayMatchService.MatchRow>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String matchStatus) {
        return Result.success(matchService.page(pageNum, pageSize, keyword, matchStatus));
    }

    @RequiresPerm("pay:match:list")
    @GetMapping("/check")
    public Result<ThreeWayMatchService.MatchRow> check(@RequestParam Long billId) {
        return Result.success(matchService.check(billId));
    }

    @RequiresPerm("pay:match:review")
    @PostMapping("/{billId}/review")
    @OperLog(module = "PTP", operType = "INSERT", desc = "发票校验异常发起复审审批")
    public Result<String> review(@PathVariable Long billId) {
        return Result.success(matchService.review(billId));
    }

    /** P3-补链：IFS 手工三单匹配审批（INVOICE_MATCH_MANUAL），走人工匹配审批链路 */
    @RequiresPerm("pay:match:review")
    @PostMapping("/{billId}/manual-review")
    @OperLog(module = "PTP", operType = "INSERT", desc = "IFS手工三单匹配发起审批")
    public Result<String> manualReview(@PathVariable Long billId,
                                      @RequestParam(required = false) String reason) {
        return Result.success(matchService.manualReview(billId, reason));
    }

    /** #17 三单匹配异常按类型路由派单：差异按责任归属派给采购/仓储/应付会计/对账专员 */
    @RequiresPerm("pay:match:review")
    @PostMapping("/{billId}/route")
    @OperLog(module = "PTP", operType = "INSERT", desc = "三单匹配异常按类型路由派单")
    public Result<Map<String, Object>> route(@PathVariable Long billId) {
        return Result.success(matchService.route(billId));
    }

    /**
     * 手工关联采购订单/收货单/采购合同到付款单（三单匹配选单操作）。
     * <p>G2R-01（2026-10-19）：原实现只接收 poId / poReceiptId，而 NO_CONTRACT 的处理出口文案
     * 明确要求「到 三单匹配 / 采购订单 补挂合同」——出口指向了一个不存在的入口，
     * 已生成的结算单（其 PO 已审批、无法再编辑挂合同）永远消不掉合同腿 WARN。
     * 此处补齐 contractId 参数，让合同腿具备可达的补挂路径。
     */
    @RequiresPerm("pay:match:review")
    @PostMapping("/{billId}/link")
    @OperLog(module = "PTP", operType = "UPDATE", desc = "手工关联PO/收货单/合同到付款单")
    public Result<String> link(@PathVariable Long billId, @RequestBody Map<String, Long> body) {
        return Result.success(matchService.link(billId, body.get("poId"), body.get("poReceiptId"),
                body.get("contractId")));
    }

    /** 查询付款单关联的发票明细列表 */
    @RequiresPerm("pay:match:list")
    @GetMapping("/{billId}/invoices")
    public Result<List<Map<String, Object>>> invoices(@PathVariable Long billId) {
        return Result.success(matchService.listInvoices(billId));
    }

    // ---------------- P1-6 三单匹配容差配置 ----------------

    @RequiresPerm("pay:match:list")
    @GetMapping("/tolerance/list")
    public Result<List<MdMatchTolerance>> toleranceList() {
        return Result.success(matchService.listTolerances());
    }

    @RequiresPerm("pay:match:list")
    @OperLog(module = "PTP", operType = "UPDATE", desc = "三单匹配容差配置")
    @PostMapping("/tolerance/upsert")
    public Result<MdMatchTolerance> toleranceUpsert(@RequestBody MdMatchTolerance body) {
        return Result.success(matchService.upsertTolerance(body));
    }

    /** 41号审查 P2-13:删除容差配置(含差异化行) */
    @RequiresPerm("pay:match:list")
    @OperLog(module = "PTP", operType = "DELETE", desc = "删除三单匹配容差配置")
    @PostMapping("/tolerance/{id}/delete")
    public Result<Void> toleranceDelete(@PathVariable Long id) {
        matchService.deleteTolerance(id);
        return Result.success();
    }
}
