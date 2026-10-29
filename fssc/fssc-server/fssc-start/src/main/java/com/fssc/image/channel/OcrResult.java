// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;

/**
 * OCR 通道识别结果（结构化票面字段 + 成败标记）。
 * 真实通道按 ocr.api.mapping 字段映射填充；Mock 通道生成确定性数据用于演示。
 * 约定：通道失败不抛穿，置 ok=false + errorMsg，由调用方（OcrService）决定页面状态。
 */
@Data
public class OcrResult {

    /** 识别是否成功（票面是否拿到响应）；字段是否齐全由调用方按 #15 自洽校验裁断 */
    private boolean ok = true;
    /** 失败原因（ok=false 时有值；起 ok=true 但 inPool=false 时也带值＝未入池的真实原因） */
    private String errorMsg;
    /**
     * 「已识别」的界面语义 ≠ 已入池：票面是否真的进了发票池 {@code inv_invoice}。
     * <ul>
     *  <li>{@link Boolean#TRUE}＝已在池（本次入池成功，或去重命中改前已录）；</li>
     *  <li>{@link Boolean#FALSE}＝拿到票面但没进池（要素不全／金额不自洽／入池被拒），原因见 {@link #errorMsg}；</li>
     *  <li>{@code null}＝未走写入数据库入池链＝本地版式解析（OFD／XML／带文本层 PDF）只回填票面、不做入池，
     *      或识别本身失败。前端不得把 null 当 false 用（两档出路不同）。</li>
     * </ul>
     * 判据一律在后端 {@code OcrService#recognizeAndRecord} 产出，不在前端重算（会两源漂移）。
     */
    private Boolean inPool;

    private String invoiceCode;
    /** 票据类型编码（与前端 constants/invoiceType.js 对应）：VAT_SPECIAL/VAT_NORMAL/ELECTRONIC/
     *  RAIL_HIGH/TRAIN/AIR/TAXI/RIDE_HALLING/QUOTA/TOLL/PARKING/MEDICAL/OTHER */
    private String invoiceType;
    /** 开票日期，统一 yyyy-MM-dd（通道内已做常见格式归一） */
    private String invoiceDate;
    private String invoiceNo;
    /** 价税合计 */
    private BigDecimal amount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 不含税 */
    private BigDecimal netAmount;
    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;
    /** 首条货物或应税劳务、服务名称（取票面首要货名，用于报销按商品名智能匹配费用类型；空串表示未提取） */
    private String primaryItemName;
    /** 识别全文/原始报文摘要（记录，回填 img_file.ocr_text） */
    private String rawText;

    /** 扫描版 PDF 标记：true 表示无有效文本层，需转图像 OCR（非本地版式解析场景恒为 false）。不入库。 */
    @JsonIgnore
    private boolean scanned;

    /** 扫描版 PDF 首页渲染出的 PNG 字节，交图像 OCR 通道使用；非扫描件为 null。不入库。 */
    @JsonIgnore
    private transient byte[] renderedImageBytes;

    /** 通道失败快捷构造（不抛穿的统一出口） */
    public static OcrResult fail(String errorMsg) {
        OcrResult r = new OcrResult();
        r.setOk(false);
        r.setErrorMsg(errorMsg);
        return r;
    }
}
