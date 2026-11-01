// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 敏感字段脱敏 Jackson 模块。
 *
 * <p>用 {@link BeanSerializerModifier} 在序列化器构建阶段把
 * {@link SensitiveSerializer} 绑定到带 {@link Sensitive} 注解的属性上。
 * 相比 {@code @JacksonAnnotationsInside} 组合注解，此方式对 getter 注解、
 * 字段注解、继承层级都稳定生效。</p>
 */
public class SensitiveModule extends SimpleModule {

    public SensitiveModule() {
        super("fssc-sensitive-module");
        setSerializerModifier(new SensitivePropertyModifier());
    }

    /** 逐属性检测 @Sensitive 并替换序列化器 */
    public static class SensitivePropertyModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                        BeanDescription beanDesc,
                                                        List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                Sensitive ann = writer.getAnnotation(Sensitive.class);
                if (ann == null && writer.getMember() != null) {
                    ann = writer.getMember().getAnnotation(Sensitive.class);
                }
                if (ann != null) {
                    writer.assignSerializer(new SensitiveSerializer(ann.value()));
                }
            }
            return beanProperties;
        }
    }
}
