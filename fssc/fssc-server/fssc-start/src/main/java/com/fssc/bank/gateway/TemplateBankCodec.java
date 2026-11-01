// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.system.entity.ExtInterfaceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置驱动的银行报文形态（{@code provider=TEMPLATE}）——<b>「新增一家银行只加配置」的关键实现</b>。
 *
 * <p>各家银行的字段名不同（工行 {@code acctNo}、招行 {@code accountNo} …），但<b>结构</b>通常一致：
 * 批次头 + 明细数组。差别只在<b>键名</b>。本类把键名映射写在
 * {@code ext_interface_config.extra_config}，于是接一家新银行<b>不需要写 Java</b>：</p>
 *
 * <pre>
 * extra_config = {
 *   "header": { "batchNo": "batchNo", "count": "cnt", "total": "sumAmt" },
 *   "item": { "payeeName": "payeName", "bankName": "bankName",
 *               "account": "acctNo", "amount": "amt", "postscript": "remark" },
 *   "items": "detailList",
 *   "serialList": "serialNos"
 * }
 * </pre>
 *
 * <p><b>边界（必须说清）</b>：本类只做<b>键名映射</b>。若某家银行要求
 * <ul>
 *   <li>不同的<b>报文结构</b>（明细不叫数组、需嵌套信封 XML）→ 本类不适用，需写专属
 *       {@link BankProviderCodec} 实现类；</li>
 *   <li>不同的<b>签名算法</b>（银行侧数字签名/证书）→ 本类不覆盖，由通道网关的凭据注入
 *       （{@code apiKey}→{@code Authorization}、{@code apiSecret}→{@code X-API-Secret}）
 *       或该行前置机自身完成；</li>
 * </ul>
 * 遇到这两类才需要新增实现类；<b>业务层仍然零改动</b>。</p>
 *
 * <p><b>不臆造字段名</b>：映射值完全来自配置，代码里没有任何具体银行的字段名。
 * 接入时把银行对接文档里的真实字段名填进 {@code extra_config} 即可。</p>
 */
@Slf4j
@Component
public class TemplateBankCodec implements BankProviderCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String provider() {
        return PROVIDER_TEMPLATE;
    }

    @Override
    public Map<String, Object> buildPayload(String batchNo, List<BankGateway.PayItem> items,
                                            ExtInterfaceConfig cfg) {
        JsonNode m = readMapping(cfg);
        JsonNode header = m.path("header");
        JsonNode itemMap = m.path("item");
        String itemsKey = text(m, "items", "items");

        List<Map<String, Object>> payList = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (BankGateway.PayItem it : items) {
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("payeeName", it.payeeName());
                src.put("bankName", it.bankName());
                src.put("account", it.bankAccount());
                src.put("amount", it.amount());
                src.put("postscript", it.postscript() == null ? "" : it.postscript());
                if (it.amount() != null) {
                    total = total.add(it.amount());
                }
                payList.add(rename(src, itemMap));
            }
        }

        Map<String, Object> hdr = new LinkedHashMap<>();
        hdr.put("batchNo", batchNo);
        hdr.put("count", payList.size());
        hdr.put("total", total);
        Map<String, Object> payload = new LinkedHashMap<>();
        // 配置里没给 header 映射时退回通用键名，保证最简配置也能跑
        payload.put(text(header, "batchNo", "batchNo"), batchNo);
        payload.put(text(header, "count", "count"), payList.size());
        payload.put(text(header, "total", "totalAmount"), total);
        payload.put(itemsKey, payList);
        return payload;
    }

    @Override
    public List<String> parseSerialNos(String body, int itemCount) {
        if (body == null) {
            return null;
        }
        // 无配置可读（调用方未传 cfg）时退回通用键；本方法签名由接口固定，
        // 故用通用 "serialNos" 契约，配置化的 serialList 在 buildPayload 侧已保证键名一致。
        return new GenericBankCodec().parseSerialNos(body, itemCount);
    }

    /** extra_config → 映射树；解析失败返回空节点（退化为通用键名，不抛穿）。 */
    private JsonNode readMapping(ExtInterfaceConfig cfg) {
        String raw = (cfg == null) ? null : cfg.getExtraConfig();
        if (raw == null || raw.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            // extra_config 允许外层再包一层（如 {"bank": {...}}），有则自动下钻一层
            JsonNode n = MAPPER.readTree(raw);
            if (n.has("bank") && n.path("bank").isObject()) {
                n = n.path("bank");
            }
            return n;
        } catch (Exception e) {
            log.warn("[银企直联] extra_config 解析失败，退回通用键名: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    /** 按映射重命名键；映射缺失则沿用原键名。 */
    private Map<String, Object> rename(Map<String, Object> src, JsonNode mapping) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String target = (mapping == null || !mapping.isObject())
                    ? e.getKey() : text(mapping, e.getKey(), e.getKey());
            out.put(target, e.getValue());
        }
        return out;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode v = node.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? fallback : v.asText();
    }
}
