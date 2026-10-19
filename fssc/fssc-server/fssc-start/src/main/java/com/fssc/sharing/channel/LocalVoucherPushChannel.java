// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 本地占位推送通道（默认实现，{@code fssc.voucher.push.channel=local}）。
 *
 * <p>P1-8 整改要点：本类不产生任何网络调用，且**不含任何人为 sleep/随机**——
 * 与改造前 mock 的差异在于：原实现用 {@code Thread.sleep(150)} 伪造网络耗时、
 * 用内存 Map 保存推送记录；现改为立即返回确定性成功 + 由 VoucherPushService 落库，
 * 演示环境同样可跑，但不污染真实链路、不影响吞吐。</p>
 *
 * <p>回执单号按「目标+凭证号+秒级时间戳」确定性生成，便于人工核对与冒烟断言。</p>
 */
@Slf4j
@Component
public class LocalVoucherPushChannel implements VoucherPushChannel {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String name() {
        return "local";
    }

    @Override
    public VoucherPushResult push(String target, Map<String, Object> payload) {
        String voucherNo = String.valueOf(payload.getOrDefault("voucherNo", "UNKNOWN"));
        String externalNo = target + "-" + voucherNo + "-" + LocalDateTime.now().format(TS);
        log.info("[凭证推送-本地] target={} voucherNo={} externalNo={}", target, voucherNo, externalNo);
        return VoucherPushResult.ok(externalNo, name(), "{\"pushed\":true,\"channel\":\"local\"}");
    }
}
