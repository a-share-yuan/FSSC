// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感数据脱敏装配。
 *
 * <p>配置项 {@code fssc.security.mask-sensitive}：true（默认）脱敏 / false 返回明文（仅限受控联调）。</p>
 *
 * <p>说明：Jackson2 {@link SensitiveModule} 由 {@link SensitiveJacksonPostProcessor}
 * 注册到容器内的 Jackson2 {@code ObjectMapper}（用于操作日志/导出等 Jackson2 序列化场景）。
 * HTTP 出参由 Boot 4 默认的 <b>Jackson3</b> 序列化，不走 Jackson2 模块，
 * 因此对外接口的 PII 脱敏在业务层显式完成（见 {@code UserService#page}）。</p>
 */
@Configuration
public class SensitiveMaskConfig {

    @Value("${fssc.security.mask-sensitive:true}")
    private boolean maskSensitive;

    @PostConstruct
    public void init() {
        SensitiveSerializer.setEnabled(maskSensitive);
    }
}
