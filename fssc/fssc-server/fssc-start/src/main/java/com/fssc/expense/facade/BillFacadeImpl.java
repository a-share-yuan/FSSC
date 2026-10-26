// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.mapper.ExpBillAttachMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.facade.BillFacade;
import com.fssc.framework.facade.dto.BillArchiveDTO;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BillFacade 实现：核心域（expense）内部直接访问 ExpBillMapper，
 * 对外只暴露接口方法，使影像域和档案域不再依赖 ExpBillMapper。
 */
@Service
@RequiredArgsConstructor
public class BillFacadeImpl implements BillFacade {

    private final ExpBillMapper expBillMapper;
    private final ExpBillAttachMapper attachMapper;
    private final OrgGuard orgGuard;

    @Override
    public PageResult<BillArchiveDTO> findPendingArchiveBills(long pageNum, long pageSize,
                                                                String billType, String keyword,
                                                                String archiveStatus) {
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<>();
        qw.in(ExpBill::getStatus, "APPROVED", "PAID", "CLOSED")
                .in(ExpBill::getImageStatus, "PARTIAL", "COMPLETE");
        // D-DEC-276b（B1，2026-09-28 已裁）：原先无条件 `ne ARCHIVED` 把已归档单据从清单里彻底抹掉
        // ⇒ 归档后无法在同一页面核对"归到哪一档、谁归的"，也让去重只能靠运气。
        // 归档记录序时簿是**状态四值筛选**（已归档/未归档/归档中/不限），已归档项仍在列表内。
        // 故改为按入参筛选，默认（空值）＝只看未归档，与页面原行为一致；重复立卷由 ArchiveService 的状态机强制校验拦（B2）。
        // 未归档一侧仍写 ne＋isNull：库里 archive_status 为 NULL 的历史行不该因三值逻辑凭空消失。
        String st = archiveStatus == null ? "" : archiveStatus.trim();
        if ("ARCHIVED".equalsIgnoreCase(st)) {
            qw.eq(ExpBill::getArchiveStatus, "ARCHIVED");
        } else if (!"ALL".equalsIgnoreCase(st)) {
            qw.and(w -> w.ne(ExpBill::getArchiveStatus, "ARCHIVED").or().isNull(ExpBill::getArchiveStatus));
        }
        if (billType != null && !billType.isBlank()) {
            qw.eq(ExpBill::getBillType, billType);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ExpBill::getBillNo, keyword).or().like(ExpBill::getTitle, keyword));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(ExpBill::getCompanyId, scopeC);
        }
        qw.orderByAsc(ExpBill::getId);
        Page<ExpBill> p = expBillMapper.selectPage(Page.of(pageNum, pageSize), qw);
        List<BillArchiveDTO> dtos = p.getRecords().stream().map(this::toDTO).collect(Collectors.toList());
        return PageResult.of(dtos, p.getTotal(), pageNum, pageSize);
    }

    @Override
    public BillArchiveDTO findBillForArchive(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        return bill == null ? null : toDTO(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markArchived(Long billId, Long userId, LocalDateTime now) {
        ExpBill upd = new ExpBill();
        upd.setId(billId);
        upd.setArchiveStatus("ARCHIVED");
        upd.setArchiveTime(now);
        upd.setArchiveBy(userId);
        expBillMapper.updateById(upd);
    }

    @Override
    public List<BillArchiveDTO> findPendingBillsForScheduledArchive(int limit) {
        List<ExpBill> pending = new ArrayList<>();
        TenantContext.runIgnoreTenant(() -> pending.addAll(expBillMapper.selectList(
                new LambdaQueryWrapper<ExpBill>()
                        .in(ExpBill::getStatus, "APPROVED", "PAID")
                        .ne(ExpBill::getArchiveStatus, "ARCHIVED")
                        .in(ExpBill::getImageStatus, "PARTIAL", "COMPLETE")
                        .last("limit " + limit))));
        return pending.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<Long> getAttachFileIdsByBillId(Long billId) {
        return attachMapper.selectList(new LambdaQueryWrapper<ExpBillAttach>()
                        .eq(ExpBillAttach::getBillId, billId))
                .stream()
                .map(ExpBillAttach::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private BillArchiveDTO toDTO(ExpBill bill) {
        BillArchiveDTO dto = new BillArchiveDTO();
        dto.setId(bill.getId());
        dto.setBillNo(bill.getBillNo());
        dto.setTitle(bill.getTitle());
        dto.setBillType(bill.getBillType());
        dto.setStatus(bill.getStatus());
        dto.setArchiveStatus(bill.getArchiveStatus());
        dto.setImageStatus(bill.getImageStatus());
        // R5-D-244：实物流交付状态外传（归档前置校验：未签收不得归档）
        dto.setDeliveryStatus(bill.getDeliveryStatus());
        dto.setCompanyId(bill.getCompanyId());
        dto.setPeriodYear(bill.getPeriodYear());
        dto.setPeriodMonth(bill.getPeriodMonth());
        dto.setTenantId(bill.getTenantId());
        dto.setVoucherNo(bill.getVoucherNo());
        dto.setProcInstId(bill.getProcInstId());
        dto.setArchiveTime(bill.getArchiveTime());
        dto.setArchiveBy(bill.getArchiveBy());
        return dto;
    }
}
