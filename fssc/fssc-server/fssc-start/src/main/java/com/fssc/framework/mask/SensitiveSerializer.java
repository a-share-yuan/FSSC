// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fssc.common.util.MaskUtil;

import java.io.IOException;

/**
 * 敏感字段序列化器：出参按 {@link SensitiveType} 脱敏。
 *
 * <p>由 {@link SensitivePropertyModifier} 在序列化器构建阶段绑定到
 * 带 {@link Sensitive} 注解的属性上；全局开关见 {@link SensitiveSerializer#setEnabled(boolean)}。</p>
 */
public class SensitiveSerializer extends JsonSerializer<Object> {

    /** 全局脱敏开关：默认开启。关闭后出参返回明文（仅限内网/联调环境）。 */
    private static volatile boolean enabled = true;

    private final SensitiveType type;

    public SensitiveSerializer() {
        this(SensitiveType.PHONE);
    }

    public SensitiveSerializer(SensitiveType type) {
        this.type = type == null ? SensitiveType.PHONE : type;
    }

    static void setEnabled(boolean v) {
        enabled = v;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        if (!enabled || s.isEmpty()) {
            gen.writeString(s);
            return;
        }
        gen.writeString(mask(type, s));
    }

    private static String mask(SensitiveType type, String v) {
        if (MaskUtil.masked(v)) {
            return v; // 已脱敏，避免二次覆盖
        }
        return switch (type) {
            case PHONE -> MaskUtil.phone(v);
            case EMAIL -> MaskUtil.email(v);
            case BANK_CARD -> MaskUtil.bankAccount(v);
            case ID_CARD -> MaskUtil.idCard(v);
            case NAME -> MaskUtil.name(v);
        };
    }
}
