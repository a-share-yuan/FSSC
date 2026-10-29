// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.credential;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 连接域凭据透明加解密器（形态 B · D 步：凭据托管统一）。
 *
 * <p>存于 {@code ext_interface_config.api_key / api_secret / extra_config} 的凭据可分两种形态：
 * <b>明文</b>（历史数据）或 <b>密文</b>（带 {@value #PREFIX} 版本前缀）。本类统一判定与转换：</p>
 * <ul>
 *   <li>默认<b>关闭</b>：未配置 {@code fssc.connector.credential.key} 时 {@link #isEnabled()}=false，
 *       {@link #decrypt(String)} 原样返回——与原先行为完全一致，零回归；</li>
 *   <li>配置密钥后启用：AES-256-GCM，随机 96 位 IV 前置，密文形如 {@code enc:v1:<base64(iv||ct||tag)>}；</li>
 *   <li>解密失败（密钥错配/数据损坏）不抛穿，记 WARN 并原样返回，避免阻断业务外呼。</li>
 * </ul>
 */
@Slf4j
@Component
public class CredentialCipher {

    /** 密文版本前缀：只有带此前缀的值才会被解密，明文与掩码串一律原样返回。 */
    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${fssc.connector.credential.key:}") String keyBase64) {
        this.key = parseKey(keyBase64);
    }

    /** 是否启用加密（配置了合法密钥）。 */
    public boolean isEnabled() {
        return key != null;
    }

    /** 是否为带版本前缀的密文。 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 明文 → 密文；未启用 / 空值时原样返回。 */
    public String encrypt(String plain) {
        if (key == null || plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.warn("凭据加密失败，按明文保存：{}", e.getMessage());
            return plain;
        }
    }

    /** 密文 → 明文；非密文（明文/掩码/空）原样返回；解密失败原样返回并告警。 */
    public String decrypt(String stored) {
        if (key == null || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("凭据解密失败（密钥错配或数据损坏），按原值使用：{}", e.getMessage());
            return stored;
        }
    }

    private SecretKey parseKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(keyBase64.trim());
            if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                log.warn("fssc.connector.credential.key 长度非法（{} 字节），凭据加密保持关闭", raw.length);
                return null;
            }
            return new SecretKeySpec(raw, "AES");
        } catch (Exception e) {
            log.warn("fssc.connector.credential.key 解析失败，凭据加密保持关闭：{}", e.getMessage());
            return null;
        }
    }
}
