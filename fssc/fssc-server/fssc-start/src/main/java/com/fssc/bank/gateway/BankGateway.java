// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银企直联网关接口（可插拔）。
 * <ul>
 *   <li>默认实现 DefaultBankGateway：模拟银行处理（演示/离线可跑）</li>
 *   <li>生产实现 HttpBankGateway：对接银行银企直联 HTTP 接口（配置 fssc.bank.gateway 切换）</li>
 * </ul>
 * 业务层（PayBatchService）只依赖本接口，银行切换零侵入。
 */
public interface BankGateway {

    /** 提交付款批次明细，返回各笔结果 */
    BankResult submitBatch(String batchNo, List<PayItem> items);

    /** 付款项（收款人/账户/金额） */
    record PayItem(String payeeName, String bankName, String bankAccount,
                   BigDecimal amount, String postscript) {
    }
}
