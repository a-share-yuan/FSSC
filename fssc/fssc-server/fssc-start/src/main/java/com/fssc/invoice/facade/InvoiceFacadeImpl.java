// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.framework.facade.InvoiceFacade;
import com.fssc.framework.facade.dto.InvoicePoolDTO;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.mapper.InvBillInvoiceMapper;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link InvoiceFacade} 的主域实现（同进程版）。
 * <p>
 * 类级路由＝发票域路由键 {@code invoice}（独立库就绪前别名 master）：本类里直接打
 * {@code inv_invoice} / {@code inv_bill_invoice} 的方法都归发票域；入池两法仍委托
 * {@link InvoiceService}（其方法自带的 {@code @FsscDataSource("master")} + REQUIRES_NEW
 * 事务边界已就位，方法级注解优先于本类级注解）。
 * <p>
 * 发票域独立部署后，本类替换为 HTTP 实现即可，调用方无感。
 * <p>
 * 为什么 {@link InvoiceService} 走 {@code ObjectProvider}：共享域的 {@code VoucherService}
 * 既依赖本 Facade，又出现在 {@code InvoiceService} 的依赖链上（{@code InvoiceService → VoucherService}），
 * 立即注入会构成构造器循环；延迟到首次入池时再解析即可。
 */
@Service
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceFacadeImpl implements InvoiceFacade, InvoiceDomainFacade {

    private final InvInvoiceMapper invInvoiceMapper;
    private final InvBillInvoiceMapper invBillInvoiceMapper;
    /**
     * 用 ObjectProvider 延迟解析：共享域 {@code VoucherService} 反向依赖本 Facade，
     * 而 {@code InvoiceService} 又依赖 {@code VoucherService}，直接构造注入会构成构造器循环
     * （Spring Boot 默认禁止循环引用）。读方法只碰两个 Mapper，不需要服务实例。
     */
    private final ObjectProvider<InvoiceService> invoiceServiceProvider;

    @Override
    public Long poolFromOcr(InvoicePoolDTO dto) {
        InvInvoice invoice = new InvInvoice();
        invoice.setTenantId(dto.getTenantId());
        invoice.setCompanyId(dto.getCompanyId());
        invoice.setInvoiceNo(dto.getInvoiceNo());
        invoice.setInvoiceCode(dto.getInvoiceCode());
        invoice.setInvoiceType(dto.getInvoiceType());
        invoice.setSellerName(dto.getSellerName());
        invoice.setSellerTaxNo(dto.getSellerTaxNo());
        invoice.setBuyerName(dto.getBuyerName());
        invoice.setBuyerTaxNo(dto.getBuyerTaxNo());
        invoice.setInvoiceDate(dto.getInvoiceDate());
        invoice.setNetAmount(dto.getNetAmount());
        invoice.setTaxAmount(dto.getTaxAmount());
        invoice.setAmount(dto.getAmount());
        // 缺陷修复（H-0）：OCR 入池回写影像页ID（inv_invoice.img_file_id），字段已由 DESC 核实存在
        invoice.setImgFileId(dto.getImgFileId());
        // 与 OcrService#toInvoice 原语义一致：票面状态由主域补齐
        invoice.setStatus("NORMAL");
        // 【D-DEC-115】OCR 识别成功 ≠ 验真通过：旧写法入池即 verify_status=PASS，
        // 系统内没有任何外部验真报文支撑，却等于「已验真」；且 PASS 不在定时验真捞取集合
        // （NONE/PENDING/FAILED）内，这张票此后再也不会被真正验真。
        // 改为 NONE=待验真：OCR 入池票带影像（img_file_id 非空），满足 D-DEC-074 前置谓词，
        // 会被定时验真捞取；外部验真通道启用后由 InvoiceVerifyScheduler 落真实结论。
        invoice.setVerifyStatus("NONE");
        return invoiceServiceProvider.getObject().create(invoice);
    }

    /**
     * D-DEC-463（去重环节②）：入池前的"已在池"判定，装配规则与本类的查重列一致——
     * 全电票的 invoiceCode 归一为空串（{@code create} 里同样把 NULL 归一为 ''，否则 {@code = ''} 永不命中）。
     */
    @Override
    public boolean existsInPool(InvoicePoolDTO dto) {
        if (dto == null) {
            return false;
        }
        String code = dto.getInvoiceCode() == null ? "" : dto.getInvoiceCode().trim();
        return invoiceServiceProvider.getObject().existsInPool(code, dto.getInvoiceNo());
    }

    // ==================== 发票主表读 ====================

    @Override
    public InvInvoice findById(Long invoiceId) {
        return invoiceId == null ? null : invInvoiceMapper.selectById(invoiceId);
    }

    @Override
    public List<InvInvoice> findByIds(Collection<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        return invInvoiceMapper.selectBatchIds(invoiceIds);
    }

    @Override
    public List<InvInvoice> findByInvoiceNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return List.of();
        }
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceNo, invoiceNo)
                .orderByAsc(InvInvoice::getId));
    }

    @Override
    public InvInvoice findOneByInvoiceNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return null;
        }
        return invInvoiceMapper.selectOne(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceNo, invoiceNo)
                .last("limit 1"));
    }

    @Override
    public long countByCodeAndNo(String invoiceCode, String invoiceNo) {
        if (invoiceNo == null) {
            return 0L;
        }
        Long n = invInvoiceMapper.selectCount(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceNo, invoiceNo)
                .eq(InvInvoice::getInvoiceCode, invoiceCode));
        return n == null ? 0L : n;
    }

    @Override
    public List<InvInvoice> findByCompanyIdAndStatus(Long companyId, String status) {
        if (companyId == null) {
            return List.of();
        }
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getCompanyId, companyId)
                .eq(InvInvoice::getStatus, status));
    }

    @Override
    public Set<Long> companyIdsByStatus(String status) {
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                        .eq(InvInvoice::getStatus, status)
                        .isNotNull(InvInvoice::getCompanyId))
                .stream()
                .map(InvInvoice::getCompanyId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    @Override
    public List<InvInvoice> findByCertifyStatus(String certifyStatus) {
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getCertifyStatus, certifyStatus));
    }

    @Override
    public List<InvInvoice> findBySellerNameAndDateRange(String sellerName, LocalDate fromInclusive, LocalDate toInclusive) {
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getSellerName, sellerName)
                .ge(InvInvoice::getInvoiceDate, fromInclusive)
                .le(InvInvoice::getInvoiceDate, toInclusive));
    }

    @Override
    public List<InvInvoice> findBySellerNameBefore(String sellerName, LocalDate beforeExclusive) {
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getSellerName, sellerName)
                .lt(InvInvoice::getInvoiceDate, beforeExclusive));
    }

    @Override
    public List<InvInvoice> findBySourceDoc(String sourceDocType, Long sourceDocId) {
        return invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getSourceDocType, sourceDocType)
                .eq(InvInvoice::getSourceDocId, sourceDocId)
                .orderByDesc(InvInvoice::getCreateTime));
    }

    // ==================== 发票主表写 ====================

    @Override
    public Long insertInvoice(InvInvoice invoice) {
        invInvoiceMapper.insert(invoice);
        return invoice.getId();
    }

    @Override
    public boolean updateStatus(Long invoiceId, String status) {
        if (invoiceId == null) {
            return false;
        }
        InvInvoice upd = new InvInvoice();
        upd.setId(invoiceId);
        upd.setStatus(status);
        return invInvoiceMapper.updateById(upd) > 0;
    }

    // ==================== 单据-发票勾稽 ====================

    @Override
    public List<InvBillInvoice> findLinksByBillId(Long billId) {
        if (billId == null) {
            return List.of();
        }
        return invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getBillId, billId));
    }

    @Override
    public List<InvBillInvoice> findLinksByBillIds(Collection<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return List.of();
        }
        return invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .in(InvBillInvoice::getBillId, billIds));
    }

    @Override
    public List<InvBillInvoice> findLinksByInvoiceId(Long invoiceId) {
        if (invoiceId == null) {
            return List.of();
        }
        return invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
    }

    @Override
    public List<InvBillInvoice> findLinksByInvoiceIdExcludingBill(Long invoiceId, Long billId) {
        if (invoiceId == null) {
            return List.of();
        }
        return invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId)
                .ne(InvBillInvoice::getBillId, billId));
    }

    @Override
    public long countLinksByInvoiceIdsExcludingBill(Collection<Long> invoiceIds, Long billId) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return 0L;
        }
        Long n = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .ne(InvBillInvoice::getBillId, billId)
                .in(InvBillInvoice::getInvoiceId, invoiceIds));
        return n == null ? 0L : n;
    }

    @Override
    public long countActiveLinksByBillId(Long billId) {
        if (billId == null) {
            return 0L;
        }
        Long n = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getBillId, billId)
                .eq(InvBillInvoice::getDelFlag, 0));
        return n == null ? 0L : n;
    }

    @Override
    public boolean existsLink(Long billId, Long invoiceId) {
        if (billId == null || invoiceId == null) {
            return false;
        }
        Long n = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getBillId, billId)
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        return n != null && n > 0;
    }

    @Override
    public Long createLink(Long billId, Long invoiceId, BigDecimal amount) {
        InvBillInvoice link = new InvBillInvoice();
        link.setBillId(billId);
        link.setInvoiceId(invoiceId);
        link.setAmount(amount == null ? BigDecimal.ZERO : amount);
        invBillInvoiceMapper.insert(link);
        return link.getId();
    }
}
