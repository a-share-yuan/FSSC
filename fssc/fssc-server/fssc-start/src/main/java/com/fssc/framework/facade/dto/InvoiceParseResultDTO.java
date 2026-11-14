package com.fssc.framework.facade.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票附件解析结果（影像域对外契约）。
 * <p>
 * 与 image 域内部的 OcrResult 字段同名同义，但本 DTO 不含 image 包的实现类型，
 * 供报销/付款附件上传等主域调用方经 {@code ImageFacade} 取值，不再直连 OcrService。
 */
@Data
public class InvoiceParseResultDTO {

    /** 是否取到票面（字段是否齐全由影像域内部判据裁断，此处只透传结论） */
    private boolean ok = true;

    /** 失败原因／未入池原因（ok=true 但 inPool=false 时也带值） */
    private String errorMsg;

    /** true=已入发票池；false=拿到票面但未入池；null=未走入库入池链（本地版式解析） */
    private Boolean inPool;

    private String invoiceCode;
    private String invoiceType;
    private String invoiceDate;
    private String invoiceNo;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;
    private String primaryItemName;
    private String rawText;
}
