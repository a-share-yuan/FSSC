package com.fssc.bank.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用银行报文形态（{@code provider=GENERIC}）——收编前的既有行为，零回归基线。
 *
 * <p>请求体为通用 JSON：{@code {batchNo, count, totalAmount, items:[{payeeName, bankName,
 * account, amount, postscript}]}}。响应按最小契约解析：body 含 {@code "serialNos":[...]} 则取之，
 * 否则由调用方降级为占位流水号。</p>
 *
 * <p>本类是<b>兜底 provider</b>：即使某银行尚未提供专属编解码器，只要通道配置的 provider
 * 留空或填 {@code GENERIC}，都会落到这里，行为与 R1/R2 完全一致。</p>
 */
@Slf4j
@Component
public class GenericBankCodec implements BankProviderCodec {

    @Override
    public String provider() {
        return PROVIDER_GENERIC;
    }

    @Override
    public Map<String, Object> buildPayload(String batchNo, List<BankGateway.PayItem> items,
                                            ExtInterfaceConfig cfg) {
        List<Map<String, Object>> payList = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (BankGateway.PayItem it : items) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("payeeName", it.payeeName());
                m.put("bankName", it.bankName());
                m.put("account", it.bankAccount());
                m.put("amount", it.amount());
                m.put("postscript", it.postscript() == null ? "" : it.postscript());
                payList.add(m);
                if (it.amount() != null) {
                    total = total.add(it.amount());
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", batchNo);
        payload.put("count", payList.size());
        payload.put("totalAmount", total);
        payload.put("items", payList);
        return payload;
    }

    @Override
    public List<String> parseSerialNos(String body, int itemCount) {
        if (body == null || !body.contains("serialNos")) {
            return null;   // 交调用方降级为占位流水号
        }
        try {
            int i = body.indexOf("serialNos");
            int lb = body.indexOf('[', i);
            int rb = (lb < 0) ? -1 : body.indexOf(']', lb);
            if (lb > 0 && rb > lb) {
                String inner = body.substring(lb + 1, rb).trim();
                if (inner.isEmpty()) {
                    return List.of();
                }
                return Arrays.stream(inner.split(","))
                        .map(s -> s.trim().replace("\"", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("[银企直联] 通用形态流水号解析失败，交调用方降级: {}", e.getMessage());
        }
        return null;
    }
}
