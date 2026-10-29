// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.entity.AuditReconcile;
import com.fssc.audit.mapper.AuditReconcileMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.service.VoucherService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 长链路一致性对账。
 * 恒等式：单据总额 = 凭证借贷合计 = 已付金额 + 未付金额；最终状态单据应有凭证/付款记录。
 * 定时每日 02:00 + 手动触发；不一致落 audit_reconcile，支持人工闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {

    /** 已成功付款状态 */
    private static final List<String> PAID_STATUS = Arrays.asList("PAID", "SUCCESS");

    private final ExpBillMapper expBillMapper;
    private final OpVoucherMapper voucherMapper;
    private final OpVoucherLineMapper voucherLineMapper;
    private final ExpBillPaymentMapper paymentMapper;
    private final AuditReconcileMapper reconcileMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /** 每日 02:00 全量对账 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledReconcile() {
        jobHeartbeatService.run("ReconcileService", () -> {
            try {
                int n = runReconcile();
                log.info("定时对账完成，异常 {} 条", n);
            } catch (Exception e) {
                log.error("定时对账失败", e);
            }
        });
    }

    /** 手动触发全量对账，返回新增/更新异常数 */
    public int runReconcile() {
        AtomicInteger count = new AtomicInteger();
        TenantContext.runIgnoreTenant(() -> {
            List<ExpBill> bills = expBillMapper.selectList(null);
            for (ExpBill b : bills) {
                TenantContext.callAs(b.getTenantId(), () -> {
                    checkVoucher(b, count);
                    checkPayment(b, count);
                    checkStatus(b, count);
                });
            }
        });
        log.info("对账完成：异常 {} 条", count.get());
        return count.get();
    }

    // 三条核对规则

    /** ① 凭证核对：单据关联凭证借贷合计 == 单据总额 */
    private void checkVoucher(ExpBill bill, AtomicInteger count) {
        if (bill.getTotalAmount() == null) return;
        // 仅取本费用族来源的活凭证——裸 bill_id 会撞上跨域同 id 单据的凭证（幻影差异行）；
        // 剔 VOID 取活凭证规则（写侧已「作废即级联」办结，历史历史差异照常落台账交人工）
        List<OpVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, bill.getId())
                .in(OpVoucher::getSourceType, VoucherService.EXPENSE_BILL_SOURCES)
                .ne(OpVoucher::getStatus, "VOID"));
        if (vouchers.isEmpty()) return; // 无凭证不算异常（未到凭证环节）
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (OpVoucher v : vouchers) {
            debit = debit.add(nvl(v.getDebitTotal()));
            credit = credit.add(nvl(v.getCreditTotal()));
        }
        boolean ok = debit.compareTo(bill.getTotalAmount()) == 0 && credit.compareTo(bill.getTotalAmount()) == 0;
        if (!ok) {
            upsert(bill, "VOUCHER",
                    "借贷合计=单据总额", "借 " + plain(debit) + " / 贷 " + plain(credit) + " vs 单据 " + plain(bill.getTotalAmount()),
                    "凭证借贷合计与单据总额不一致", count);
        }
    }

    /** ② 付款核对：成功付款累计 == 单据 paid_amount，且 ≤ 单据总额 */
    private void checkPayment(ExpBill bill, AtomicInteger count) {
        List<ExpBillPayment> pays = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, bill.getId())
                .in(ExpBillPayment::getPayStatus, PAID_STATUS));
        if (pays.isEmpty()) return; // 无成功付款不算异常
        BigDecimal sum = pays.stream().map(p -> nvl(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = nvl(bill.getPaidAmount());
        boolean ok = sum.compareTo(paid) == 0 && sum.compareTo(bill.getTotalAmount()) <= 0;
        if (!ok) {
            upsert(bill, "PAYMENT",
                    "成功付款累计=已付金额且≤总额", "付款累计 " + plain(sum) + " vs 已付 " + plain(paid) + " / 总额 " + plain(bill.getTotalAmount()),
                    "付款累计与单据已付金额不一致", count);
        }
    }

    /** ③ 状态核对：最终状态（APPROVED/PAID）单据应有凭证或付款记录 */
    private void checkStatus(ExpBill bill, AtomicInteger count) {
        String st = bill.getStatus();
        if (!"APPROVED".equals(st) && !"PAID".equals(st)) return;
        // 凭证计数仅取本费用族来源——裸 bill_id 会被跨域同 id 凭证虚增计数，
        // 掩盖「最终状态单据实际无凭证」的真实异常（不加状态过滤：VOID 也是走过凭证环节的记录）
        long voucherCnt = voucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, bill.getId())
                .in(OpVoucher::getSourceType, VoucherService.EXPENSE_BILL_SOURCES));
        long payCnt = paymentMapper.selectCount(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, bill.getId()));
        if (voucherCnt == 0 && payCnt == 0) {
            upsert(bill, "STATUS",
                    "已结束的单据仍有凭证或付款记录", "凭证 0 条 / 付款 0 条",
                    "单据已 " + st + " 但无凭证与付款记录", count);
        }
    }

    // 异常写入数据库（同项 OPEN 更新，否则插入）

    private void upsert(ExpBill bill, String item, String expect, String actual, String diff, AtomicInteger count) {
        try {
            AuditReconcile exist = reconcileMapper.selectOne(new LambdaQueryWrapper<AuditReconcile>()
                    .eq(AuditReconcile::getTenantId, bill.getTenantId())
                    .eq(AuditReconcile::getBillId, bill.getId())
                    .eq(AuditReconcile::getCheckItem, item)
                    .eq(AuditReconcile::getStatus, "OPEN")
                    .last("limit 1"));
            if (exist != null) {
                exist.setExpectValue(expect);
                exist.setActualValue(actual);
                exist.setDiffDesc(diff);
                reconcileMapper.updateById(exist);
            } else {
                AuditReconcile r = new AuditReconcile();
                r.setTenantId(bill.getTenantId());
                r.setBillType(bill.getBillType());
                r.setBillId(bill.getId());
                r.setBillNo(bill.getBillNo());
                r.setCheckItem(item);
                r.setExpectValue(expect);
                r.setActualValue(actual);
                r.setDiffDesc(diff);
                r.setStatus("OPEN");
                reconcileMapper.insert(r);
            }
            count.incrementAndGet();
        } catch (Exception e) {
            log.warn("对账异常落库失败 billId={} item={}", bill.getId(), item, e);
        }
    }

    // 查询 / 人工处理

    public PageResult<AuditReconcile> page(long pageNum, long pageSize, String billType, String status) {
        LambdaQueryWrapper<AuditReconcile> qw = new LambdaQueryWrapper<>();
        if (billType != null && !billType.isBlank()) {
            qw.eq(AuditReconcile::getBillType, billType);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(AuditReconcile::getStatus, status);
        }
        qw.orderByDesc(AuditReconcile::getId);
        Page<AuditReconcile> p = reconcileMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 标记已处理（只更新 status，不删除） */
    public void resolve(Long id, String remark) {
        AuditReconcile r = reconcileMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账记录不存在: " + id);
        }
        AuditReconcile upd = new AuditReconcile();
        upd.setId(id);
        upd.setStatus("RESOLVED");
        upd.setResolveRemark(remark);
        upd.setResolveTime(LocalDateTime.now());
        try {
            upd.setResolveBy(UserContext.getRequired().getUserId());
        } catch (Exception ignored) {
            // 无登录上下文（定时任务/系统调账）时不记录处理人，属预期降级
        }
        reconcileMapper.updateById(upd);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
