// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认银行网关（模拟）：离线/演示可跑，直接返回成功并生成流水号。
 * 生产接入真实银企直联：新增 HttpBankGateway（@Primary 覆盖）调用银行 HTTP 接口，
 * 认证/签名/报文转换在网关内封装，业务层无感知。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "fssc.bank.gateway", havingValue = "mock", matchIfMissing = true)
public class DefaultBankGateway implements BankGateway {

    @Override
    public BankResult submitBatch(String batchNo, List<PayItem> items) {
        // 测试开关：批次号含 FAIL 时模拟银行拒绝（用于演示支付失败闭环与重试，生产不影响）
        if (batchNo != null && batchNo.contains("FAIL")) {
            log.warn("[模拟银企直联] 批次 {} 银行拒绝（测试）", batchNo);
            BankResult fail = new BankResult();
            fail.setSuccess(false);
            fail.setFailReason("模拟银行拒绝：余额不足");
            fail.setResp("ERR:INSUFFICIENT_FUNDS");
            return fail;
        }
        List<String> serials = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            serials.add("SER" + System.currentTimeMillis() + i);
        }
        log.info("[模拟银企直联] 批次 {} 提交 {} 笔，全部成功", batchNo, items.size());
        BankResult r = new BankResult();
        r.setSuccess(true);
        r.setResp("模拟银行处理成功，共 " + items.size() + " 笔");
        r.setSerialNos(serials);
        return r;
    }
}
