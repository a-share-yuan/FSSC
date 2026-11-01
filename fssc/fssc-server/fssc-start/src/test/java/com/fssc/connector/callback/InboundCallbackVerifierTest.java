// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.callback;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入站回调验签入口测试：与 BCP/LinkU 规则一致（HMAC-SHA256(secret, "<ts>."+body)）。
 */
class InboundCallbackVerifierTest {

    private final InboundCallbackVerifier verifier = new InboundCallbackVerifier();

    private static String sign(String secret, String ts, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] h = mac.doFinal((ts + "." + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return "sha256=" + sb;
    }

    @Test
    void validSignature_passes() throws Exception {
        long now = 1_700_000_000L;
        String body = "{\"eventType\":\"RECON_CONFIRM\"}";
        InboundCallbackVerifier.Result r = verifier.verify("s3cret", String.valueOf(now),
                sign("s3cret", String.valueOf(now), body), body, now, 300);

        assertTrue(r.ok());
        assertFalse(r.isTimestampError());
    }

    @Test
    void tamperedBody_failsAsSignatureError() throws Exception {
        long now = 1_700_000_000L;
        String body = "{\"a\":1}";
        String sig = sign("s3cret", String.valueOf(now), body);

        InboundCallbackVerifier.Result r = verifier.verify("s3cret", String.valueOf(now), sig, "{\"a\":2}", now, 300);

        assertFalse(r.ok());
        assertFalse(r.isTimestampError());
    }

    @Test
    void outOfWindow_failsAsTimestampError() throws Exception {
        long ts = 1_700_000_000L;
        String body = "{}";
        InboundCallbackVerifier.Result r = verifier.verify("s3cret", String.valueOf(ts),
                sign("s3cret", String.valueOf(ts), body), body, ts + 10_000, 300);

        assertFalse(r.ok());
        assertTrue(r.isTimestampError(), "超窗应标记为时间窗错误（回 400）");
    }

    @Test
    void missingHeaders_fails() {
        InboundCallbackVerifier.Result r = verifier.verify("s3cret", null, null, "{}", 1L, 300);
        assertFalse(r.ok());
    }
}
