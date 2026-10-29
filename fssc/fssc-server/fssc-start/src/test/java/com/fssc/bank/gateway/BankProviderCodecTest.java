// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T2.5 / 域3-R3 多银行 provider 分派验证。
 *
 * <p>验证点：同一批付款明细，在不同 provider 下产出不同报文——
 * 这正是「新增一家银行只加配置、不改业务码」的可测形态。</p>
 */
class BankProviderCodecTest {

    private static final List<BankGateway.PayItem> ITEMS = List.of(
            new BankGateway.PayItem("收款人A", "工商银行", "6222000000000001",
                    new BigDecimal("100.00"), "货款"),
            new BankGateway.PayItem("收款人B", "建设银行", "6217000000000002",
                    new BigDecimal("200.50"), "服务费"));

    private static ExtInterfaceConfig cfgWith(String extra) {
        ExtInterfaceConfig c = new ExtInterfaceConfig();
        c.setCategory("BANK");
        c.setProvider("TEMPLATE");
        c.setExtraConfig(extra);
        return c;
    }

    @Test
    @DisplayName("GENERIC：产出通用键名，与收编前行为一致（零回归基线）")
    void genericKeepsLegacyShape() {
        Map<String, Object> p = new GenericBankCodec().buildPayload("B1", ITEMS, null);
        assertTrue(p.containsKey("batchNo"));
        assertTrue(p.containsKey("totalAmount"));
        assertTrue(p.containsKey("items"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) p.get("items");
        assertEquals(2, items.size());
        assertTrue(items.get(0).containsKey("payeeName"));
        assertTrue(items.get(0).containsKey("account"));
        assertEquals(new BigDecimal("300.50"), p.get("totalAmount"));
    }

    @Test
    @DisplayName("TEMPLATE：按 extra_config 重命名键 —— 换一家银行只改配置")
    void templateRenamesByConfig() {
        String extra = """
                {"header":{"batchNo":"batchNo","count":"cnt","total":"sumAmt"},
                 "item":{"payeeName":"payeName","account":"acctNo","amount":"amt","postscript":"remark"},
                 "items":"detailList"}""";
        Map<String, Object> p = new TemplateBankCodec()
                .buildPayload("B1", ITEMS, cfgWith(extra));
        assertEquals("B1", p.get("batchNo"));
        assertEquals(2, p.get("cnt"));
        assertEquals(new BigDecimal("300.50"), p.get("sumAmt"));
        assertTrue(p.containsKey("detailList"));
        assertFalse(p.containsKey("items"), "明细键应被配置改名");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) p.get("detailList");
        assertTrue(items.get(0).containsKey("payeName"));
        assertTrue(items.get(0).containsKey("acctNo"));
        assertTrue(items.get(0).containsKey("amt"));
        assertFalse(items.get(0).containsKey("payeeName"), "原键名应已被替换");
        // 未在映射中出现的字段保留通用键名，不丢数据
        assertTrue(items.get(0).containsKey("bankName"));
    }

    @Test
    @DisplayName("TEMPLATE：extra_config 缺失/非法时退化为通用键名，不抛穿")
    void templateDegradesGracefully() {
        Map<String, Object> none = new TemplateBankCodec().buildPayload("B1", ITEMS, null);
        assertTrue(none.containsKey("items"), "无配置应退回通用明细键");

        Map<String, Object> bad = new TemplateBankCodec()
                .buildPayload("B1", ITEMS, cfgWith("{ 这不是合法 JSON"));
        assertTrue(bad.containsKey("items"), "非法 JSON 不应抛异常");
    }

    @Test
    @DisplayName("流水号：可解析则返回，不可解析返回 null 交调用方降级")
    void serialNoContract() {
        BankProviderCodec g = new GenericBankCodec();
        assertEquals(List.of("S1", "S2"),
                g.parseSerialNos("{\"serialNos\":[\"S1\",\"S2\"]}", 2));
        assertNull(g.parseSerialNos("{\"other\":1}", 2), "无 serialNos 应返回 null 而非空列表");
        assertNull(g.parseSerialNos(null, 2));
    }
}
