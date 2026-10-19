// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpAuditTask;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.service.SharingService;
import com.fssc.sharing.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 共享运营（/api/tenant/sharing/**）：派工单池/认领/完成 + 记账凭证 */
@RestController
@RequestMapping("/api/tenant/sharing")
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;
    private final VoucherService voucherService;
    /** D-DEC-288：人工过账口的实付闸（付款腿凭证未实付不得记账） */
    private final com.fssc.sharing.service.VoucherAutoPostService voucherAutoPostService;
    /** A12（RA-ROOT-09）：自动制证失败事件的只读视图 */
    private final com.fssc.sharing.service.VoucherGenFailureService voucherGenFailureService;
    private final ExpBillMapper expBillMapper;

    // ---------- 派工单 ----------

    @RequiresPerm("sharing:task:list")
    @GetMapping("/task/pool")
    public Result<PageResult<OpAuditTask>> pool(PageQuery query) {
        return Result.success(sharingService.pool(query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("sharing:task:list")
    @GetMapping("/task/mine")
    public Result<PageResult<OpAuditTask>> mine(PageQuery query) {
        return Result.success(sharingService.mine(query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("sharing:task:receive")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "认领派工单")
    @PostMapping("/task/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        sharingService.receive(id);
        return Result.success();
    }

    @RequiresPerm("sharing:task:finish")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "完成派工单")
    @PostMapping("/task/{id}/finish")
    public Result<Void> finish(@PathVariable Long id,
                               // D-DEC-024/D-DEC-030：办结备注（审计口语）随请求提交并落 op_audit_task.remark
                               @RequestParam(required = false) String remark) {
        sharingService.finish(id, remark);
        return Result.success();
    }

    // ---------- 任务池管理（需求 8.2.2：撤回/重新分配/全量查看） ----------

    @RequiresPerm("sharing:task:list")
    @GetMapping("/task/manage")
    public Result<PageResult<OpAuditTask>> manage(PageQuery query, String poolStatus, String taskType,
                                                  String keyword, String activeFlag) {
        // R5-D-236：activeFlag=N 即「未激活（待实物签收）」筛选值域
        return Result.success(sharingService.manage(query.getPageNum(), query.getPageSize(),
                poolStatus, taskType, keyword, activeFlag));
    }

    @RequiresPerm("sharing:task:receive")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "撤回派工单")
    @PostMapping("/task/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        sharingService.withdraw(id);
        return Result.success();
    }

    @RequiresPerm("sharing:task:receive")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "重新分配派工单")
    @PostMapping("/task/{id}/reassign")
    public Result<Void> reassign(@PathVariable Long id, Long assigneeId) {
        sharingService.reassign(id, assigneeId);
        return Result.success();
    }

    // ---------- 工单处理（需求 8.2.3：退单） ----------

    @RequiresPerm("sharing:task:finish")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "退单退回任务池")
    @PostMapping("/task/{id}/return")
    public Result<Void> returnTask(@PathVariable Long id, String reason) {
        sharingService.returnTask(id, reason);
        return Result.success();
    }

    // ---------- 凭证 ----------

    @RequiresPerm("sharing:voucher:generate")
    @OperLog(module = "SHARING", operType = "INSERT", desc = "生成记账凭证")
    @PostMapping("/voucher/generate/{billId}")
    public Result<Long> generate(@PathVariable Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            return Result.fail(400, "单据不存在: " + billId);
        }
        // P0-4 驳回/草稿/撤回等非有效单据禁生成凭证
        if (bill.getStatus() == null
                || !java.util.Set.of("APPROVED", "PAID", "CLOSED").contains(bill.getStatus())) {
            return Result.fail(400, "仅已审批通过的单据可生成凭证，当前状态: "
                    + (bill.getStatus() == null ? "null" : bill.getStatus()));
        }
        // 对公付款单（PAY：SETTLE/PREPAY/AGENCY/AIR）走付款凭证语义：借 2202 / 贷 1002，
        // generatePtpPayment 内置 P0-5 幂等判重与 voucher_no 兜底回写，可安全补生成。
        if ("PAY".equals(bill.getBillType())) {
            return Result.success(voucherService.generatePtpPayment(bill));
        }
        return Result.success(voucherService.generate(bill));
    }

    /**
     * 凭证台账列表。D-DEC-168 后端腿：补「公司／年度／月度」三个可空筛选参数——
     * 原先签名里没有这三项，页面筛选与查询按钮对返回结果完全无效。
     * 三个参数一律 {@code required = false}，不传＝不加条件＝改前口径；数据范围仍由服务层
     * {@code @DataScope} 注入（本接口未绕过可见性闸）。
     */
    @RequiresPerm("sharing:voucher:list")
    @GetMapping("/voucher/page")
    public Result<PageResult<OpVoucher>> voucherPage(PageQuery query,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String sourceType,
                                                     @RequestParam(required = false) Long companyId,
                                                     @RequestParam(required = false) Integer periodYear,
                                                     @RequestParam(required = false) Integer periodMonth,
                                                     // R5-D-149：表头排序透传（前端 sortable="custom" → orderBy/order）。
                                                     // 缺这两个参数时跨页排序契约断裂：排序仅作用当前页、翻页即丢。
                                                     @RequestParam(required = false) String orderBy,
                                                     @RequestParam(required = false) String order) {
        PageResult<OpVoucher> page = voucherService.page(query.getPageNum(), query.getPageSize(), status, sourceType,
                companyId, periodYear, periodMonth, orderBy, order);
        // D-DEC-288（X-49 乙）：读侧打标"资金指令未下发"（payLegUnsettled）——已记账但款未付出的存量付款腿凭证
        // 保持不动账，只在凭证台账明示注记；纯只读，不改 status、不写库、不拦记账（记账闸仍是 assertPaymentLegSettled）。
        voucherAutoPostService.fillPayLegUnsettledFlag(page.getList());
        return Result.success(page);
    }

    /**
     * A12 执行结果视图（RA-ROOT-09）：审批通过后自动制证失败的<b>事件</b>列表，只读、零写。
     * status 传空＝不限（默认口径）；PENDING＝至今未补生成，RESOLVED＝已按同源补出凭证。
     * 覆盖范围仅费用族，理由见 {@link com.fssc.sharing.service.VoucherGenFailureService} 类注释。
     */
    @RequiresPerm("sharing:voucher:list")
    @GetMapping("/voucher/gen-failure/page")
    public Result<PageResult<java.util.Map<String, Object>>> voucherGenFailurePage(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String billNo) {
        return Result.success(voucherGenFailureService.page(
                query.getPageNum(), query.getPageSize(), status, sourceType, billNo));
    }

    /** A12 配套：按来源事件计数（含扫描上限与是否截断，避免把「最近 N 条内」说成全量真值） */
    @RequiresPerm("sharing:voucher:list")
    @GetMapping("/voucher/gen-failure/stats")
    public Result<java.util.Map<String, Object>> voucherGenFailureStats(
            @RequestParam(required = false) String sourceType) {
        return Result.success(voucherGenFailureService.stats(sourceType));
    }

    @RequiresPerm("sharing:voucher:list")
    @GetMapping("/voucher/{id}/lines")
    public Result<List<OpVoucherLine>> voucherLines(@PathVariable Long id) {
        return Result.success(voucherService.lines(id));
    }

    @RequiresPerm("sharing:voucher:post")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "凭证记账")
    @PostMapping("/voucher/{id}/post")
    public Result<Void> voucherPost(@PathVariable Long id) {
        // D-DEC-288：人工过账口的实付闸——付款腿凭证（贷 银行存款）在款项实际付出前不得置 POSTED。
        // 自动通道只在银行指令成功后推（PayBatchService.writeBackPayment／PayBatchBizAction 的 paid 判定），
        // 而本口原先只闸制审分离（D-DEC-166），实测 4 张未付款的付款腿凭证由此被人工记账（现库凭证 14/15/16/23）。
        voucherAutoPostService.assertPaymentLegSettled(id);
        voucherService.post(id, com.fssc.framework.security.UserContext.getRequired().getUserId());
        return Result.success();
    }

    /** P1-2 批量过账：逐张 DRAFT→POSTED，单张失败不影响其余，返回每张结果 */
    /** GAP-01(20260921)：凭证作废（仅 DRAFT 凭证可作废） */
    @RequiresPerm("sharing:voucher:post")
    @OperLog(module = "VOUCHER", operType = "VOID", desc = "凭证作废")
    @PostMapping("/voucher/void/{id}")
    public Result<Void> voucherVoid(@PathVariable Long id) {
        voucherService.voidVoucher(id, com.fssc.framework.security.UserContext.getRequired().getUserId());
        return Result.success();
    }

    /** GAP-01(20260921)：凭证红字冲销（POSTED → 生成方向相反金额相等的红字凭证） */
    @RequiresPerm("sharing:voucher:post")
    @OperLog(module = "VOUCHER", operType = "REVERSE", desc = "凭证红字冲销")
    @PostMapping("/voucher/reverse/{id}")
    public Result<Long> voucherReverse(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? "红字冲销" : String.valueOf(body.get("reason"));
        Long newId = voucherService.reverseVoucher(id, com.fssc.framework.security.UserContext.getRequired().getUserId(), reason);
        return Result.success(newId);
    }

    @RequiresPerm("sharing:voucher:post")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "凭证批量记账")
    @PostMapping("/voucher/batch-post")
    public Result<java.util.Map<Long, String>> voucherBatchPost(
            @org.springframework.web.bind.annotation.RequestBody java.util.List<Long> ids) {
        // D-DEC-288：批量口逐张给原因（与 batchPost 的"单张失败不影响其余"同口径），
        // 未实付的付款腿凭证不进过账队列、原因原样回给界面。
        java.util.Map<Long, String> blocked = voucherAutoPostService.findUnsettledPaymentLegs(ids);
        java.util.List<Long> postable = ids == null ? java.util.List.of()
                : ids.stream().filter(java.util.Objects::nonNull).filter(x -> !blocked.containsKey(x)).toList();
        java.util.Map<Long, String> r = new java.util.LinkedHashMap<>(voucherService.batchPost(postable,
                com.fssc.framework.security.UserContext.getRequired().getUserId()));
        r.putAll(blocked);
        return Result.success(r);
    }

    /** P2-2 凭证断号检测：按期间检查凭证号重复与缺失 */
    @RequiresPerm("sharing:voucher:list")
    @GetMapping("/voucher/gap-check")
    public Result<java.util.Map<String, Object>> voucherGapCheck(@RequestParam(required = false) Integer periodYear,
                                                                 @RequestParam(required = false) Integer periodMonth) {
        return Result.success(voucherService.gapCheck(periodYear, periodMonth));
    }

    /** 工单处理（需求 8.2.3）：修改凭证分录 */
    @RequiresPerm("sharing:voucher:post")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "修改凭证分录")
    @PostMapping("/voucher/{id}/lines/update")
    public Result<Void> voucherLinesUpdate(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody List<OpVoucherLine> lines) {
        voucherService.updateLines(id, lines);
        return Result.success();
    }
}
