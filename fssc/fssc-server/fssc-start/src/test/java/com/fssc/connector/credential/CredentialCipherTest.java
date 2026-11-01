// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.credential;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭据透明加解密测试：默认关闭时零行为变化，启用时往返一致且只对带前缀的值解密。
 */
class CredentialCipherTest {

    private static String key() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void disabled_passesThrough() {
        CredentialCipher cipher = new CredentialCipher("");

        assertFalse(cipher.isEnabled());
        assertEquals("secret-abc", cipher.encrypt("secret-abc"), "未启用时加密应原样返回");
        assertEquals("secret-abc", cipher.decrypt("secret-abc"), "未启用时解密应原样返回");
        assertFalse(cipher.isEncrypted("secret-abc"));
    }

    @Test
    void enabled_roundTrip() {
        CredentialCipher cipher = new CredentialCipher(key());

        assertTrue(cipher.isEnabled());
        String enc = cipher.encrypt("app-secret-123");
        assertTrue(cipher.isEncrypted(enc), "密文应带版本前缀");
        assertTrue(enc.startsWith(CredentialCipher.PREFIX));
        assertEquals("app-secret-123", cipher.decrypt(enc), "往返应还原原文");
        assertFalse(enc.contains("app-secret-123"), "密文不应包含明文");
    }

    @Test
    void enabled_plaintextAndMaskedAreUntouched() {
        CredentialCipher cipher = new CredentialCipher(key());

        assertEquals("plain-value", cipher.decrypt("plain-value"), "非密文原样返回");
        assertEquals("********abcd", cipher.decrypt("********abcd"), "掩码串原样返回");
        assertEquals("", cipher.encrypt(""), "空值原样返回");
    }

    @Test
    void wrongKey_decryptFailsClosedToOriginal() {
        CredentialCipher enc = new CredentialCipher(key());
        String encVal = enc.encrypt("top-secret");

        byte[] other = new byte[32];
        CredentialCipher otherCipher = new CredentialCipher(Base64.getEncoder().encodeToString(other));

        assertEquals(encVal, otherCipher.decrypt(encVal), "密钥错配时不抛穿，原值返回");
    }

    @Test
    void resolver_shouldSend_ignoresMaskedAndBlank() {
        CredentialResolver resolver = new CredentialResolver(new CredentialCipher(key()));

        assertTrue(resolver.shouldSend("real-key"));
        assertFalse(resolver.shouldSend("********abcd"), "掩码占位不可外发");
        assertFalse(resolver.shouldSend("   "));
        assertFalse(resolver.shouldSend(null));

        String enc = new CredentialCipher(key()).encrypt("real-key");
        assertTrue(resolver.shouldSend(enc), "密文可外发（解密后非掩码）");
    }

    @Test
    void invalidKeyLength_keepsDisabled() {
        CredentialCipher cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[7]));
        assertFalse(cipher.isEnabled(), "非法密钥长度应保持关闭而非抛错");
    }
}
