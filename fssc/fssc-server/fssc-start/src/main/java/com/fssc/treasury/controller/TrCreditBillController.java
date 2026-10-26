// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.treasury.entity.TrCreditBill;
import com.fssc.treasury.service.TrCreditBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 资金收款单管理（/api/tenant/treasury/credit-bill/**) */
@RestController
@RequestMapping("/api/tenant/treasury/credit-bill")
@RequiredArgsConstructor
public class TrCreditBillController {

    private final TrCreditBillService service;

    @RequiresPerm("treasury:creditbill:list")
    @GetMapping("/page")
    public Result<PageResult<TrCreditBill>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 String status) {
        return Result.success(service.page(pageNum, pageSize, status));
    }

    @RequiresPerm("treasury:creditbill:list")
    @GetMapping("/{id}")
    public Result<TrCreditBill> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    /**
     * D-DEC-418 丙档：本笔到账与收款核销的勾稽体检（只读，不改账、不改状态、不出证、不起流）。
     *
     * <p>背景：到账环节（tr_credit_bill，TR_RECEIPT 审批收尾出「借 1002／贷 1122」凭证）与
     * 核销环节（rc_receipt，冲减 rc_receivable 余额）是同一笔钱的两半，但<b>两环节之间没有任何共同外键</b>，
     * 现库只靠同日同额人眼对齐；核销环节还完全不出证（现库 op_voucher 无 RC_RECEIPT 类凭证）。
     * 跨域打通该做成"到账自动核销"还是"核销补出证"属账务规则裁决（甲/乙），本轮<b>只落丙</b>：
     * 把两环节现状、可用的启发式候选、以及"这不是勾稽结论"如实告诉使用者。
     *
     * <p>读侧另起于本域：已上线的联查读侧 {@code BillTraceChainController}（禁改）实测全文
     * 不含 tr_credit_bill／rc_receipt／TR_RECEIPT，不覆盖这两个环节，故无重复规则、无冲突。
     *
     * <p>权限复用既有 {@code treasury:creditbill:list}（sys_menu id=3072），不新建权限码。
     */
    @RequiresPerm("treasury:creditbill:list")
    @GetMapping("/{id}/recon-trace")
    public Result<java.util.Map<String, Object>> reconTrace(@PathVariable Long id) {
        return Result.success(service.reconTrace(id));
    }

    /**
     * 渠道流水认领，生成应收收款。
     * <p>D-DEC-422：本类原为资金域唯一"零 {@code @OperLog}"的写控制器——实测 tr_credit_bill
     * 已建行、wf_instance 已起流，而 sys_oper_log 在该时间窗零行（对照同包
     * {@code TreasuryPositionController:54} 与应收域 {@code RcReceivableController:37}），
     * "库有痕、日志无行"＝到账这类最易被伪造的单据反而无法按操作日志回溯建单人/金额/时间。
     * 现三个写端点全挂记录（两个核销入口 {@code auto-recon}／{@code confirm-recon} 必挂，
     * 旁路核销不再无痕）；{@code page}/{@code detail} 不加 QUERY，与全项目写动作打点、
     * 读动作不打点的既有惯例一致（全仓 QUERY 型 {@code @OperLog} 仅 3 处）。
     */
    @RequiresPerm("treasury:creditbill:create")
    @OperLog(module = "TREASURY", operType = "INSERT", desc = "资金收款-渠道流水认领建单")
    @PostMapping("/receive")
    public Result<Long> receiveRecord(@RequestBody TrCreditBill bill) {
        return Result.success(service.receiveRecord(bill));
    }

    /** 资金收款核销：对全部待核销收款单按同金额未对账银行流水自动勾对（D-DEC-436：原注释与界面同名
     *  「自动对账」，与本域的「银行流水勾对」混称，现按真实对象改名）
     *  <p>D-DEC-420：本端点返回命中条数（前端按数字查询，契约不可加字段），"本人建单—本人核销"的
     *  复核提示在服务端按单据记录记入日志，界面提示走单条确认核销端点的 data 提示位。 */
    @RequiresPerm("treasury:creditbill:recon")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金收款-按同金额银行流水自动核销")
    @PostMapping("/auto-recon")
    public Result<Integer> autoRecon() {
        return Result.success(service.autoRecon());
    }

    /**
     * 人工核销（确认对账）。
     * <p>D-DEC-420 丙档：本端点不经审批环节，同持建单权＋核销权的人可"本人建单—本人确认核销"。
     * 不拆权、不拦操作，只在 data 里回非阻断复核提示位（{@code selfRecon}／{@code warning}）供界面显示。
     */
    @RequiresPerm("treasury:creditbill:recon")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "资金收款-人工确认核销")
    @PostMapping("/{id}/confirm-recon")
    public Result<java.util.Map<String, Object>> confirmRecon(@PathVariable Long id) {
        return Result.success(service.confirmRecon(id));
    }
}