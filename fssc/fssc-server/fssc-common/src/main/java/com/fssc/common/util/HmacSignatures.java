// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 回调签名校验工具（HMAC-SHA256 + 时间窗），中立包工具，不依赖任何业务域。
 *
 * <p>由 {@code InboundCallbackVerifier} 统一调用。
 * 本类系自 {@code LinkuCrypto} 迁入：连接域（connector）不应反向依赖
 * 业务域（sharing），验签属通用密码学工具，归位到 fssc-common 中立包后反向依赖消除。</p>
 *
 * <p>验签规则（对齐对端 BCP / LinkU 侧生成规则）：</p>
 * <pre>
 *   signature = "sha256=" + lowercaseHex( HMAC-SHA256(secret, "&lt;ts&gt;." + body) )
 *   校验：|now(epoch秒) − tsHeader| ≤ maxSkew；小写 hex + 常量时间比较。
 * </pre>
 * 任何一步不通过返回非 null 的失败原因；全部通过返回 {@code null}。
 */
public final class HmacSignatures {

    private static final String ALG = "HmacSHA256";
    private static final String SIG_PREFIX = "sha256=";

    private HmacSignatures() {
    }

    /**
     * 校验回调签名与时间窗。
     *
     * @param secret 共享密钥（非空；空密钥由调用方先行拒绝，本方法不判定）
     * @param tsHeader 时间戳头（Unix 秒），如 {@code X-BCP-Timestamp}
     * @param sigHeader 签名头（{@code sha256=} 前缀可选），如 {@code X-BCP-Signature}
     * @param body 原始请求体（参与签名的原文）
     * @param nowEpoch 当前 Unix 秒
     * @param maxSkew 允许的时间偏差（秒）
     * @return null=校验通过；否则为失败原因（{@code ts|} 前缀表示时间窗错误，应对应 400）
     */
    public static String verify(String secret, String tsHeader, String sigHeader,
                                String body, long nowEpoch, long maxSkew) {
        if (tsHeader == null || tsHeader.isBlank() || sigHeader == null || sigHeader.isBlank()) {
            return "missing X-BCP-Timestamp/X-BCP-Signature";
        }
        long ts;
        try {
            ts = Long.parseLong(tsHeader.trim());
        } catch (NumberFormatException e) {
            return "invalid X-BCP-Timestamp";
        }
        if (Math.abs(nowEpoch - ts) > maxSkew) {
            return "ts|timestamp out of window";
        }
        String provided = sigHeader.startsWith(SIG_PREFIX) ? sigHeader.substring(SIG_PREFIX.length()) : sigHeader;
        String expected = hmacHex(secret, ts + "." + body);
        if (!constantTimeEquals(expected, provided)) {
            return "invalid X-BCP-Signature";
        }
        return null;
    }

    /** HMAC-SHA256 → 小写十六进制 */
    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG));
            byte[] h = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /** 常量时间比较，避免时序侧信道 */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
