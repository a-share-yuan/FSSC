// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.support.SQLErrorCodesFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SQL 异常翻译链路的启动期预热。
 * <p>
 * MyBatis 的 {@code MyBatisExceptionTranslator} 把异常翻译器做成懒加载：运行期第一次出现
 * SQL 异常时才在 {@code initExceptionTranslator()} 里持一把 {@code ReentrantLock}，构造
 * {@code SQLErrorCodeSQLExceptionTranslator}，进而由 {@code SQLErrorCodesFactory} 用
 * {@code XmlBeanDefinitionReader} 解析 {@code sql-error-codes.xml}。这条路径会首次触发
 * xerces 的 {@code SchemaGrammar} / {@code XMLSchemaValidator} 类初始化。
 * <p>
 * 在低内存或高并发启动时，持锁线程可能长时间停在类初始化上；此后所有走 MyBatis 的线程
 * 都堵在同一把锁上，HTTP 工作线程与调度线程被整体饿死，表现为端口仍 LISTENING 但请求无响应。
 * 本组件在启动阶段（单线程、无外部流量）把这两步一次性做完：
 * <ol>
 *   <li>{@code SQLErrorCodesFactory.getInstance()} 完成 XML 解析与 xerces 类初始化；</li>
 *   <li>逐个触发容器内 {@code SqlSessionTemplate} 的异常翻译器懒初始化。</li>
 * </ol>
 * 运行期再遇到 SQL 异常时，工厂已是单例、翻译器已就绪，不会再进入类初始化与锁嵌套。
 * 预热失败只记 WARN，不阻断启动。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceTranslatorWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersistenceTranslatorWarmup.class);

    private final ApplicationContext applicationContext;

    public PersistenceTranslatorWarmup(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        try {
            SQLErrorCodesFactory.getInstance();

            Map<String, SqlSessionTemplate> templates =
                    applicationContext.getBeansOfType(SqlSessionTemplate.class);
            int warmed = 0;
            for (SqlSessionTemplate template : templates.values()) {
                PersistenceExceptionTranslator translator = template.getPersistenceExceptionTranslator();
                if (translator != null) {
                    translator.translateExceptionIfPossible(new RuntimeException("exception translator warmup"));
                    warmed++;
                }
            }

            log.info("SQL 异常翻译链路预热完成：SqlSessionTemplate {} 个 / 已预热 {} 个，耗时 {} ms",
                    templates.size(), warmed, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception ex) {
            log.warn("SQL 异常翻译链路预热未成功（不阻断启动，运行期将按需初始化）：{}", ex.toString());
        }
    }
}
