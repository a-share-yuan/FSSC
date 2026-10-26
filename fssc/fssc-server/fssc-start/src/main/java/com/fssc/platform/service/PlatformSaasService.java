// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfBill;
import com.fssc.platform.entity.PfOrder;
import com.fssc.platform.entity.PfTenantStat;
import com.fssc.platform.mapper.PfBillMapper;
import com.fssc.platform.mapper.PfOrderMapper;
import com.fssc.platform.mapper.PfTenantStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 平台 SaaS 运营：订购订单（pf_order）+ 账单（pf_bill）+ 租户用量统计（pf_tenant_stat）。
 * 【平台表】tenantId 恒为 null，只碰 pf_* 表；跨租户统计走 runIgnoreTenant 且只允许聚合。
 */
@Service
@RequiredArgsConstructor
public class PlatformSaasService {

    private final PfOrderMapper orderMapper;
    private final PfBillMapper billMapper;
    private final PfTenantStatMapper statMapper;

    // ---------- 订单 ----------

    public PageResult<PfOrder> orderPage(long pageNum, long pageSize, Long refTenantId, String payStatus) {
        LambdaQueryWrapper<PfOrder> qw = new LambdaQueryWrapper<>();
        if (refTenantId != null) {
            qw.eq(PfOrder::getRefTenantId, refTenantId);
        }
        if (payStatus != null && !payStatus.isBlank()) {
            qw.eq(PfOrder::getPayStatus, payStatus);
        }
        qw.orderByDesc(PfOrder::getId);
        Page<PfOrder> p = orderMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long createOrder(PfOrder order) {
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单号必填");
        }
        long dup = orderMapper.selectCount(new LambdaQueryWrapper<PfOrder>()
                .eq(PfOrder::getOrderNo, order.getOrderNo()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单号已存在: " + order.getOrderNo());
        }
        order.setId(null);
        if (order.getOrderType() == null) {
            order.setOrderType("NEW");
        }
        if (order.getDuration() == null) {
            order.setDuration(12);
        }
        if (order.getPayStatus() == null) {
            order.setPayStatus("UNPAID");
        }
        orderMapper.insert(order);
        return order.getId();
    }

    /** 确认收款：UNPAID → PAID，回写支付信息与服务周期 */
    public void confirmPaid(Long id, String payMethod) {
        PfOrder exist = orderMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单不存在: " + id);
        }
        LocalDateTime now = LocalDateTime.now();
        PfOrder upd = new PfOrder();
        upd.setId(id);
        upd.setPayStatus("PAID");
        upd.setPayTime(now);
        upd.setPayMethod(payMethod);
        upd.setStartTime(exist.getStartTime() != null ? exist.getStartTime() : now);
        int months = exist.getDuration() == null ? 12 : exist.getDuration();
        upd.setEndTime(exist.getStartTime() != null ? exist.getStartTime().plusMonths(months)
                : now.plusMonths(months));
        orderMapper.updateById(upd);
    }

    // ---------- 账单 ----------

    public PageResult<PfBill> billPage(long pageNum, long pageSize, Long refTenantId, String status) {
        LambdaQueryWrapper<PfBill> qw = new LambdaQueryWrapper<>();
        if (refTenantId != null) {
            qw.eq(PfBill::getRefTenantId, refTenantId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(PfBill::getStatus, status);
        }
        qw.orderByDesc(PfBill::getId);
        Page<PfBill> p = billMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long createBill(PfBill bill) {
        if (bill.getBillNo() == null || bill.getBillNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单号必填");
        }
        long dup = billMapper.selectCount(new LambdaQueryWrapper<PfBill>()
                .eq(PfBill::getBillNo, bill.getBillNo()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单号已存在: " + bill.getBillNo());
        }
        bill.setId(null);
        if (bill.getStatus() == null) {
            bill.setStatus("UNPAID");
        }
        billMapper.insert(bill);
        return bill.getId();
    }

    /** 收款核销：累计 paid_amount，达应收即结清 */
    public void receivePayment(Long id, java.math.BigDecimal paid) {
        PfBill exist = billMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单不存在: " + id);
        }
        java.math.BigDecimal acc = exist.getPaidAmount() == null ? java.math.BigDecimal.ZERO : exist.getPaidAmount();
        acc = acc.add(paid);
        PfBill upd = new PfBill();
        upd.setId(id);
        upd.setPaidAmount(acc);
        if (acc.compareTo(exist.getAmount()) >= 0) {
            upd.setStatus("PAID");
        }
        billMapper.updateById(upd);
    }

    // ---------- 租户用量统计（仅聚合，跨租户时走 runIgnoreTenant） ----------

    public PageResult<PfTenantStat> statPage(long pageNum, long pageSize, Long refTenantId, LocalDate statDate) {
        LambdaQueryWrapper<PfTenantStat> qw = new LambdaQueryWrapper<>();
        if (refTenantId != null) {
            qw.eq(PfTenantStat::getRefTenantId, refTenantId);
        }
        if (statDate != null) {
            qw.eq(PfTenantStat::getStatDate, statDate);
        }
        qw.orderByDesc(PfTenantStat::getStatDate);
        Page<PfTenantStat> p = statMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 写入/更新某租户某日统计快照（去重：存在则累加覆盖） */
    public void upsertStat(PfTenantStat stat) {
        PfTenantStat exist = statMapper.selectOne(new LambdaQueryWrapper<PfTenantStat>()
                .eq(PfTenantStat::getRefTenantId, stat.getRefTenantId())
                .eq(PfTenantStat::getStatDate, stat.getStatDate()));
        if (exist == null) {
            stat.setId(null);
            statMapper.insert(stat);
        } else {
            PfTenantStat upd = new PfTenantStat();
            upd.setId(exist.getId());
            upd.setUserCount(stat.getUserCount());
            upd.setActiveUser(stat.getActiveUser());
            upd.setBillCount(stat.getBillCount());
            upd.setStorageMb(stat.getStorageMb());
            upd.setApiCount(stat.getApiCount());
            statMapper.updateById(upd);
        }
    }

    /** 跨租户聚合（仅允许 sum/count 聚合，禁止查明细） */
    public List<java.util.Map<String, Object>> aggregateUsage(LocalDate statDate) {
        final List<java.util.Map<String, Object>>[] holder = new List[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = statMapper.selectMaps(
                new LambdaQueryWrapper<PfTenantStat>()
                        .eq(PfTenantStat::getStatDate, statDate)
                        .select(PfTenantStat::getRefTenantId,
                                PfTenantStat::getUserCount,
                                PfTenantStat::getBillCount,
                                PfTenantStat::getApiCount)));
        return holder[0];
    }
}
