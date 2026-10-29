// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * 把 {@link SensitiveModule} 强制注册到容器内所有 {@code ObjectMapper} Bean 上。
 *
 * <p>背景：Boot 4 同时存在 Jackson2 / Jackson3 两套自动配置，仅靠
 * {@code @Bean Module} 或 {@code Jackson2ObjectMapperBuilderCustomizer}
 * 在部分装配顺序下不会生效（出参仍是明文）。这里用 {@link BeanPostProcessor}
 * 在 ObjectMapper 初始化完成后直接注册模块，确保 HTTP 出参一定走脱敏器。</p>
 */
@Configuration
public class SensitiveJacksonPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ObjectMapper mapper) {
            mapper.registerModule(new SensitiveModule());
        }
        return bean;
    }
}
