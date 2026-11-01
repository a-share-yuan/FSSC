// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import lombok.Data;

import java.util.List;

/**
 * 银行网关结果。
 * 真实银企直联返回各笔流水号/失败原因；模拟实现直接成功。
 */
@Data
public class BankResult {

    /** 是否整体成功 */
    private boolean success;
    /** 银行响应原文 */
    private String resp;
    /** 各笔流水号（按提交顺序） */
    private List<String> serialNos;
    /** 失败原因 */
    private String failReason;
}
