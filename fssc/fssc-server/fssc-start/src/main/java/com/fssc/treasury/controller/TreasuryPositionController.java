// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.bank.entity.BankBalance;
import com.fssc.bank.mapper.BankBalanceMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.treasury.entity.TrFundPlan;
import com.fssc.treasury.mapper.TrFundPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金头寸视图（P1-4 资金域补齐：无资金头寸视图 → 建成）。
 * <p>
 * 一个接口聚合四类数据，形成司库日幕：
 * <ul>
 *   <li>账户余额：md_bank_account × bank_balance（每账户取最近一次余额表日）</li>
 *   <li>待付流出：exp_bill_payment 中 WAITING 付款记录合计（未组批/已组批未打款全覆盖）</li>
 *   <li>应收未收流入：rc_receivable 剩余余额合计（NORMAL/OVERDUE/PARTIAL）</li>
 *   <li>当期资金计划：tr_fund_plan 按期间汇总 计划/实际 收支</li>
 * </ul>
 * 净头寸 = 账户总余额 + 应收未收 − 待付流出。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/treasury/position")
public class TreasuryPositionController {

    private final MdBankAccountMapper bankAccountMapper;
    private final BankBalanceMapper bankBalanceMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final RcReceivableMapper receivableMapper;
    private final TrFundPlanMapper fundPlanMapper;
    private final OrgGuard orgGuard;

    /**
     * 资金头寸日幕视图（越权读修复 R5-D-187，级别 高）。
     * <p>授权校验：新增 {@code @RequiresPerm}。台账命名码为 {@code treasury:position:list}；本页面挂在
     * 资金管理工作台菜单（sys_menu 3072，perms {@code treasury:fundplan:list,treasury:creditbill:list}）
     * 之下、前端未按页签单独设权，故采用全项目既有「多码 OR 迁移」写法（见 {@link RequiresPerm} javadoc
     * 与 SoDCheckController）：新码 treasury:position:list（随登记件播种）与工作台两个既有读码取并集放行，
     * 避免"加校验未补授"期间合法用户 403。持有任一即通过；零权限账号无任何码 ⇒ 被校验拦下。</p>
     * <p>数据范围：本域读路径统一走 {@link OrgGuard#enforceCompany()}（同 TrFundPlanService.page、
     * RcReceivableService.page 规则）——按调用者公司办结；集团级账号返回 null 不限（域内既有语义）。
     * 三个子查询（账户 / 待付 / 应收）逐一办结，非仅首个；显式点名入参绝不放宽。</p>
     */
    @RequiresPerm({"treasury:position:list", "treasury:fundplan:list", "treasury:creditbill:list"})
    @OperLog(module = "TREASURY", operType = "QUERY", desc = "资金头寸查询")
    @GetMapping
    public Result<Map<String, Object>> position(@RequestParam(required = false) String period,
                                                @RequestParam(required = false) Long companyId) {
        Map<String, Object> out = new LinkedHashMap<>();

        // 读路径公司数据范围：集团级 null=不限，其余恒为本公司；显式点名不得放宽（explicit filter never widens）
        Long scopeC = orgGuard.enforceCompany();
        if (companyId != null && scopeC != null && !scopeC.equals(companyId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "越权访问：无权查看其他公司的资金头寸");
        }
        Long effScope = scopeC != null ? scopeC : companyId;

        // 1) 各账户最近余额（md_bank_account 按调用者公司办结）
        List<MdBankAccount> accounts = bankAccountMapper.selectList(new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getDelFlag, 0)
                .eq(effScope != null, MdBankAccount::getCompanyId, effScope)
                .orderByAsc(MdBankAccount::getId));
        List<Long> scopedAccountIds = accounts.stream().map(MdBankAccount::getId).toList();
        List<Map<String, Object>> accountRows = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        if (!accounts.isEmpty()) {
            List<BankBalance> balances = bankBalanceMapper.selectList(new LambdaQueryWrapper<BankBalance>()
                    .in(BankBalance::getAccountId, scopedAccountIds)
                    .orderByDesc(BankBalance::getBalanceDate));
            Map<Long, BankBalance> latest = new LinkedHashMap<>();
            for (BankBalance b : balances) {
                latest.putIfAbsent(b.getAccountId(), b); // 已按日期倒序，首个即最近
            }
            for (MdBankAccount a : accounts) {
                BankBalance b = latest.get(a.getId());
                // R5-D-185：余额表未登记时退化为账户主数据当前余额（改前恒 0，与"账户确实没钱"同形，
                // 界面按 balanceDate 是否为空标注「未登记（取账户余额）」；登记入口见资金管理→头寸→登记余额）
                BigDecimal bal = b == null || b.getBalance() == null
                        ? (a.getBalance() == null ? BigDecimal.ZERO : a.getBalance())
                        : b.getBalance();
                totalBalance = totalBalance.add(bal);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("accountId", a.getId());
                row.put("accountName", a.getAccountName());
                row.put("bankName", a.getBankName());
                row.put("bankAccount", a.getBankAccount());
                row.put("balance", bal);
                row.put("balanceDate", b == null ? null : b.getBalanceDate());
                accountRows.add(row);
            }
        }
        out.put("accounts", accountRows);
        out.put("totalBalance", totalBalance);

