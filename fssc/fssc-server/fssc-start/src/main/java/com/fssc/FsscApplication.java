// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FSSC 后端启动类。扫描 com.fssc 下所有模块（framework / platform / system / demo ...）。
 * <p>
 * 多租户与四道防线全部由框架层（com.fssc.framework）自动装配，
 * 业务模块只需关注自己的业务 Controller/Service/Mapper。
 * <p>
 * 排除四个分体入口类：ai/gateway/report/image 各是一个 {@code @SpringBootApplication}
 * （自带 {@code @MapperScan("com.fssc.**.mapper")} 与收窄的扫描面），与本类同在一个 fat jar 里。
 * 不排除则本进程会把它们当普通配置类二次装配——mapper 被重复登记 262 枚（449 条
 * "Skipping MapperFactoryBean ... already defined" WARN）且启动慢 ~10 秒。
 * 分体进程自己以 {@code -Dloader.main} 把它们作为主类启动，主类不走组件扫描，故不受影响。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.fssc", excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.fssc\\.(ai|gateway|report|image)\\.\\w*ServiceApplication"))
@MapperScan("com.fssc.**.mapper")
@EnableScheduling
@EnableCaching
public class FsscApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsscApplication.class, args);
    }
}
