// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OCR 响应字段映射解析器（纯函数，无 Spring 依赖，便于单测）。
 *
 * <p>mapping 为 JSON 对象：键 = {@link OcrResult} 字段名，值 = 响应 JSON 的一层点号路径
 * （如 "words_result.InvoiceCode.word"）。缺省/非法/缺字段一律容错：
 * mapping 为空或解析失败回退 {@link #DEFAULT_MAPPING}（百度式字段名）；
 * 响应缺对应路径则该字段置 null，不影响其余字段。</p>
 */
public final class OcrFieldMapper {

    /** 默认映射（百度智能云增值税发票识别风格字段名）：接入其它真通道只需改 sys_config ocr.api.mapping */
    public static final String DEFAULT_MAPPING = "{"
            + "\"invoiceCode\":\"words_result.InvoiceCode.word\","
            + "\"invoiceNo\":\"words_result.InvoiceNum.word\","
            + "\"invoiceType\":\"words_result.InvoiceType.word\","
            + "\"invoiceDate\":\"words_result.InvoiceDate.word\","
            + "\"amount\":\"words_result.TotalTax.word\","
            + "\"taxAmount\":\"words_result.TaxAmount.word\","
            + "\"netAmount\":\"words_result.AmountInFiguers.word\","
            + "\"sellerName\":\"words_result.SellerName.word\","
            + "\"sellerTaxNo\":\"words_result.SellerPayerIdentificationNumber.word\","
            + "\"buyerName\":\"words_result.PurchaserName.word\","
            + "\"buyerTaxNo\":\"words_result.PurchaserPayerIdentificationNumber.word\""
            + "}";

    /** 金额/税额字段（映射为 BigDecimal） */
    private static final Set<String> DECIMAL_FIELDS = Set.of("amount", "taxAmount", "netAmount");
    /** OcrResult 中允许被 mapping 配置的字符串字段 */
    private static final Set<String> STRING_FIELDS = Set.of(
            "invoiceCode", "invoiceNo", "invoiceType", "invoiceDate",
            "sellerName", "sellerTaxNo", "buyerName", "buyerTaxNo", "rawText");

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 宽松输入（容忍 2026-8-10 单位数月/日），输出统一 yyyy-MM-dd */
    private static final DateTimeFormatter LOOSE_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");

    private OcrFieldMapper() {
    }

    /** 解析 mapping JSON → 字段→路径表。空/非法/空对象一律回退默认映射。 */
    public static Map<String, String> parseMapping(String mappingJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (mappingJson != null && !mappingJson.isBlank()) {
            try {
                JsonNode root = JSON.readTree(mappingJson);
                if (root != null && root.isObject()) {
                    root.fields().forEachRemaining(e -> {
                        String key = e.getKey();
                        if ((DECIMAL_FIELDS.contains(key) || STRING_FIELDS.contains(key))
                                && e.getValue() != null && e.getValue().isTextual()
                                && !e.getValue().asText().isBlank()) {
                            result.put(key, e.getValue().asText().trim());
                        }
                    });
                }
            } catch (Exception ignore) {
                // 非法 JSON → 回退默认
                result.clear();
            }
        }
        if (result.isEmpty()) {
            return parseDefault();
        }
        return result;
    }

    /**
     * 按 mapping 从响应 JSON 提取票面字段。
     *
     * @param mappingJson 字段映射表 JSON（空则用默认）
     * @param responseJson OCR 服务响应原文 JSON
     * @return 识别结果；响应非法 JSON 时返回 ok=false + errorMsg（不抛穿）
     */
    public static OcrResult map(String mappingJson, String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return OcrResult.fail("OCR 响应为空");
        }
        JsonNode root;
        try {
            root = JSON.readTree(responseJson);
        } catch (Exception e) {
            return OcrResult.fail("识别结果无法解析：" + trim(e.getMessage()));
        }
        if (root == null || !root.isObject()) {
            return OcrResult.fail("识别结果格式不正确，请重试或更换识别通道");
        }
        Map<String, String> mapping = parseMapping(mappingJson);
        OcrResult r = new OcrResult();
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            JsonNode node = readPath(root, e.getValue());
            String text = asText(node);
            if (text == null) {
                continue; // 缺字段容错：该字段留空
            }
            switch (e.getKey()) {
                case "amount" -> r.setAmount(toDecimal(text));
                case "taxAmount" -> r.setTaxAmount(toDecimal(text));
                case "netAmount" -> r.setNetAmount(toDecimal(text));
                case "invoiceDate" -> r.setInvoiceDate(normalizeDate(text));
                case "invoiceCode" -> r.setInvoiceCode(text);
                case "invoiceNo" -> r.setInvoiceNo(text);
                case "invoiceType" -> r.setInvoiceType(text);
                case "sellerName" -> r.setSellerName(text);
                case "sellerTaxNo" -> r.setSellerTaxNo(text);
                case "buyerName" -> r.setBuyerName(text);
                case "buyerTaxNo" -> r.setBuyerTaxNo(text);
                case "rawText" -> r.setRawText(text);
                default -> { /* 未知字段忽略 */ }
            }
        }
        return r;
    }

    private static Map<String, String> parseDefault() {
        Map<String, String> def = new LinkedHashMap<>();
        try {
            JsonNode root = JSON.readTree(DEFAULT_MAPPING);
            root.fields().forEachRemaining(e -> def.put(e.getKey(), e.getValue().asText()));
        } catch (Exception impossible) {
            // 默认映射常量化，不会失败
        }
        return def;
    }

    /** 简单一层点号路径取值：a.b.c → root.get(a).get(b).get(c)；任一段缺失返回 null */
    static JsonNode readPath(JsonNode root, String path) {
        JsonNode cur = root;
        for (String seg : path.split("\\.")) {
            if (cur == null || seg.isBlank()) {
                return null;
            }
            cur = cur.get(seg.trim());
        }
        return cur;
    }

    /** 节点文本化：容器/缺失返回 null；数值/布尔取字符串表示；去首尾空白后空串归 null */
    static String asText(JsonNode node) {
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String text = node.asText();
        if (text == null) {
            return null;
        }
        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    /** 金额容错解析：容忍 ¥/￥/千分位逗号；不可解析返回 null（不阻断其余字段） */
    static BigDecimal toDecimal(String text) {
        if (text == null) {
            return null;
        }
        try {
            String s = text.replace("¥", "").replace("￥", "").replace(",", "").replace(" ", "");
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日期归一为 yyyy-MM-dd：支持 2026-08-10 / 2026/08/10 / 2026年8月10日 / 20260810；失败原样返回 */
    static String normalizeDate(String text) {
        String s = text.replace("年", "-").replace("月", "-").replace("日", "").trim();
        s = s.replace("/", "-");
        if (s.matches("\\d{8}")) {
            s = s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8);
        }
        try {
            return LocalDate.parse(s, LOOSE_DATE).format(ISO_DATE);
        } catch (Exception e) {
            return text; // 无法识别的格式保留原文，交由票面校验/人工复核兜底
        }
    }

    private static String trim(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 100 ? msg.substring(0, 100) : msg;
    }
}
