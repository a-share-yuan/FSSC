// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.facade;

import com.fssc.framework.facade.InvoiceFacade;
import com.fssc.framework.facade.dto.InvoicePoolDTO;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link InvoiceFacade} 的主域实现（同进程版）。
 * <p>
 * 只做 DTO → 实体的装配，业务校验与事务仍由 {@link InvoiceService#create} 承担
 * （该方法自带 {@code @FsscDataSource("master")} + REQUIRES_NEW，跨域路由与事务边界已就位）。
 * <p>
 * 影像档案独立部署后，本类替换为 HTTP 实现即可，调用方无感。
 */
@Service
@RequiredArgsConstructor
public class InvoiceFacadeImpl implements InvoiceFacade {

    private final InvoiceService invoiceService;

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
        return invoiceService.create(invoice);
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
        return invoiceService.existsInPool(code, dto.getInvoiceNo());
    }
}
