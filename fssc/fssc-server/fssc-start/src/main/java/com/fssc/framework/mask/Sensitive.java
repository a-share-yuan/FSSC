// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解。
 *
 * <p>标注在实体字段 / getter 上，**出参序列化**时由
 * {@link SensitiveModule} 中的 {@link SensitivePropertyModifier} 自动套用脱敏；
 * 反序列化（入参）不受影响，明文提交仍可正常保存。</p>
 *
 * <p>全局开关：{@code fssc.security.mask-sensitive}（默认 true）。
 * 需要返回明文时不要依赖此注解字段，应走单独的「授权查看」接口。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Sensitive {

    SensitiveType value() default SensitiveType.PHONE;
}
