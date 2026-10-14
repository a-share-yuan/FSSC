// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.credential;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 连接域凭据统一托管入口（形态 B · D 步）。
 *
 * <p>把"凭据从哪来、是明文还是密文、能不能外发"的判断收敛到一处，避免各通道各自读
 * {@code ext_interface_config} / {@code sys_config} 再各自判定形态。当前提供：</p>
 * <ul>
 *   <li>{@link #effective(String)}：返回可外发的明文凭据（透明解密；非密文原样返回）；</li>
 *   <li>{@link #shouldSend(String)}：凭据是否可外发（非空且非掩码占位）；</li>
 *   <li>{@link #isEncrypted(String)} / {@link #isEnabled()}：形态与启用状态观测。</li>
 * </ul>
 *
 * <p><b>安全纪律</b>：本类绝不把凭据写入日志；掩码串（{@code ********xxxx}）不视为可外发凭据。</p>
 */
@Component
@RequiredArgsConstructor
public class CredentialResolver {

    private final CredentialCipher cipher;

    /** 返回可外发的明文凭据：密文透明解密，明文/掩码/空值原样返回。 */
    public String effective(String stored) {
        return cipher.decrypt(stored);
    }

    /** 凭据是否可外发：非空且非掩码占位。传库中原始值（自动先解密）。 */
    public boolean shouldSend(String stored) {
        String plain = cipher.decrypt(stored);
        return plain != null && !plain.isBlank() && !com.fssc.system.entity.ExtInterfaceConfig.looksMasked(plain);
    }

    public boolean isEncrypted(String stored) {
        return cipher.isEncrypted(stored);
    }

    public boolean isEnabled() {
        return cipher.isEnabled();
    }
}