        // 2) 待付流出：WAITING 付款记录（未组批 + 已组批未执行打款）
        //    exp_bill_payment 无 company_id 列 ⇒ 以「付款账户所属公司」为关联：payAccountId 即
        //    md_bank_account.id，故用已按公司办结出的 scopedAccountIds 限定；作用域内无账户时用
        //    DENY 哨兵（id=-1 恒不命中）fail-closed，避免退化成全租户口。集团级 effScope=null 不限。
        LambdaQueryWrapper<ExpBillPayment> payQw = new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getPayStatus, "WAITING");
        if (effScope != null) {
            if (scopedAccountIds.isEmpty()) {
                payQw.eq(ExpBillPayment::getId, -1L);
            } else {
                payQw.in(ExpBillPayment::getPayAccountId, scopedAccountIds);
            }
        }
        List<ExpBillPayment> waiting = expBillPaymentMapper.selectList(payQw);
        BigDecimal payableWaiting = waiting.stream()
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        out.put("payableWaiting", payableWaiting);
        out.put("payableWaitingCount", waiting.size());

        // 3) 应收未收：剩余余额合计（rc_receivable 按调用者公司办结）
        List<RcReceivable> open = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .in(RcReceivable::getStatus, "NORMAL", "OVERDUE", "PARTIAL")
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .eq(effScope != null, RcReceivable::getCompanyId, effScope));
        BigDecimal receivableOpen = open.stream()
                .map(r -> r.getRemainAmount() == null ? BigDecimal.ZERO : r.getRemainAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        out.put("receivableOpen", receivableOpen);
        out.put("receivableOpenCount", open.size());

        // 4) 当期资金计划（period 缺省取当前月）
        String cur = period == null || period.isBlank()
                ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) : period;
        List<TrFundPlan> plans = fundPlanMapper.selectList(new LambdaQueryWrapper<TrFundPlan>()
                .eq(TrFundPlan::getPeriod, cur));
        BigDecimal planIncome = BigDecimal.ZERO, planOutcome = BigDecimal.ZERO;
        BigDecimal actualIncome = BigDecimal.ZERO, actualOutcome = BigDecimal.ZERO;
        for (TrFundPlan p : plans) {
            planIncome = planIncome.add(nz(p.getIncomeAmount()));
            planOutcome = planOutcome.add(nz(p.getOutcomeAmount()));
            actualIncome = actualIncome.add(nz(p.getIncomeActual()));
            actualOutcome = actualOutcome.add(nz(p.getOutcomeActual()));
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("period", cur);
        plan.put("planIncome", planIncome);
        plan.put("planOutcome", planOutcome);
        plan.put("actualIncome", actualIncome);
        plan.put("actualOutcome", actualOutcome);
        out.put("plan", plan);

        // 5) 净头寸 = 余额 + 应收未收 − 待付
        out.put("netPosition", totalBalance.add(receivableOpen).subtract(payableWaiting));
        return Result.success(out);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
